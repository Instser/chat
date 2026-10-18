#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
限流阈值实测验证（在生产实例本机 loopback 运行）。

验证项：
A. 试用激活：同一 IP 每天最多 10 次（TRIAL_PER_IP_DAILY_LIMIT）→ 第 11 次拒绝
B. 单用户聊天频率：20 次/分钟（ChatRateLimitServiceImpl.USER_RATE_LIMIT_PER_MINUTE）
C. 单 IP 聊天频率：30 次/分钟（IP_RATE_LIMIT_PER_MINUTE）
D. 游客对话额度：10 次/账号（TRIAL_CHAT_LIMIT）
E. 单用户单活跃会话互斥（hasActiveSession）

方法要点：
- 聊天频率计数发生在 validateChatRateLimit（套餐/额度校验之后、活跃会话检查之前），
  因此"盲发"（发完即断、不读响应）也会真实计数；据此可在一分钟内打满窗口。
- 盲发请求在客户端断开后由 AsyncContext 监听取消上游订阅，不会完整消耗 AI 额度。
"""
import http.client
import json
import sys
import threading
import time

HOST, PORT = '127.0.0.1', 8080


def mint(ip):
    conn = http.client.HTTPConnection(HOST, PORT, timeout=30)
    try:
        conn.request('POST', '/api/auth/trial', body='{}',
                     headers={'Content-Type': 'application/json', 'X-Forwarded-For': ip})
        resp = conn.getresponse()
        data = resp.read()
        cookie = resp.getheader('Set-Cookie')
        if resp.status == 200 and cookie:
            uid = json.loads(data)['data']['id']
            return cookie.split(';')[0], uid
        return None, json.loads(data).get('message') if data else f'http_{resp.status}'
    finally:
        conn.close()


def chat(cookie, ip, blind=True, prompt='hi'):
    """发起一次聊天。blind=True 时发送后立即断开（仍完成服务端限流计数）。
    返回 (首帧文本摘要, http状态)。"""
    conn = http.client.HTTPConnection(HOST, PORT, timeout=30)
    try:
        conn.request('POST', '/api/chat',
                     body=json.dumps({'messages': [{'role': 'user', 'content': prompt}], 'modelId': 1}),
                     headers={'Content-Type': 'application/json', 'Accept': 'text/event-stream',
                              'Cookie': cookie, 'X-Forwarded-For': ip})
        if blind:
            time.sleep(0.5)       # 给服务端 prepare 阶段留时间（限流计数发生在这里）
            return 'blind', 0
        resp = conn.getresponse()
        data = resp.read(65536)   # 读前64KB足够看到错误帧或首批内容
        text = data.decode('utf-8', errors='replace')[:200].replace('\n', ' | ')
        return text, resp.status
    except Exception as e:
        return f'exc:{type(e).__name__}', -1
    finally:
        conn.close()


def line(msg):
    print(msg, flush=True)


def test_a_trial_ip_limit():
    line('=== A. 试用激活 IP 日限流（阈值10）===')
    ip = '10.66.66.66'
    results = []
    for i in range(11):
        cookie, info = mint(ip)
        results.append(info is not None and cookie is not None)
        line(f'  第{i+1}次激活: {"成功" if cookie else "拒绝:" + str(info)}')
    ok = sum(results)
    line(f'  结果: {ok}/11 成功, {11-ok} 拒绝 -> 阈值={"10次/天 验证" + ("通过" if ok == 10 else "异常:" + str(ok))}')
    return ok == 10


def test_b_user_rate_limit():
    line('=== B. 单用户聊天频率（阈值20次/分钟）===')
    cookie, uid = mint('10.66.0.1')
    if not cookie:
        line(f'  建号失败: {uid}')
        return False
    for i in range(20):
        chat(cookie, '10.66.0.1', blind=True)
        time.sleep(0.35)
    text, status = chat(cookie, '10.66.0.1', blind=False)
    hit = 'frequency is too high' in text
    line(f'  第21个请求响应: HTTP {status}: {text[:120]}')
    line(f'  结果: {"限流触发，阈值20次/分钟验证通过" if hit else "未触发限流，验证失败"}')
    return hit


def test_c_ip_rate_limit():
    line('=== C. 单IP聊天频率（阈值30次/分钟）===')
    shared_ip = '10.66.77.77'
    accounts = []
    for i in range(6):  # 6个账号（各自用户维度余量充足），共享同一聊天IP
        cookie, uid = mint(f'10.66.88.{i}')
        if cookie:
            accounts.append(cookie)
    if len(accounts) < 6:
        line(f'  建号不足: {len(accounts)}')
        return False
    for i in range(30):
        chat(accounts[i % 5], shared_ip, blind=True)   # 留第6个账号做观测
        time.sleep(0.25)
    text, status = chat(accounts[5], shared_ip, blind=False)
    hit = 'frequency is too high' in text
    line(f'  第31个请求响应: HTTP {status}: {text[:120]}')
    line(f'  结果: {"IP限流触发，阈值30次/分钟验证通过" if hit else "未触发，验证失败"}')
    return hit


def test_d_guest_quota():
    line('=== D. 游客对话额度（阈值10次/账号，AI成功回复计1次）===')
    cookie, uid = mint('10.66.99.1')
    if not cookie:
        line(f'  建号失败: {uid}')
        return False
    done = 0
    for i in range(11):
        text, status = chat(cookie, f'10.66.99.{i+1}', blind=False, prompt='用中文说一个字')
        if '试用额度已用完' in text:
            line(f'  第{i+1}次聊天: 额度耗尽提示出现 -> {text[:80]}')
            break
        elif 'finishReason' in text or '0:"' in text:
            done += 1
            line(f'  第{i+1}次聊天: AI正常回复')
        else:
            line(f'  第{i+1}次聊天: 其他响应 HTTP {status}: {text[:80]}')
        time.sleep(1.2)  # 等会话完全结束，避免活跃会话互斥干扰
    line(f'  结果: AI成功回复{done}次后出现额度提示 -> {"10次额度验证通过" if done == 10 else "实测成功数=" + str(done)}')
    return done == 10


def test_e_active_session_mutex():
    line('=== E. 单用户单活跃会话互斥 ===')
    cookie, uid = mint('10.66.100.1')
    if not cookie:
        line(f'  建号失败: {uid}')
        return False
    out = {}

    def slow_chat(tag):
        text, status = chat(cookie, '10.66.100.1', blind=False, prompt='用中文写一段100字散文')
        out[tag] = text

    t1 = threading.Thread(target=slow_chat, args=('first',))
    t1.start()
    time.sleep(1.5)  # 确保第一条已进入流式阶段
    slow_chat('second')
    t1.join(timeout=60)
    mutex_hit = 'Currently chatting' in out.get('second', '')
    line(f'  并发第二请求响应: {out.get("second", "")[:100]}')
    line(f'  结果: {"互斥生效，验证通过" if mutex_hit else "未见互斥提示:" + out.get("second", "")[:60]}')
    return mutex_hit


if __name__ == '__main__':
    which = sys.argv[1] if len(sys.argv) > 1 else 'abcde'
    if 'a' in which:
        test_a_trial_ip_limit()
    if 'b' in which:
        test_b_user_rate_limit()
    if 'c' in which:
        test_c_ip_rate_limit()
    if 'd' in which:
        test_d_guest_quota()
    if 'e' in which:
        test_e_active_session_mutex()
