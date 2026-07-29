#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
短请求并发：登录 + GET /chat/models + GET /chat/sessions
用于测鉴权/Tomcat/DB，不消耗上游 AI 配额。
"""
from __future__ import annotations

import argparse
import json
import statistics
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from http.cookiejar import CookieJar
from pathlib import Path
from urllib.request import Request, build_opener, HTTPCookieProcessor

OUT = Path("logs/load")
OUT.mkdir(parents=True, exist_ok=True)


def one(base: str, user: str, password: str, i: int):
    t0 = time.time()
    cj = CookieJar()
    op = build_opener(HTTPCookieProcessor(cj))
    try:
        req = Request(
            base + "/auth/login",
            data=json.dumps({"usernameOrEmail": user, "password": password}).encode(),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with op.open(req, timeout=20) as r:
            j = json.loads(r.read().decode())
            if j.get("code") != 200:
                return {"i": i, "ok": False, "ms": int((time.time() - t0) * 1000), "err": "login"}
        for path in ("/chat/models", "/chat/sessions"):
            req = Request(base + path, headers={"Accept": "application/json"})
            with op.open(req, timeout=20) as r:
                j = json.loads(r.read().decode())
                if j.get("code") != 200:
                    return {"i": i, "ok": False, "ms": int((time.time() - t0) * 1000), "err": path}
        return {"i": i, "ok": True, "ms": int((time.time() - t0) * 1000), "err": ""}
    except Exception as e:
        return {"i": i, "ok": False, "ms": int((time.time() - t0) * 1000), "err": str(e)[:120]}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://127.0.0.1:8080/api")
    ap.add_argument("--user", default="testuser")
    ap.add_argument("--password", default="123456")
    ap.add_argument("--concurrency", type=int, default=50)
    ap.add_argument("--total", type=int, default=100)
    args = ap.parse_args()

    t0 = time.time()
    results = []
    with ThreadPoolExecutor(max_workers=args.concurrency) as ex:
        futs = [ex.submit(one, args.base, args.user, args.password, i) for i in range(args.total)]
        for f in as_completed(futs):
            results.append(f.result())
    wall = time.time() - t0
    ok = [r for r in results if r["ok"]]
    fail = [r for r in results if not r["ok"]]
    ms = [r["ms"] for r in ok]
    summary = {
        "concurrency": args.concurrency,
        "total": len(results),
        "success": len(ok),
        "fail": len(fail),
        "success_rate": round(len(ok) / max(1, len(results)), 4),
        "avg_ms": round(statistics.mean(ms), 1) if ms else None,
        "p95_ms": round(sorted(ms)[int(0.95 * (len(ms) - 1))], 1) if ms else None,
        "rps": round(len(results) / wall, 2),
        "errors": {},
    }
    for r in fail:
        summary["errors"][r["err"] or "unknown"] = summary["errors"].get(r["err"] or "unknown", 0) + 1
    out = OUT / f"api_concurrent_{int(time.time())}.json"
    out.write_text(json.dumps({"summary": summary, "results": results}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print("report=", out)
    return 0 if not fail else 2


if __name__ == "__main__":
    raise SystemExit(main())
