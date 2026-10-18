#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SSE 长连接压测 v2 —— 打 mock 上游版（零真实 LLM 调用）。

与 v1 的差异：
- 目标为第二实例（默认 18080）+ mock 模型（modelId 由 MODEL_ID 环境变量指定）
- mock 上游无限吐字；客户端持有 HOLD_SECONDS 秒后主动断开（断开即取消订阅，不触发标题生成，
  因此整个压测**零真实上游调用**）
- 采样增加 app→mock 侧连接数（sport=19399）

用法：python3 sse_stress_mock_test.py <levels逗号分隔> [hold_seconds]
环境变量：TARGET_PORT(默认18080) MODEL_ID(必填) MOCK_PORT(默认19399)
"""
import http.client
import json
import os
import subprocess
import sys
import threading
import time
from collections import Counter
from concurrent.futures import ThreadPoolExecutor

TARGET_PORT = int(os.environ.get('TARGET_PORT', '18080'))
MOCK_PORT = int(os.environ.get('MOCK_PORT', '19399'))
MODEL_ID = int(os.environ['MODEL_ID']) if os.environ.get('MODEL_ID') else None
HOST = '127.0.0.1'
LEVELS = [int(x) for x in sys.argv[1].split(',')] if len(sys.argv) > 1 else [100, 250, 500]
HOLD = int(sys.argv[2]) if len(sys.argv) > 2 else 60
LOAD_STOP = 14.0
APP_PID = None


def fake_ip(i):
    return f'10.{(i // 65536) % 256}.{(i // 256) % 256}.{i % 256}'


def mint_account(i):
    for _ in range(3):
        conn = None
        try:
            conn = http.client.HTTPConnection(HOST, TARGET_PORT, timeout=30)
            conn.request('POST', '/api/auth/trial', body='{}',
                         headers={'Content-Type': 'application/json', 'X-Forwarded-For': fake_ip(i)})
            resp = conn.getresponse()
            data = resp.read()
            setc = resp.getheader('Set-Cookie')
            if not (resp.status == 200 and setc):
                raise RuntimeError('trial failed')
            cookie = setc.split(';')[0]
            uid = json.loads(data)['data']['id']
            conn.close()
            # 预建会话拿 sessionId（模拟真实前端流程；也是中止回收的前提）
            conn = http.client.HTTPConnection(HOST, TARGET_PORT, timeout=30)
            conn.request('POST', '/api/chat/sessions',
                         body=json.dumps({'model': 'mock-stream'}),
                         headers={'Content-Type': 'application/json', 'Cookie': cookie,
                                  'X-Forwarded-For': fake_ip(i)})
            resp2 = conn.getresponse()
            sid = json.loads(resp2.read())['data']['sessionId']
            return cookie, uid, sid
        except Exception:
            time.sleep(0.5)
        finally:
            try:
                conn and conn.close()
            except Exception:
                pass
    return None


def run_stream(i, cookie, sid, barrier, result):
    """持有一条 SSE 流 HOLD 秒，期间持续读取计数，随后主动断开。"""
    barrier.wait()
    t0 = time.monotonic()
    conn = http.client.HTTPConnection(HOST, TARGET_PORT, timeout=40)
    chunks = 0
    try:
        conn.request('POST', '/api/chat',
                     body=json.dumps({'messages': [{'role': 'user', 'content': '压测'}], 'modelId': MODEL_ID, 'sessionId': sid}),
                     headers={'Content-Type': 'application/json', 'Accept': 'text/event-stream',
                              'Cookie': cookie, 'X-Forwarded-For': fake_ip(i)})
        result['t_conn'] = time.monotonic() - t0
        resp = conn.getresponse()
        result['http'] = resp.status
        if resp.status != 200:
            resp.read()
            result['outcome'] = f'http_{resp.status}'
            return
        result['t_head'] = time.monotonic() - t0
        deadline = time.monotonic() + HOLD
        first = True
        last_data = time.monotonic()
        max_gap = 0.0
        while time.monotonic() < deadline:
            data = resp.read1(8192)
            if not data:
                break
            now = time.monotonic()
            max_gap = max(max_gap, now - last_data)
            last_data = now
            if first:
                result['ttft'] = now - t0
                first = False
            chunks += data.count(b'0:')
            result['bytes'] = result.get('bytes', 0) + len(data)
        result['held'] = time.monotonic() - t0
        result['chunks'] = chunks
        result['max_gap'] = round(max_gap, 2)
        result['outcome'] = 'held_ok' if chunks > 0 else 'no_data'
    except Exception as e:
        result['outcome'] = 'exc_' + type(e).__name__
    finally:
        try:
            conn.close()
        except Exception:
            pass


def established_on(port):
    out = subprocess.run(['ss', '-tn', 'state', 'established', '(', 'sport', '=', f':{port}', ')'],
                         capture_output=True, text=True).stdout
    return max(0, len(out.strip().split('\n')) - 1)


def sample_loop(stop_evt, samples, base_app, base_mock):
    while not stop_evt.is_set():
        try:
            load = float(open('/proc/loadavg').read().split()[0])
            jthreads = len(subprocess.run(['ls', f'/proc/{APP_PID}/task'],
                                          capture_output=True, text=True).stdout.split()) if APP_PID else 0
            samples.append({'t': round(time.monotonic(), 1),
                            'app_est': established_on(TARGET_PORT) - base_app,
                            'mock_est': established_on(MOCK_PORT) - base_mock,
                            'load': load, 'jthreads': jthreads})
        except Exception:
            pass
        stop_evt.wait(1.0)


def run_level(level, accounts):
    barrier = threading.Barrier(level)
    results = [dict() for _ in range(level)]
    stop_evt = threading.Event()
    samples = []
    base_app = established_on(TARGET_PORT)
    base_mock = established_on(MOCK_PORT)
    st = threading.Thread(target=sample_loop, args=(stop_evt, samples, base_app, base_mock), daemon=True)
    st.start()
    t0 = time.monotonic()
    with ThreadPoolExecutor(max_workers=level) as ex:
        for i in range(level):
            ex.submit(run_stream, accounts[i]['idx'], accounts[i]['cookie'], accounts[i]['sid'], barrier, results[i])
    wall = time.monotonic() - t0
    stop_evt.set()
    st.join()

    oc = Counter(r.get('outcome', 'unknown') for r in results)
    http200 = sum(1 for r in results if r.get('http') == 200)
    ttfts = sorted(r['ttft'] for r in results if 'ttft' in r)
    heads = sorted(r['t_head'] for r in results if 't_head' in r)
    gaps = sorted(r['max_gap'] for r in results if 'max_gap' in r)
    helds = sorted(r['held'] for r in results if 'held' in r)

    def pct(arr, p):
        return round(arr[min(len(arr) - 1, int(len(arr) * p))], 2) if arr else None

    summary = {
        'level': level, 'wall_s': round(wall, 1), 'hold_s': HOLD,
        'http200': http200, 'outcomes': dict(oc),
        'thead_p50': pct(heads, .5), 'thead_max': round(heads[-1], 2) if heads else None,
        'ttft_p50': pct(ttfts, .5), 'ttft_p95': pct(ttfts, .95),
        'max_chunk_gap_p95': pct(gaps, .95), 'max_chunk_gap_max': round(gaps[-1], 2) if gaps else None,
        'held_p50': pct(helds, .5),
        'peak_app_established': max((s['app_est'] for s in samples), default=0),
        'peak_mock_established': max((s['mock_est'] for s in samples), default=0),
        'peak_load': max((s['load'] for s in samples), default=0),
        'peak_jvm_threads': max((s['jthreads'] for s in samples), default=0),
    }
    print(json.dumps(summary, ensure_ascii=False), flush=True)
    return summary


def main():
    global APP_PID
    if not MODEL_ID:
        print('需要 MODEL_ID 环境变量（mock 模型行 id）', file=sys.stderr)
        sys.exit(1)
    out = subprocess.run(['pgrep', '-f', 'server.port=28080'], capture_output=True, text=True).stdout.split()
    APP_PID = out[0] if out else None
    print(f'APP_PID(instance2)={APP_PID}', file=sys.stderr)

    max_level = max(LEVELS)
    print(f'预创建 {max_level} 个游客账号（实例{TARGET_PORT}）...', file=sys.stderr)
    accounts = []
    with ThreadPoolExecutor(max_workers=20) as ex:
        futs = [ex.submit(mint_account, i) for i in range(max_level)]
        for i, f in enumerate(futs):
            r = f.result()
            if r:
                accounts.append({'idx': i, 'cookie': r[0], 'uid': r[1], 'sid': r[2]})
    print(f'账号就绪 {len(accounts)}/{max_level}', file=sys.stderr)
    if len(accounts) < max_level:
        print('账号不足，终止', file=sys.stderr)
        sys.exit(1)

    all_summaries = []
    for level in LEVELS:
        print(f'--- level {level} (hold {HOLD}s) ---', file=sys.stderr)
        s = run_level(level, accounts[:level])
        all_summaries.append(s)
        http200_rate = s['http200'] / level
        if s['peak_load'] > LOAD_STOP:
            print(f'负载 {s["peak_load"]} 超阈值，停止升级', file=sys.stderr)
            break
        if http200_rate < 0.95:
            print(f'连接建立率 {http200_rate:.0%} 过低（连接层拐点），停止升级', file=sys.stderr)
            break
        time.sleep(10)

    with open('/tmp/sse_stress_mock_result.json', 'w') as f:
        json.dump(all_summaries, f, ensure_ascii=False, indent=1)
    print('saved /tmp/sse_stress_mock_result.json', file=sys.stderr)


if __name__ == '__main__':
    main()
