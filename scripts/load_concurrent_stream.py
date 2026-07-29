#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
真实并发检测：登录后并发打 /chat 流式接口。

适用本项目特点：
- Cookie JWT（auth-token）
- Vercel AI data stream（0:/d: 帧，不是纯 JSON）
- 长连接流式，不能用只看 HTTP 状态码的短请求压测

用法示例：
  python scripts/load_concurrent_stream.py --base http://127.0.0.1:8080/api --users 5 --concurrency 5
  python scripts/load_concurrent_stream.py --base https://your-server/api --users 20 --concurrency 10 --duration 0
"""
from __future__ import annotations

import argparse
import json
import statistics
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field, asdict
from http.cookiejar import CookieJar
from pathlib import Path
from typing import Any, Dict, List, Optional
from urllib.error import HTTPError, URLError
from urllib.request import Request, build_opener, HTTPCookieProcessor

OUT = Path("logs/load")
OUT.mkdir(parents=True, exist_ok=True)


@dataclass
class StreamResult:
    worker: int
    ok: bool
    http: int = 0
    session_id: str = ""
    ttfb_ms: Optional[int] = None
    total_ms: int = 0
    zero_frames: int = 0
    d_frames: int = 0
    content_len: int = 0
    error: str = ""
    sample: str = ""


@dataclass
class Summary:
    base: str
    concurrency: int
    total: int
    success: int
    fail: int
    success_rate: float
    ttfb_p50_ms: Optional[float]
    ttfb_p95_ms: Optional[float]
    ttfb_avg_ms: Optional[float]
    total_p50_ms: Optional[float]
    total_p95_ms: Optional[float]
    total_avg_ms: Optional[float]
    rps: float
    zero_frames_sum: int
    errors: Dict[str, int] = field(default_factory=dict)


def login(base: str, user: str, password: str) -> build_opener:
    cj = CookieJar()
    op = build_opener(HTTPCookieProcessor(cj))
    body = json.dumps({"usernameOrEmail": user, "password": password}).encode("utf-8")
    req = Request(
        base + "/auth/login",
        data=body,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    with op.open(req, timeout=30) as resp:
        raw = resp.read().decode("utf-8", errors="replace")
        j = json.loads(raw)
        if j.get("code") != 200:
            raise RuntimeError(f"login failed: {raw[:300]}")
    if not any(c.name == "auth-token" for c in cj):
        raise RuntimeError("login ok but no auth-token cookie")
    return op


def create_session(op, base: str, model: str = "deepseek-chat") -> str:
    body = json.dumps({"model": model}).encode("utf-8")
    req = Request(
        base + "/chat/sessions",
        data=body,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    with op.open(req, timeout=30) as resp:
        j = json.loads(resp.read().decode("utf-8", errors="replace"))
        sid = (j.get("data") or {}).get("sessionId")
        if not sid:
            raise RuntimeError(f"create session failed: {j}")
        return sid


def stream_chat(
    op,
    base: str,
    worker: int,
    model_id: int,
    prompt: str,
    max_wait_s: float,
) -> StreamResult:
    t0 = time.time()
    try:
        sid = create_session(op, base)
    except Exception as e:
        return StreamResult(worker=worker, ok=False, total_ms=int((time.time() - t0) * 1000), error=f"session:{e}")

    body = json.dumps(
        {
            "sessionId": sid,
            "modelId": model_id,
            "messages": [{"role": "user", "content": prompt}],
            "deepThinking": False,
            "network": False,
        },
        ensure_ascii=False,
    ).encode("utf-8")
    req = Request(
        base + "/chat",
        data=body,
        headers={
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
        },
        method="POST",
    )
    first = None
    zero = d = 0
    parts: List[str] = []
    err = ""
    http = 0
    try:
        resp = op.open(req, timeout=max_wait_s + 15)
        http = getattr(resp, "status", 200) or 200
        while True:
            if time.time() - t0 > max_wait_s:
                err = f"timeout>{max_wait_s}s"
                break
            line_b = resp.readline()
            if not line_b:
                break
            if first is None:
                first = time.time()
            line = line_b.decode("utf-8", errors="replace").rstrip("\n")
            if not line:
                continue
            if line.startswith("0:"):
                zero += 1
                try:
                    payload = line[2:].strip()
                    text = json.loads(payload) if payload.startswith('"') else payload
                    if isinstance(text, str):
                        parts.append(text)
                except Exception:
                    parts.append(line[2:])
            elif line.startswith("d:"):
                d += 1
                break
            elif line.startswith("data: 0:") or line.startswith("data: d:"):
                err = "sse-rewrapped"
        try:
            resp.close()
        except Exception:
            pass
    except HTTPError as e:
        http = e.code
        err = f"http:{e.code}:{e.read()[:120]!r}"
    except URLError as e:
        err = f"url:{e}"
    except Exception as e:
        err = f"ex:{e}"

    full = "".join(parts)
    total_ms = int((time.time() - t0) * 1000)
    ttfb = int((first - t0) * 1000) if first else None
    # 产品约束：同一用户同时只能有一个活跃聊天（Currently chatting...）
    if "Currently chatting" in full:
        err = err or "product_limit:active_session"
    ok = http == 200 and zero > 0 and d >= 1 and not err
    return StreamResult(
        worker=worker,
        ok=ok,
        http=http,
        session_id=sid,
        ttfb_ms=ttfb,
        total_ms=total_ms,
        zero_frames=zero,
        d_frames=d,
        content_len=len(full),
        error=err,
        sample=full[:80],
    )


def pct(values: List[float], p: float) -> Optional[float]:
    if not values:
        return None
    s = sorted(values)
    if len(s) == 1:
        return float(s[0])
    k = (len(s) - 1) * p
    f = int(k)
    c = min(f + 1, len(s) - 1)
    if f == c:
        return float(s[f])
    return float(s[f] + (s[c] - s[f]) * (k - f))


def main() -> int:
    ap = argparse.ArgumentParser(description="Concurrent stream load test for chat backend")
    ap.add_argument("--base", default="http://127.0.0.1:8080/api")
    ap.add_argument("--user", default="testuser")
    ap.add_argument("--password", default="123456")
    ap.add_argument("--concurrency", type=int, default=5, help="同时在途流数量")
    ap.add_argument("--users", type=int, default=5, help="总请求数（每个请求一条完整流）")
    ap.add_argument("--model-id", type=int, default=1)
    ap.add_argument("--max-wait", type=float, default=90.0)
    ap.add_argument(
        "--prompt",
        default="用一句话介绍你自己，不超过20字。",
        help="短 prompt 适合测并发；长 prompt 会拉高上游成本",
    )
    ap.add_argument(
        "--shared-login",
        action="store_true",
        help="不推荐：CookieJar 非线程安全，高并发下会 AccessDenied；默认每 worker 独立登录",
    )
    ap.add_argument(
        "--stagger-ms",
        type=int,
        default=0,
        help="启动错峰毫秒（测单用户串流排队时用；真并发请保持 0 并用多账号）",
    )
    args = ap.parse_args()

    print(f"base={args.base} concurrency={args.concurrency} total={args.users}")
    print("logging in / preparing workers...")

    shared_op = None
    if args.shared_login:
        shared_op = login(args.base, args.user, args.password)

    lock = threading.Lock()
    results: List[StreamResult] = []
    t_wall0 = time.time()

    def run_one(i: int) -> StreamResult:
        if args.stagger_ms > 0:
            time.sleep((args.stagger_ms / 1000.0) * i)
        try:
            op = shared_op if shared_op is not None else login(args.base, args.user, args.password)
        except Exception as e:
            return StreamResult(worker=i, ok=False, error=f"login:{e}")
        r = stream_chat(op, args.base, i, args.model_id, args.prompt, args.max_wait)
        with lock:
            status = "OK" if r.ok else "FAIL"
            print(
                f"[{status}] w={i} http={r.http} ttfb={r.ttfb_ms}ms total={r.total_ms}ms "
                f"0={r.zero_frames} d={r.d_frames} err={r.error or '-'} sample={r.sample!r}"
            )
        return r

    with ThreadPoolExecutor(max_workers=args.concurrency) as ex:
        futs = [ex.submit(run_one, i) for i in range(args.users)]
        for fut in as_completed(futs):
            results.append(fut.result())

    wall = time.time() - t_wall0
    ok_list = [r for r in results if r.ok]
    fail_list = [r for r in results if not r.ok]
    ttfbs = [float(r.ttfb_ms) for r in ok_list if r.ttfb_ms is not None]
    totals = [float(r.total_ms) for r in ok_list]
    err_count: Dict[str, int] = {}
    for r in fail_list:
        key = r.error.split(":")[0] if r.error else f"http{r.http}"
        err_count[key] = err_count.get(key, 0) + 1

    summary = Summary(
        base=args.base,
        concurrency=args.concurrency,
        total=len(results),
        success=len(ok_list),
        fail=len(fail_list),
        success_rate=round(len(ok_list) / max(1, len(results)), 4),
        ttfb_p50_ms=round(pct(ttfbs, 0.50), 1) if ttfbs else None,
        ttfb_p95_ms=round(pct(ttfbs, 0.95), 1) if ttfbs else None,
        ttfb_avg_ms=round(statistics.mean(ttfbs), 1) if ttfbs else None,
        total_p50_ms=round(pct(totals, 0.50), 1) if totals else None,
        total_p95_ms=round(pct(totals, 0.95), 1) if totals else None,
        total_avg_ms=round(statistics.mean(totals), 1) if totals else None,
        rps=round(len(results) / wall, 3) if wall > 0 else 0.0,
        zero_frames_sum=sum(r.zero_frames for r in results),
        errors=err_count,
    )

    report = {
        "time": time.strftime("%Y-%m-%d %H:%M:%S"),
        "summary": asdict(summary),
        "results": [asdict(r) for r in sorted(results, key=lambda x: x.worker)],
        "notes": [
            "流式并发：关注成功率、TTFB、是否缺 d: 帧、是否 429/限流、Tomcat 线程是否打满",
            "本脚本每请求新建 session + 完整等到 d:，成本=真实 AI 调用次数",
            "若测服务器：--base https://host/api 并保证网络/CORS/Cookie Domain 正确",
        ],
    }
    out = OUT / f"concurrent_{int(time.time())}.json"
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print("\n==== SUMMARY ====")
    print(json.dumps(asdict(summary), ensure_ascii=False, indent=2))
    print(f"report={out}")
    return 0 if summary.fail == 0 else 2


if __name__ == "__main__":
    raise SystemExit(main())
