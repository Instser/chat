#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
多账号流式并发 / 最大并发探测（适合「最大同时聊天」压测）。

产品约束：同一 userId 同时只能 1 路活跃聊天。
因此测最大并发时必须：账号数 >= 并发数（1 账号 1 路）。

账号文件格式见 load_accounts.example.txt：
  user pass
  user:pass
  user,pass

示例：
  # 固定并发 20（需要 >=20 个账号）
  python scripts/load_concurrent_stream_multi.py \\
    --accounts scripts/my_accounts.txt \\
    --concurrency 20 --total 20 --max-wait 30

  # 自动探测最大并发（从 1 爬到 --max-probe）
  python scripts/load_concurrent_stream_multi.py \\
    --accounts scripts/my_accounts.txt \\
    --find-max --max-probe 50 --success-rate 0.9

  # 零 token：后端打开 mock-upstream + 本地 mock 服务
"""
from __future__ import annotations

import argparse
import json
import statistics
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import asdict, dataclass, field
from http.cookiejar import CookieJar
from pathlib import Path
from typing import Dict, List, Optional, Tuple
from urllib.error import HTTPError, URLError
from urllib.request import HTTPCookieProcessor, Request, build_opener

OUT = Path("logs/load")
OUT.mkdir(parents=True, exist_ok=True)


@dataclass
class Account:
    username: str
    password: str


@dataclass
class StreamResult:
    worker: int
    account: str
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


def load_accounts(path: str) -> List[Account]:
    p = Path(path)
    if not p.exists():
        raise FileNotFoundError(f"accounts file not found: {path}")
    accounts: List[Account] = []
    for line_no, raw in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if ":" in line and "," not in line and " " not in line.split(":", 1)[0]:
            # user:pass （密码里可能还有冒号）
            u, pw = line.split(":", 1)
        elif "," in line:
            u, pw = line.split(",", 1)
        else:
            parts = line.split()
            if len(parts) < 2:
                raise ValueError(f"bad account line {line_no}: {raw!r}")
            u, pw = parts[0], parts[1]
        u, pw = u.strip(), pw.strip()
        if not u or not pw:
            raise ValueError(f"empty user/pass line {line_no}")
        accounts.append(Account(u, pw))
    if not accounts:
        raise ValueError(f"no accounts in {path}")
    return accounts


def login(base: str, user: str, password: str):
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
        j = json.loads(resp.read().decode("utf-8", errors="replace"))
        if j.get("code") != 200:
            raise RuntimeError(f"login failed user={user}: {j.get('message')}")
    if not any(c.name == "auth-token" for c in cj):
        raise RuntimeError(f"login ok but no cookie user={user}")
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
    account: str,
    model_id: int,
    prompt: str,
    max_wait_s: float,
) -> StreamResult:
    t0 = time.time()
    try:
        sid = create_session(op, base)
    except Exception as e:
        return StreamResult(
            worker=worker,
            account=account,
            ok=False,
            total_ms=int((time.time() - t0) * 1000),
            error=f"session:{e}",
        )

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
        headers={"Content-Type": "application/json", "Accept": "text/event-stream"},
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
        err = f"http:{e.code}"
    except URLError as e:
        err = f"url:{e}"
    except Exception as e:
        err = f"ex:{e}"

    full = "".join(parts)
    if "Currently chatting" in full:
        err = err or "product_limit:active_session"
    if "frequency is too high" in full.lower() or "too high" in full:
        err = err or "rate_limit"
    total_ms = int((time.time() - t0) * 1000)
    ttfb = int((first - t0) * 1000) if first else None
    ok = http == 200 and zero > 0 and d >= 1 and not err
    return StreamResult(
        worker=worker,
        account=account,
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


def run_batch(
    base: str,
    accounts: List[Account],
    concurrency: int,
    total: int,
    model_id: int,
    prompt: str,
    max_wait: float,
    quiet: bool = False,
) -> Dict:
    if concurrency > len(accounts):
        print(
            f"[WARN] concurrency={concurrency} > accounts={len(accounts)}；"
            f"多余 worker 会复用账号并触发 product_limit"
        )
    # total 可 > concurrency：分波次，每波 concurrency 路；波内尽量 1 账号 1 路
    results: List[StreamResult] = []
    lock = threading.Lock()
    t_wall0 = time.time()

    # 分波：每波最多 concurrency 路，且尽量每路不同账号
    remaining = total
    wave = 0
    idx = 0
    while remaining > 0:
        batch = min(concurrency, remaining)
        # 本波账号：从 accounts 轮转取 batch 个不重复（若账号够）
        wave_accounts = []
        for j in range(batch):
            wave_accounts.append(accounts[(wave * concurrency + j) % len(accounts)])

        def one_wave(local_i: int, acc: Account, global_i: int) -> StreamResult:
            try:
                op = login(base, acc.username, acc.password)
            except Exception as e:
                return StreamResult(worker=global_i, account=acc.username, ok=False, error=f"login:{e}")
            r = stream_chat(op, base, global_i, acc.username, model_id, prompt, max_wait)
            if not quiet:
                with lock:
                    st = "OK" if r.ok else "FAIL"
                    print(
                        f"[{st}] wave={wave} w={global_i} user={r.account} http={r.http} "
                        f"ttfb={r.ttfb_ms} total={r.total_ms} 0={r.zero_frames} d={r.d_frames} "
                        f"err={r.error or '-'} sample={r.sample!r}"
                    )
            return r

        with ThreadPoolExecutor(max_workers=batch) as ex:
            futs = [
                ex.submit(one_wave, j, wave_accounts[j], idx + j) for j in range(batch)
            ]
            for fut in as_completed(futs):
                results.append(fut.result())
        remaining -= batch
        idx += batch
        wave += 1
        # 波次之间稍等，避免上一波活跃会话未清完
        if remaining > 0:
            time.sleep(0.5)

    wall = time.time() - t_wall0
    ok_list = [r for r in results if r.ok]
    fail_list = [r for r in results if not r.ok]
    ttfbs = [float(r.ttfb_ms) for r in ok_list if r.ttfb_ms is not None]
    totals = [float(r.total_ms) for r in ok_list]
    err_count: Dict[str, int] = {}
    for r in fail_list:
        key = (r.error or f"http{r.http}").split(":")[0]
        err_count[key] = err_count.get(key, 0) + 1

    summary = {
        "base": base,
        "concurrency": concurrency,
        "total": len(results),
        "accounts": len(accounts),
        "success": len(ok_list),
        "fail": len(fail_list),
        "success_rate": round(len(ok_list) / max(1, len(results)), 4),
        "ttfb_p50_ms": round(pct(ttfbs, 0.50), 1) if ttfbs else None,
        "ttfb_p95_ms": round(pct(ttfbs, 0.95), 1) if ttfbs else None,
        "ttfb_avg_ms": round(statistics.mean(ttfbs), 1) if ttfbs else None,
        "total_p50_ms": round(pct(totals, 0.50), 1) if totals else None,
        "total_p95_ms": round(pct(totals, 0.95), 1) if totals else None,
        "total_avg_ms": round(statistics.mean(totals), 1) if totals else None,
        "wall_s": round(wall, 3),
        "rps": round(len(results) / wall, 3) if wall > 0 else 0.0,
        "errors": err_count,
    }
    return {"summary": summary, "results": [asdict(r) for r in sorted(results, key=lambda x: x.worker)]}


def find_max(
    base: str,
    accounts: List[Account],
    max_probe: int,
    success_rate: float,
    model_id: int,
    prompt: str,
    max_wait: float,
) -> Dict:
    """从 1 递增探测最大同时成功并发（受账号数上限）。"""
    hard_cap = min(max_probe, len(accounts))
    print(f"find-max: hard_cap={hard_cap} (min of max_probe={max_probe}, accounts={len(accounts)})")
    history = []
    best = 0
    # 先指数增长再细化
    c = 1
    last_ok = 0
    while c <= hard_cap:
        print(f"\n----- probe concurrency={c} -----")
        rep = run_batch(base, accounts, c, c, model_id, prompt, max_wait, quiet=False)
        s = rep["summary"]
        history.append(s)
        print(json.dumps(s, ensure_ascii=False, indent=2))
        if s["success_rate"] >= success_rate:
            best = c
            last_ok = c
            # 翻倍
            nxt = c * 2
            if nxt > hard_cap and c < hard_cap:
                c = hard_cap
            elif nxt <= hard_cap:
                c = nxt
            else:
                break
        else:
            # 在 (last_ok, c) 二分
            lo, hi = last_ok, c
            while lo + 1 < hi:
                mid = (lo + hi) // 2
                print(f"\n----- binary probe concurrency={mid} -----")
                rep2 = run_batch(base, accounts, mid, mid, model_id, prompt, max_wait, quiet=False)
                s2 = rep2["summary"]
                history.append(s2)
                print(json.dumps(s2, ensure_ascii=False, indent=2))
                if s2["success_rate"] >= success_rate:
                    lo = mid
                    best = mid
                else:
                    hi = mid
            best = lo
            break
        # 波次间冷却，清活跃会话
        time.sleep(1.0)

    return {
        "best_concurrency": best,
        "hard_cap": hard_cap,
        "success_rate_threshold": success_rate,
        "history": history,
    }


def main() -> int:
    ap = argparse.ArgumentParser(description="Multi-account concurrent stream / max concurrency probe")
    ap.add_argument("--base", default="http://127.0.0.1:8080/api")
    ap.add_argument("--accounts", required=True, help="账号文件路径")
    ap.add_argument("--concurrency", type=int, default=5, help="同时在途流数量")
    ap.add_argument("--total", type=int, default=0, help="总请求数，0=等于 concurrency")
    ap.add_argument("--model-id", type=int, default=1)
    ap.add_argument("--max-wait", type=float, default=60.0)
    ap.add_argument("--prompt", default="ping")
    ap.add_argument("--find-max", action="store_true", help="自动探测最大并发")
    ap.add_argument("--max-probe", type=int, default=50, help="探测上限")
    ap.add_argument("--success-rate", type=float, default=0.9, help="判定通过的最低成功率")
    args = ap.parse_args()

    accounts = load_accounts(args.accounts)
    print(f"loaded {len(accounts)} accounts from {args.accounts}")
    print(f"base={args.base}")

    if args.find_max:
        probe = find_max(
            args.base,
            accounts,
            args.max_probe,
            args.success_rate,
            args.model_id,
            args.prompt,
            args.max_wait,
        )
        out = OUT / f"max_concurrent_{int(time.time())}.json"
        out.write_text(json.dumps(probe, ensure_ascii=False, indent=2), encoding="utf-8")
        print("\n==== FIND-MAX RESULT ====")
        print(json.dumps({k: probe[k] for k in ("best_concurrency", "hard_cap", "success_rate_threshold")}, indent=2))
        print(f"report={out}")
        return 0

    total = args.total if args.total > 0 else args.concurrency
    rep = run_batch(
        args.base,
        accounts,
        args.concurrency,
        total,
        args.model_id,
        args.prompt,
        args.max_wait,
    )
    out = OUT / f"multi_concurrent_{int(time.time())}.json"
    out.write_text(json.dumps(rep, ensure_ascii=False, indent=2), encoding="utf-8")
    print("\n==== SUMMARY ====")
    print(json.dumps(rep["summary"], ensure_ascii=False, indent=2))
    print(f"report={out}")
    return 0 if rep["summary"]["fail"] == 0 else 2


if __name__ == "__main__":
    raise SystemExit(main())
