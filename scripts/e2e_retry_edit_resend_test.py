#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""真实联调：retry / edit-resend 非阻塞流路径。"""
from __future__ import annotations

import json
import sys
import time
from http.cookiejar import CookieJar
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
from urllib.request import Request, build_opener, HTTPCookieProcessor

BASE = "http://127.0.0.1:8080/api"
OUT = Path("logs/e2e")
OUT.mkdir(parents=True, exist_ok=True)


class Client:
    def __init__(self) -> None:
        self.cj = CookieJar()
        self.op = build_opener(HTTPCookieProcessor(self.cj))

    def json(self, method: str, path: str, body: Any = None, timeout: float = 30) -> Dict[str, Any]:
        data = None
        headers = {"Accept": "application/json"}
        if body is not None:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        req = Request(BASE + path, data=data, headers=headers, method=method)
        with self.op.open(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8", errors="replace"))

    def stream(self, path: str, body: Dict[str, Any], max_wait_s: float = 90) -> Dict[str, Any]:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers = {
            "Accept": "text/event-stream",
            "Content-Type": "application/json",
        }
        req = Request(BASE + path, data=data, headers=headers, method="POST")
        t0 = time.time()
        resp = self.op.open(req, timeout=max_wait_s + 10)
        headers_out = dict(resp.headers.items())
        lines: List[str] = []
        zero = d = u = 0
        content_parts: List[str] = []
        first = None
        err = None
        try:
            while True:
                if time.time() - t0 > max_wait_s:
                    err = f"timeout {max_wait_s}s"
                    break
                b = resp.readline()
                if not b:
                    break
                if first is None:
                    first = time.time()
                s = b.decode("utf-8", errors="replace").rstrip("\n")
                if not s:
                    continue
                lines.append(s)
                if s.startswith("0:"):
                    zero += 1
                    try:
                        payload = s[2:].strip()
                        text = json.loads(payload) if payload.startswith('"') else payload
                        if isinstance(text, str):
                            content_parts.append(text)
                    except Exception:
                        content_parts.append(s[2:])
                elif s.startswith("d:"):
                    d += 1
                    break
                elif s.startswith("u:"):
                    u += 1
                if s.startswith("data: 0:") or s.startswith("data: d:"):
                    err = "SSE re-wrapped frames"
        finally:
            try:
                resp.close()
            except Exception:
                pass
        full = "".join(content_parts)
        return {
            "http": getattr(resp, "status", 200),
            "headers": {
                "content_type": headers_out.get("Content-Type") or headers_out.get("content-type"),
                "x_vercel": headers_out.get("x-vercel-ai-data-stream") or headers_out.get("X-Vercel-Ai-Data-Stream"),
                "x_accel": headers_out.get("X-Accel-Buffering") or headers_out.get("x-accel-buffering"),
            },
            "ttfb_ms": int((first - t0) * 1000) if first else None,
            "total_ms": int((time.time() - t0) * 1000),
            "zero_frames": zero,
            "d_frames": d,
            "u_frames": u,
            "content_len": len(full),
            "sample": full[:200],
            "last_frames": lines[-5:],
            "error": err,
            "ok": zero > 0 and d >= 1 and err is None,
            "raw_lines": lines,
        }


def main() -> int:
    c = Client()
    results = []

    login = c.json("POST", "/auth/login", {"usernameOrEmail": "testuser", "password": "123456"})
    ok = login.get("code") == 200
    results.append({"name": "login", "ok": ok, "detail": login.get("message")})
    print(f"[{'PASS' if ok else 'FAIL'}] login: {login.get('message')}")
    if not ok:
        return 1

    # seed session + chat
    sid = c.json("POST", "/chat/sessions", {"model": "deepseek-chat"})["data"]["sessionId"]
    print(f"[INFO] seed session={sid}")
    seed = c.stream(
        "/chat",
        {
            "sessionId": sid,
            "modelId": 1,
            "messages": [{"role": "user", "content": "请只回复一个词：ALPHA"}],
            "deepThinking": False,
            "network": False,
        },
        max_wait_s=90,
    )
    results.append({"name": "seed_chat", "ok": seed["ok"], "detail": seed})
    print(f"[{'PASS' if seed['ok'] else 'FAIL'}] seed_chat: 0={seed['zero_frames']} d={seed['d_frames']} sample={seed['sample']!r}")
    (OUT / "retry_seed_stream.txt").write_text("\n".join(seed["raw_lines"]), encoding="utf-8")

    msgs = c.json("GET", f"/chat/sessions/{sid}/messages").get("data") or []
    user_msg = next((m for m in msgs if m.get("role") == "user"), None)
    asst_msg = next((m for m in reversed(msgs) if m.get("role") == "assistant"), None)
    print(f"[INFO] msgs={len(msgs)} user={user_msg and user_msg.get('messageId')} asst={asst_msg and asst_msg.get('messageId')}")
    if not user_msg or not asst_msg:
        results.append({"name": "seed_messages", "ok": False, "detail": f"count={len(msgs)}"})
        _dump(results)
        return 1
    results.append({"name": "seed_messages", "ok": True, "detail": {"count": len(msgs)}})

    # ---- retry: regenerate assistant ----
    retry = c.stream(
        "/chat/retry",
        {
            "chatId": sid,
            "messageId": asst_msg["messageId"],
            "modelId": 1,
            "network": False,
        },
        max_wait_s=90,
    )
    results.append({"name": "retry_stream", "ok": retry["ok"], "detail": retry})
    print(
        f"[{'PASS' if retry['ok'] else 'FAIL'}] retry_stream: "
        f"0={retry['zero_frames']} d={retry['d_frames']} ttfb={retry['ttfb_ms']}ms "
        f"total={retry['total_ms']}ms vercel={retry['headers']['x_vercel']} sample={retry['sample']!r}"
    )
    (OUT / "retry_stream.txt").write_text("\n".join(retry["raw_lines"]), encoding="utf-8")

    msgs_after_retry = c.json("GET", f"/chat/sessions/{sid}/messages").get("data") or []
    # retry deletes target+after then writes new assistant; expect user + new assistant
    retry_persist_ok = len(msgs_after_retry) >= 2 and any(m.get("role") == "assistant" for m in msgs_after_retry)
    results.append({"name": "retry_persist", "ok": retry_persist_ok, "detail": {"count": len(msgs_after_retry)}})
    print(f"[{'PASS' if retry_persist_ok else 'FAIL'}] retry_persist: count={len(msgs_after_retry)}")

    # refresh ids after retry
    msgs = msgs_after_retry
    user_msg = next((m for m in msgs if m.get("role") == "user"), None)
    if not user_msg:
        results.append({"name": "edit_resend_stream", "ok": False, "detail": "no user msg after retry"})
        _dump(results)
        return 1

    # ---- edit-resend: edit user message ----
    edit = c.stream(
        "/chat/edit-resend",
        {
            "chatId": sid,
            "messageId": user_msg["messageId"],
            "newContent": "请只回复一个词：BETA",
            "modelId": 1,
            "network": False,
        },
        max_wait_s=90,
    )
    results.append({"name": "edit_resend_stream", "ok": edit["ok"], "detail": edit})
    print(
        f"[{'PASS' if edit['ok'] else 'FAIL'}] edit_resend_stream: "
        f"0={edit['zero_frames']} d={edit['d_frames']} ttfb={edit['ttfb_ms']}ms "
        f"total={edit['total_ms']}ms vercel={edit['headers']['x_vercel']} sample={edit['sample']!r}"
    )
    (OUT / "edit_resend_stream.txt").write_text("\n".join(edit["raw_lines"]), encoding="utf-8")

    msgs_final = c.json("GET", f"/chat/sessions/{sid}/messages").get("data") or []
    user_final = next((m for m in msgs_final if m.get("role") == "user"), None)
    asst_final = next((m for m in reversed(msgs_final) if m.get("role") == "assistant"), None)
    content_ok = bool(user_final and "BETA" in (user_final.get("content") or ""))
    persist_ok = len(msgs_final) >= 2 and asst_final is not None and content_ok
    results.append(
        {
            "name": "edit_resend_persist",
            "ok": persist_ok,
            "detail": {
                "count": len(msgs_final),
                "user_content": (user_final or {}).get("content"),
                "asst_sample": ((asst_final or {}).get("content") or "")[:80],
            },
        }
    )
    print(
        f"[{'PASS' if persist_ok else 'FAIL'}] edit_resend_persist: count={len(msgs_final)} "
        f"user={(user_final or {}).get('content')!r} asst={((asst_final or {}).get('content') or '')[:40]!r}"
    )
    (OUT / "retry_edit_messages.json").write_text(json.dumps(msgs_final, ensure_ascii=False, indent=2), encoding="utf-8")

    # protocol sanity on both
    for name, m in [("retry_protocol", retry), ("edit_protocol", edit)]:
        proto_ok = (
            m["headers"]["x_vercel"] == "v1"
            and m["headers"]["x_accel"] == "no"
            and (m["headers"]["content_type"] or "").startswith("text/event-stream")
            and not any(x.startswith("data: 0:") for x in m["raw_lines"][:3])
        )
        results.append({"name": name, "ok": proto_ok, "detail": m["headers"]})
        print(f"[{'PASS' if proto_ok else 'FAIL'}] {name}: {m['headers']}")

    report = _dump(results)
    failed = [r for r in results if not r["ok"]]
    print("\n==== SUMMARY ====")
    print(f"total={len(results)} pass={len(results)-len(failed)} fail={len(failed)} report={report}")
    for f in failed:
        print(" FAIL", f["name"], f.get("detail"))
    return 1 if failed else 0


def _dump(results: List[Dict[str, Any]]) -> Path:
    # strip huge raw_lines from report
    slim = []
    for r in results:
        d = r.get("detail")
        if isinstance(d, dict) and "raw_lines" in d:
            d = {k: v for k, v in d.items() if k != "raw_lines"}
        slim.append({"name": r["name"], "ok": r["ok"], "detail": d})
    p = OUT / "retry_edit_report.json"
    p.write_text(json.dumps({"time": time.strftime("%Y-%m-%d %H:%M:%S"), "results": slim}, ensure_ascii=False, indent=2), encoding="utf-8")
    return p


if __name__ == "__main__":
    sys.exit(main())
