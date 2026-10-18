#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SSE 长连接单机并发压测（在生产实例本机 loopback 运行，测真实单机容量）。

原理：
- 每个并发流使用一个独立游客账号（/api/auth/trial，唯一伪 XFF 规避 IP 维度限制，
  账号间互不影响；单用户 1 活跃会话约束天然满足）。
- 每个账号 POST /api/chat（modelId=1），持有 SSE 连接直到流结束或超时。
- 采样线程每 1.5s 记录 :8080 established 连接数、load、JVM 线程数。
- 梯度提升并发级别，观察连接建立成功率/TTFT/完成率/错误分类，找到拐点。

安全闸：load1 > 14（8核）或某级别连接成功率 < 90% 时停止升级；级别上限 250。
"""
import http.client
import json
import subprocess
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor

HOST, PORT = '127.0.0.1', 8080
PROMPT = '用中文写一段150字左右的团队介绍'
LEVELS = [int(x) for x in sys.argv[1].split(',')] if len(sys.argv) > 1 else [10, 30, 60, 100, 150, 200, 250]
LOAD_STOP = 14.0
APP_PID = None


def fake_ip(i):
    return f'10.{(i // 65536) % 256}.{(i // 256) % 256}.{i % 256}'


def post_json(path, body, headers, timeout=30):
    conn = http.client.HTTPConnection(HOST, PORT, timeout=timeout)
    try:
        conn.request('POST', path, body=json.dumps(body),
                     headers={'Content-Type': 'application/json', **headers})
        resp = conn.getresponse()
        data = resp.read()
        return resp.status, dict(resp.getheaders()), data
    finally:
        conn.close()


def mint_account(i):
    """创建一个游客账号，返回 (cookie_header, user_id) 或 None。"""
    for _ in range(3):
        try:
            status, hdrs, data = post_json('/api/auth/trial', {}, {'X-Forwarded-For': fake_ip(i)})
            if status == 200:
                cookie = hdrs.get('Set-Cookie', '').split(';')[0]
                uid = json.loads(data)['data']['id']
                return cookie, uid
        except Exception:
            time.sleep(0.5)
    return None


def run_stream(i, cookie, barrier, result):
    """持有 1 条 SSE 流直到结束。result 记录各项指标。"""
    barrier.wait()
    t0 = time.monotonic()
    conn = http.client.HTTPConnection(HOST, PORT, timeout=120)
    try:
        conn.request('POST', '/api/chat',
                     body=json.dumps({'messages': [{'role': 'user', 'content': PROMPT}], 'modelId': 1}),
                     headers={'Content-Type': 'application/json',
                              'Accept': 'text/event-stream',
                              'Cookie': cookie,
                              'X-Forwarded-For': fake_ip(i)})
        result['t_conn'] = time.monotonic() - t0
        resp = conn.getresponse()
        result['http'] = resp.status
        if resp.status != 200:
            resp.read()
            result['outcome'] = f'http_{resp.status}'
            return
        result['t_head'] = time.monotonic() - t0
        buf = b''
        first = True
        deadline = time.monotonic() + 110
        while time.monotonic() < deadline:
            chunk = resp.read1(4096)
            if not chunk:
                break
            if first:
                result['ttft'] = time.monotonic() - t0
                first = False
            buf += chunk
        result['duration'] = time.monotonic() - t0
        text = buf.decode('utf-8', errors='replace')
        result['bytes'] = len(buf)
        if '"finishReason":"stop"' in text or '"finishReason": "stop"' in text:
            if 'Request failed' in text:
                result['outcome'] = 'error_frame'
            else:
                result['outcome'] = 'complete'
        elif 'Request failed' in text:
            result['outcome'] = 'error_frame'
        elif not buf:
            result['outcome'] = 'empty'
        else:
            result['outcome'] = 'partial'
    except Exception as e:
        result['outcome'] = 'exc_' + type(e).__name__
        result['duration'] = time.monotonic() - t0
    finally:
        try:
            conn.close()
        except Exception:
            pass


def sample_loop(stop_evt, samples):
    base = established_count()
    t0 = time.monotonic()
    while not stop_evt.is_set():
        try:
            load = float(open('/proc/loadavg').read().split()[0])
            jthreads = len(subprocess.run(['ls', f'/proc/{APP_PID}/task'],
                                          capture_output=True, text=True).stdout.split()) if APP_PID else 0
            raw = established_count()
            samples.append({'t': round(time.monotonic() - t0, 2), 'est': raw - base, 'raw': raw,
                            'load': load, 'jthreads': jthreads})
        except Exception:
            pass
        stop_evt.wait(0.5)


def established_count():
    out = subprocess.run(['ss', '-tn', 'state', 'established', '(', 'sport', '=', ':8080', ')'],
                         capture_output=True, text=True).stdout
    return max(0, len(out.strip().split('\n')) - 1)


def run_level(level, accounts):
    barrier = threading.Barrier(level)
    results = [dict() for _ in range(level)]
    stop_evt = threading.Event()
    samples = []
    st = threading.Thread(target=sample_loop, args=(stop_evt, samples), daemon=True)
    st.start()
    t0 = time.monotonic()
    with ThreadPoolExecutor(max_workers=level) as ex:
        for i in range(level):
            ex.submit(run_stream, accounts[i]['idx'], accounts[i]['cookie'], barrier, results[i])
    wall = time.monotonic() - t0
    stop_evt.set()
    st.join()

    ok = sum(1 for r in results if r.get('outcome') == 'complete')
    http200 = sum(1 for r in results if r.get('http') == 200)
    from collections import Counter
    oc = Counter(r.get('outcome', 'unknown') for r in results)
    ttfts = sorted(r['ttft'] for r in results if 'ttft' in r)
    heads = sorted(r['t_head'] for r in results if 't_head' in r)
    durs = sorted(r['duration'] for r in results if 'duration' in r)
    peak_est = max((s['est'] for s in samples), default=0)
    peak_load = max((s['load'] for s in samples), default=0)
    peak_jt = max((s['jthreads'] for s in samples), default=0)
    print(f'[debug] samples={len(samples)} est_series={[(s["t"], s["raw"]) for s in samples]}', file=sys.stderr)

    def pct(arr, p):
        return round(arr[min(len(arr) - 1, int(len(arr) * p))], 2) if arr else None

    summary = {
        'level': level, 'wall_s': round(wall, 1),
        'http200': http200, 'complete': ok, 'outcomes': dict(oc),
        'thead_p50': pct(heads, .5), 'thead_max': round(heads[-1], 2) if heads else None,
        'ttft_p50': pct(ttfts, .5), 'ttft_p95': pct(ttfts, .95), 'ttft_max': round(ttfts[-1], 2) if ttfts else None,
        'dur_p50': pct(durs, .5), 'dur_p95': pct(durs, .95),
        'peak_established': peak_est, 'peak_load': peak_load, 'peak_jvm_threads': peak_jt,
    }
    print(json.dumps(summary, ensure_ascii=False))
    return summary


def main():
    global APP_PID
    out = subprocess.run(['pgrep', '-f', 'chat-0.0.1-SNAPSHOT.jar'],
                         capture_output=True, text=True).stdout.split()
    APP_PID = out[0] if out else None
    print(f'APP_PID={APP_PID}', file=sys.stderr)

    max_level = max(LEVELS)
    print(f'预创建 {max_level} 个游客账号...', file=sys.stderr)
    accounts = []
    with ThreadPoolExecutor(max_workers=20) as ex:
        futs = [ex.submit(mint_account, i) for i in range(max_level)]
        for i, f in enumerate(futs):
            r = f.result()
            if r:
                accounts.append({'idx': i, 'cookie': r[0], 'uid': r[1]})
    print(f'账号就绪 {len(accounts)}/{max_level}', file=sys.stderr)
    if len(accounts) < max_level:
        print('账号不足，终止', file=sys.stderr)
        sys.exit(1)

    all_summaries = []
    for level in LEVELS:
        print(f'--- level {level} ---', file=sys.stderr)
        s = run_level(level, accounts[:level])
        all_summaries.append(s)
        http200_rate = s['http200'] / level
        if s['peak_load'] > LOAD_STOP:
            print(f'负载 {s["peak_load"]} 超阈值，停止升级', file=sys.stderr)
            break
        if http200_rate < 0.95:
            print(f'连接建立率 {http200_rate:.0%} 过低（连接层拐点），停止升级', file=sys.stderr)
            break
        time.sleep(15)  # 级间冷却

    with open('/tmp/sse_stress_result.json', 'w') as f:
        json.dump(all_summaries, f, ensure_ascii=False, indent=1)
    print('saved /tmp/sse_stress_result.json', file=sys.stderr)


if __name__ == '__main__':
    main()
