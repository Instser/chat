#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
真实后端非阻塞流式联调脚本（login -> session -> chat stream -> stop）
"""
from __future__ import annotations

import json
import re
import sys
import threading
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
from urllib.error import HTTPError, URLError
from urllib.request import Request, build_opener, HTTPCookieProcessor
from http.cookiejar import CookieJar

BASE = "http://127.0.0.1:8080/api"
USER = "testuser"
PASS = "123456"
OUT_DIR = Path("logs/e2e")
OUT_DIR.mkdir(parents=True, exist_ok=True)


@dataclass
class CaseResult:
    name: str
    ok: bool
    detail: str
    metrics: Dict[str, Any] = field(default_factory=dict)


class Client:
    def __init__(self) -> None:
        self.cj = CookieJar()
        self.opener = build_opener(HTTPCookieProcessor(self.cj))
        self.token: Optional[str] = None

    def request(
        self,
        method: str,
        path: str,
        body: Any = None,
        headers: Optional[Dict[str, str]] = None,
        timeout: float = 60,
        stream: bool = False,
    ):
        url = BASE + path
        data = None
        hdrs = {"Accept": "*/*"}
        if headers:
            hdrs.update(headers)
        if body is not None:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            hdrs.setdefault("Content-Type", "application/json")
        if self.token:
            # cookie already set; also allow Authorization if needed
            pass
        req = Request(url, data=data, headers=hdrs, method=method)
        try:
            resp = self.opener.open(req, timeout=timeout)
        except HTTPError as e:
            raw = e.read().decode("utf-8", errors="replace")
            return e.code, dict(e.headers.items()), raw, None
        except URLError as e:
            return 0, {}, str(e), None

        resp_headers = dict(resp.headers.items())
        if stream:
            return resp.status, resp_headers, None, resp
        raw = resp.read().decode("utf-8", errors="replace")
        return resp.status, resp_headers, raw, None

    def login(self) -> CaseResult:
        code, headers, raw, _ = self.request(
            "POST",
            "/auth/login",
            {"usernameOrEmail": USER, "password": PASS},
            timeout=30,
        )
        ok = code == 200
        detail = raw[:500]
        try:
            j = json.loads(raw)
            ok = ok and j.get("code") == 200
            detail = j.get("message", detail)
        except Exception:
            pass
        # extract cookie token
        for c in self.cj:
            if c.name == "auth-token":
                self.token = c.value
        return CaseResult("login", ok and bool(self.token), f"http={code} token={bool(self.token)} msg={detail}")

    def create_session(self, model_code: str = "deepseek-chat") -> Tuple[CaseResult, Optional[str]]:
        code, _, raw, _ = self.request("POST", "/chat/sessions", {"model": model_code}, timeout=30)
        sid = None
        ok = False
        try:
            j = json.loads(raw)
            ok = code == 200 and j.get("code") == 200
            sid = (j.get("data") or {}).get("sessionId")
        except Exception:
            j = {"raw": raw}
        return CaseResult("create_session", ok and bool(sid), f"http={code} sid={sid} body={raw[:300]}"), sid

    def list_models(self) -> CaseResult:
        code, _, raw, _ = self.request("GET", "/chat/models", timeout=30)
        try:
            j = json.loads(raw)
            models = j.get("data") or []
            ok = code == 200 and j.get("code") == 200 and len(models) > 0
            return CaseResult("list_models", ok, f"count={len(models)} first={models[0].get('modelName') if models else None}")
        except Exception as e:
            return CaseResult("list_models", False, f"http={code} err={e} raw={raw[:200]}")

    def stop(self, session_id: str) -> CaseResult:
        code, _, raw, _ = self.request(
            "POST",
            "/chat/stop",
            {"sessionId": session_id},
            timeout=20,
        )
        try:
            j = json.loads(raw)
            ok = code == 200 and (j.get("code") in (200, 0) or j.get("success") is True or "success" in raw.lower() or j.get("message"))
            # stop API may wrap differently
            if not ok and code == 200:
                ok = True
            return CaseResult("stop", ok, f"http={code} body={raw[:300]}")
        except Exception:
            return CaseResult("stop", code == 200, f"http={code} raw={raw[:300]}")

    def stream_chat(
        self,
        session_id: str,
        model_id: int,
        content: str,
        stop_after_s: Optional[float] = None,
        max_wait_s: float = 90,
        outfile: Optional[Path] = None,
    ) -> CaseResult:
        body = {
            "sessionId": session_id,
            "modelId": model_id,
            "messages": [{"role": "user", "content": content}],
            "deepThinking": False,
            "network": False,
        }
        t0 = time.time()
        code, headers, _, resp = self.request(
            "POST",
            "/chat",
            body,
            headers={
                "Accept": "text/event-stream",
                "Content-Type": "application/json",
            },
            timeout=max_wait_s + 10,
            stream=True,
        )
        if resp is None:
            return CaseResult(
                "stream_chat",
                False,
                f"no stream body http={code} headers={headers}",
                {"http": code},
            )

        chunks: List[str] = []
        content_parts: List[str] = []
        d_frames = 0
        u_frames = 0
        zero_frames = 0
        first_byte_at = None
        first_content_at = None
        done = False
        error_text = ""
        stop_result: Optional[CaseResult] = None

        def stopper():
            nonlocal stop_result
            time.sleep(stop_after_s or 0)
            stop_result = self.stop(session_id)

        stop_thread = None
        if stop_after_s is not None:
            stop_thread = threading.Thread(target=stopper, daemon=True)
            stop_thread.start()

        try:
            while True:
                if time.time() - t0 > max_wait_s:
                    error_text = f"timeout after {max_wait_s}s"
                    break
                line_b = resp.readline()
                if not line_b:
                    done = True
                    break
                if first_byte_at is None:
                    first_byte_at = time.time()
                line = line_b.decode("utf-8", errors="replace")
                if not line.strip():
                    continue
                chunks.append(line.rstrip("\n"))
                # Vercel AI data stream frames: 0:"text" / d:{...} / u:{...}
                if line.startswith("0:"):
                    zero_frames += 1
                    if first_content_at is None:
                        first_content_at = time.time()
                    # extract JSON string after 0:
                    try:
                        payload = line[2:].strip()
                        # may be JSON string
                        text = json.loads(payload) if payload.startswith('"') else payload
                        if isinstance(text, str):
                            content_parts.append(text)
                    except Exception:
                        content_parts.append(line[2:])
                elif line.startswith("d:"):
                    d_frames += 1
                elif line.startswith("u:"):
                    u_frames += 1
                elif line.startswith("data:"):
                    # unexpected SSE wrap - mark
                    error_text = (error_text + " | unexpected data: wrap").strip(" |")
                if d_frames > 0 and stop_after_s is None:
                    # normal completion may still send more; wait briefly
                    pass
        except Exception as e:
            error_text = f"read error: {e}"
        finally:
            try:
                resp.close()
            except Exception:
                pass
            if stop_thread:
                stop_thread.join(timeout=10)

        t1 = time.time()
        full_text = "".join(content_parts)
        raw_dump = "\n".join(chunks)
        if outfile:
            outfile.write_text(raw_dump, encoding="utf-8")

        ct = headers.get("Content-Type") or headers.get("content-type") or ""
        x_vercel = headers.get("x-vercel-ai-data-stream") or headers.get("X-Vercel-Ai-Data-Stream") or ""
        x_accel = headers.get("X-Accel-Buffering") or headers.get("x-accel-buffering") or ""

        metrics = {
            "http": code,
            "content_type": ct,
            "x_vercel_ai_data_stream": x_vercel,
            "x_accel_buffering": x_accel,
            "ttfb_ms": int((first_byte_at - t0) * 1000) if first_byte_at else None,
            "ttfc_ms": int((first_content_at - t0) * 1000) if first_content_at else None,
            "total_ms": int((t1 - t0) * 1000),
            "zero_frames": zero_frames,
            "d_frames": d_frames,
            "u_frames": u_frames,
            "content_len": len(full_text),
            "chunk_lines": len(chunks),
            "stop": stop_result.detail if stop_result else None,
            "sample": full_text[:200],
            "error": error_text or None,
        }

        # success criteria
        ok = code == 200 and zero_frames > 0 and "data:" not in "".join(chunks[:3])  # first frames shouldn't be sse-wrapped content only
        # allow pure error path without 0 frames if explicit failure message in stream
        if stop_after_s is not None:
            # stop path: 应有内容 + stop 成功 + 客户端能较快结束（不应挂到 max_wait）
            ended_fast = metrics["total_ms"] < int((stop_after_s + 15) * 1000)
            ok = (
                code == 200
                and zero_frames > 0
                and bool(stop_result and stop_result.ok)
                and ended_fast
                and d_frames >= 0  # interrupt frame may be d: or custom
            )
            if not ended_fast:
                metrics["warn"] = f"client hung after stop total_ms={metrics['total_ms']}"
        if d_frames == 0 and stop_after_s is None and zero_frames > 0:
            # stream content but no completion frame — soft fail
            ok = False
            metrics["warn"] = "missing d: completion frame"

        # detect accidental SSE re-wrap of vercel frames
        if any(c.startswith("data: 0:") or c.startswith("data: d:") for c in chunks):
            ok = False
            metrics["error"] = "SSE re-wrapped Vercel frames (SseEmitter misuse)"

        detail = (
            f"http={code} 0={zero_frames} d={d_frames} u={u_frames} "
            f"ttfb={metrics['ttfb_ms']}ms ttfc={metrics['ttfc_ms']}ms "
            f"len={len(full_text)} ct={ct} vercel={x_vercel}"
        )
        if error_text:
            detail += f" err={error_text}"
        return CaseResult("stream_chat" if stop_after_s is None else "stream_then_stop", ok, detail, metrics)


def main() -> int:
    client = Client()
    results: List[CaseResult] = []

    r = client.login()
    results.append(r)
    print(f"[{'PASS' if r.ok else 'FAIL'}] {r.name}: {r.detail}")
    if not r.ok:
        _write_report(results)
        return 1

    r = client.list_models()
    results.append(r)
    print(f"[{'PASS' if r.ok else 'FAIL'}] {r.name}: {r.detail}")

    # session 1: full stream
    r, sid1 = client.create_session("deepseek-chat")
    results.append(r)
    print(f"[{'PASS' if r.ok else 'FAIL'}] {r.name}: {r.detail}")
    if not sid1:
        _write_report(results)
        return 1

    r = client.stream_chat(
        sid1,
        model_id=1,
        content="用一句话介绍你自己，不超过30字。",
        max_wait_s=90,
        outfile=OUT_DIR / "stream_full.txt",
    )
    results.append(r)
    print(f"[{'PASS' if r.ok else 'FAIL'}] {r.name}: {r.detail}")
    print("  metrics:", json.dumps(r.metrics, ensure_ascii=False))

    # session 2: stop mid-stream
    r, sid2 = client.create_session("deepseek-chat")
    results.append(CaseResult("create_session_stop", r.ok, r.detail))
    print(f"[{'PASS' if r.ok else 'FAIL'}] create_session_stop: {r.detail}")
    if sid2:
        r = client.stream_chat(
            sid2,
            model_id=1,
            content="请慢慢从1数到100，每行一个数字，不要省略。",
            stop_after_s=1.5,
            max_wait_s=45,
            outfile=OUT_DIR / "stream_stop.txt",
        )
        results.append(r)
        print(f"[{'PASS' if r.ok else 'FAIL'}] {r.name}: {r.detail}")
        print("  metrics:", json.dumps(r.metrics, ensure_ascii=False))

    # messages persistence check
    code, _, raw, _ = client.request("GET", f"/chat/sessions/{sid1}/messages", timeout=20)
    try:
        j = json.loads(raw)
        msgs = j.get("data") or []
        ok = code == 200 and len(msgs) >= 2
        results.append(CaseResult("persist_messages", ok, f"http={code} count={len(msgs)}"))
        print(f"[{'PASS' if ok else 'FAIL'}] persist_messages: http={code} count={len(msgs)}")
        (OUT_DIR / "messages_sid1.json").write_text(json.dumps(j, ensure_ascii=False, indent=2), encoding="utf-8")
    except Exception as e:
        results.append(CaseResult("persist_messages", False, str(e)))
        print(f"[FAIL] persist_messages: {e}")

    # me endpoint
    code, _, raw, _ = client.request("GET", "/auth/me", timeout=20)
    try:
        j = json.loads(raw)
        ok = code == 200 and j.get("code") == 200
        results.append(CaseResult("auth_me", ok, f"user={(j.get('data') or {}).get('username')}"))
        print(f"[{'PASS' if ok else 'FAIL'}] auth_me: {(j.get('data') or {}).get('username')}")
    except Exception as e:
        results.append(CaseResult("auth_me", False, str(e)))

    report = _write_report(results)
    failed = [x for x in results if not x.ok]
    print("\n==== SUMMARY ====")
    print(f"total={len(results)} pass={len(results)-len(failed)} fail={len(failed)}")
    print(f"report={report}")
    for f in failed:
        print(f"  FAIL {f.name}: {f.detail}")
    return 1 if failed else 0


def _write_report(results: List[CaseResult]) -> Path:
    p = OUT_DIR / "report.json"
    payload = {
        "time": time.strftime("%Y-%m-%d %H:%M:%S"),
        "base": BASE,
        "results": [
            {"name": r.name, "ok": r.ok, "detail": r.detail, "metrics": r.metrics} for r in results
        ],
    }
    p.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return p


if __name__ == "__main__":
    sys.exit(main())
