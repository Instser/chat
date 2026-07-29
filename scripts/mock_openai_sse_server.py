#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
本地 OpenAI 兼容流式 Mock（零 AI token）。

协议：POST /v1/chat/completions + stream=true
返回：
  data: {"choices":[{"delta":{"content":"..."}}]}
  data: [DONE]

用法：
  python scripts/mock_openai_sse_server.py --port 18080

然后把测试用模型的 baseUrl 临时指到：
  http://127.0.0.1:18080/v1/chat/completions
apiKey 任意非空即可。

注意：只用于压测/联调，不要长期指向生产模型配置。
"""
from __future__ import annotations

import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Handler(BaseHTTPRequestHandler):
    delay_ms = 20
    chunks = 12
    text = "MOCK-OK zero-token concurrent stream. "

    def log_message(self, fmt, *args):
        # 压测时减少噪音
        if self.path.endswith("/health"):
            return
        super().log_message(fmt, *args)

    def do_GET(self):
        if self.path.startswith("/health"):
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"status":"UP","mock":true}')
            return
        self.send_response(404)
        self.end_headers()

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length else b"{}"
        try:
            body = json.loads(raw.decode("utf-8") or "{}")
        except Exception:
            body = {}

        stream = bool(body.get("stream", True))
        # 支持任意 path，方便 baseUrl 配成 .../v1/chat/completions
        if not stream:
            payload = {
                "id": "mock-1",
                "object": "chat.completion",
                "choices": [{"message": {"role": "assistant", "content": self.text}, "finish_reason": "stop"}],
            }
            data = json.dumps(payload).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return

        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "keep-alive")
        self.end_headers()

        # 逐片输出，模拟真流
        piece = max(1, len(self.text) // self.chunks)
        for i in range(0, len(self.text), piece):
            chunk = self.text[i : i + piece]
            frame = {
                "id": "mock-chat",
                "object": "chat.completion.chunk",
                "choices": [{"delta": {"content": chunk}, "index": 0}],
            }
            line = f"data: {json.dumps(frame, ensure_ascii=False)}\n\n"
            self.wfile.write(line.encode("utf-8"))
            self.wfile.flush()
            if self.delay_ms > 0:
                time.sleep(self.delay_ms / 1000.0)

        self.wfile.write(b"data: [DONE]\n\n")
        self.wfile.flush()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=18080)
    ap.add_argument("--delay-ms", type=int, default=20, help="每片间隔，0=尽快刷完")
    ap.add_argument("--text", default="MOCK-OK zero-token concurrent stream. ")
    args = ap.parse_args()

    Handler.delay_ms = args.delay_ms
    Handler.text = args.text

    httpd = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"mock OpenAI SSE on http://{args.host}:{args.port}")
    print(f"point model baseUrl to http://{args.host}:{args.port}/v1/chat/completions")
    print("GET /health for liveness")
    httpd.serve_forever()


if __name__ == "__main__":
    main()
