#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Mock 上游 LLM（OpenAI 兼容 SSE），用于不打真实上游的 SSE 长连接压测。
- POST /v1/chat/completions，stream=true → 以固定间隔吐出中文内容块（chunked 编码）
- 每块 2 个中文字符，默认 400 块 × 100ms ≈ 40s 一条流
- 每连接一个线程；每 5s 在 stderr 打印活跃连接统计
"""
import json
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 19399
CHUNKS = int(sys.argv[2]) if len(sys.argv) > 2 else 400
INTERVAL = float(sys.argv[3]) if len(sys.argv) > 3 else 0.1

SENTENCE = '这是一段用于长连接压测的流式内容，包含中文标点、数字123和emoji😊。'
active = 0
total = 0
lock = threading.Lock()


def sse_frame(obj):
    return ('data: ' + json.dumps(obj, ensure_ascii=False) + '\n\n').encode('utf-8')


class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'

    def log_message(self, *a):
        pass

    def do_POST(self):
        global active, total
        length = int(self.headers.get('Content-Length') or 0)
        body = self.rfile.read(length) if length else b'{}'
        try:
            req = json.loads(body)
        except Exception:
            req = {}
        if not req.get('stream'):
            # 非流式（标题等同步调用兜底）
            payload = {
                'id': 'mock', 'object': 'chat.completion',
                'choices': [{'index': 0, 'message': {'role': 'assistant', 'content': '压测标题'},
                             'finish_reason': 'stop'}],
                'usage': {'prompt_tokens': 1, 'completion_tokens': 1},
            }
            data = json.dumps(payload, ensure_ascii=False).encode('utf-8')
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return

        with lock:
            active += 1
            total += 1
        try:
            self.send_response(200)
            self.send_header('Content-Type', 'text/event-stream; charset=utf-8')
            self.send_header('Transfer-Encoding', 'chunked')
            self.send_header('Cache-Control', 'no-cache')
            self.end_headers()

            def write_chunk(b):
                self.wfile.write(('%x\r\n' % len(b)).encode() + b + b'\r\n')
                self.wfile.flush()

            # 首包立即给（模拟真实上游 TTFT）
            chars = SENTENCE
            for i in range(CHUNKS):
                piece = chars[(i * 2) % len(chars):(i * 2) % len(chars) + 2]
                frame = {'id': 'mock', 'object': 'chat.completion.chunk',
                         'choices': [{'index': 0, 'delta': {'content': piece}, 'finish_reason': None}]}
                write_chunk(sse_frame(frame))
                if i < CHUNKS - 1:
                    time.sleep(INTERVAL)
            write_chunk(sse_frame({'id': 'mock', 'object': 'chat.completion.chunk',
                                   'choices': [{'index': 0, 'delta': {}, 'finish_reason': 'stop'}],
                                   'usage': {'prompt_tokens': 10, 'completion_tokens': CHUNKS * 2}}))
            write_chunk(b'data: [DONE]\n\n')
            self.wfile.write(b'0\r\n\r\n')
            self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError):
            pass
        finally:
            with lock:
                active -= 1


def stats_loop():
    while True:
        time.sleep(5)
        with lock:
            print(f'[mock] active={active} total={total}', file=sys.stderr, flush=True)


class MockServer(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 2048   # 默认 5，高并发建连会排队失败


if __name__ == '__main__':
    threading.Thread(target=stats_loop, daemon=True).start()
    srv = MockServer(('127.0.0.1', PORT), Handler)
    print(f'mock upstream listening on 127.0.0.1:{PORT}, chunks={CHUNKS}, interval={INTERVAL}s', flush=True)
    srv.serve_forever()
