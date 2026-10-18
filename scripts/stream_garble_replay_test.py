#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
流式输出防乱码 修复前/修复后 回放对比测试

对照实现（均来自本仓库真实代码）：
- OLD = 修复前（git 241dab3~1 / 2025-06-18 前）ChatApiController.streamChatDirect：
    每个 DataBuffer 直接 new String(bytes, UTF_8)（畸形字节 -> U+FFFD），
    chunk.split("\\n") 直接当完整行处理（无行缓冲），
    line.startsWith("data: ") 才解析，mapper.readTree 失败即 catch 丢弃（内容丢失），
    无任何字符清洗。
- NEW = 现行生产代码（src/main/java/com/instser/chat/service/stream/AiStreamProcessor.java）：
    解码后剔除 \\uFFFD\\u0000，lineBuffer 保留不完整行尾，jsonBuffer 跨行重组 +
    isValidJson 完整性校验 + 非 data 行续拼（1万字符上限）+ doOnComplete 末尾冲刷，
    consumeJson 内 isTextual() 校验。

测试方法：
1. 语料 = 生产环境 chat.instser.cn 真实 AI 回复（scripts 运行时采集的 SSE 帧文件）。
2. 将每条真实回复重建为 OpenAI 风格上游 SSE 字节流，再按随机字节边界分片
   （模拟文档记载的"上游返回内容被随机分片"），多种分片强度 × 多随机种子。
3. 同一份字节流分别喂给 OLD / NEW 解析器，统计：
   - 乱码字符数（U+FFFD 替换字符流出到前端）
   - 内容缺失字符数（difflib 对比应得文本）
   - JSON 帧解析失败/丢弃数
4. 汇总输出修复前后乱码率/丢失率对比。

用法：python3 scripts/stream_garble_replay_test.py --corpus-dir /tmp/garble_test --rounds 100
"""

import argparse
import difflib
import json
import os
import random
import re
import statistics
import sys
from dataclasses import dataclass, field


# ---------------------------------------------------------------- 语料加载

def load_corpus_frames(path):
    """解析生产采集的 SSE 文件，提取 0:"..." 帧文本，返回 (pieces, full_text)。"""
    pieces = []
    with open(path, 'r', encoding='utf-8') as f:
        for raw in f:
            line = raw.rstrip('\n')
            if line.startswith('0:'):
                try:
                    pieces.append(json.loads(line[2:]))
                except Exception:
                    pass
            elif line.startswith('d:'):
                break
    return pieces, ''.join(pieces)


def build_upstream_stream(pieces):
    """把真实回复重建为 OpenAI 风格上游 SSE 字节流（每 piece 一帧）。"""
    out = bytearray()
    for p in pieces:
        frame = {"id": "chatcmpl-replay", "object": "chat.completion.chunk",
                 "choices": [{"index": 0, "delta": {"content": p}, "finish_reason": None}]}
        out += ("data: " + json.dumps(frame, ensure_ascii=False) + "\n\n").encode('utf-8')
    out += b"data: [DONE]\n\n"
    return bytes(out)


# ---------------------------------------------------------------- 分片模型

def fragment(data: bytes, mode: str, rnd: random.Random):
    """按模式把字节流切成 DataBuffer 大小的块（可在任意字节边界，含多字节字符中间）。"""
    if mode == 'whole':          # 对照组：不分片
        return [data]
    chunks, i = [], 0
    while i < len(data):
        if mode == 'tiny':       # 极端：1~9 字节（高频切断多字节字符与行）
            n = rnd.randint(1, 9)
        elif mode == 'tcp':      # 典型 TCP：40~300 字节
            n = rnd.randint(40, 300)
        elif mode == 'mixed':    # 混合：70% 正常大块 + 30% 1~15 字节碎块
            n = rnd.randint(40, 300) if rnd.random() < 0.7 else rnd.randint(1, 15)
        else:
            raise ValueError(mode)
        chunks.append(data[i:i + n])
        i += n
    return chunks


# ---------------------------------------------------------------- OLD 解析器（修复前）

@dataclass
class ParseResult:
    text: str = ''
    mojibake: int = 0            # 流出到前端的 U+FFFD 数
    json_fail: int = 0           # JSON 解析失败被丢弃的帧数
    frames_ok: int = 0


def parse_old(chunks):
    """修复前逻辑（git 241dab3~1）。逐 chunk 解码 -> split('\\n') -> 直接解析 -> 失败丢弃。"""
    r = ParseResult()
    for buf in chunks:
        chunk = buf.decode('utf-8', errors='replace')      # new String(bytes, UTF_8)
        for line in chunk.split('\n'):                      # split("\n")，无行缓冲
            line = line.strip()
            if not line:
                continue
            if line.startswith('data: '):
                if line == 'data: [DONE]':
                    continue
                json_data = line[6:].strip()
                try:
                    node = json.loads(json_data)            # mapper.readTree 直接解析
                except Exception:
                    r.json_fail += 1                        # catch 丢弃：该帧内容丢失
                    continue
                try:
                    delta = node['choices'][0].get('delta') or {}
                    content = delta.get('content')
                    if content is None:
                        content = 'null'                    # Jackson NullNode.asText() == "null"
                    if content:
                        r.text += content
                        r.frames_ok += 1
                except Exception:
                    r.json_fail += 1
    r.mojibake = r.text.count('\uFFFD')
    return r


# ---------------------------------------------------------------- V1 解析器（241dab3，06-18 修复）

def parse_v1(chunks, strip=True):
    """06-18 修复版（git 241dab3）：清洗 \\uFFFD\\u0000 + jsonBuffer 重组，但无行缓冲。
    strip=False 时退化为 10:23 中间态（有 JSON 缓冲、无字符清洗）——即 12:16 报告里的"??"乱码态。"""
    r = ParseResult()
    json_buffer = ''

    def is_valid_json(s):
        s = s.strip()
        if not s:
            return False
        try:
            json.loads(s)
            return True
        except Exception:
            return False

    def consume(buffered):
        nonlocal r
        try:
            node = json.loads(buffered)
            choices = node.get('choices')
            if choices and isinstance(choices, list):
                delta = choices[0].get('delta') or {}
                if 'content' in delta:
                    content = delta['content']
                    if content is None:
                        content = 'null'                    # asText() 对 JSON null 的行为
                    if isinstance(content, str) and content:
                        r.text += content
                        r.frames_ok += 1
        except Exception:
            r.json_fail += 1

    for buf in chunks:
        chunk = buf.decode('utf-8', errors='replace')
        if strip:
            chunk = chunk.replace('\uFFFD', '').replace('\x00', '')
        for line in chunk.split('\n'):                      # 无行缓冲：chunk 边界切断行
            line = line.strip()
            if not line:
                continue
            if line.startswith('data: '):
                if line == 'data: [DONE]':
                    continue
                json_buffer += line[6:].strip()
                if is_valid_json(json_buffer):
                    consume(json_buffer)
                    json_buffer = ''
            elif json_buffer:
                json_buffer += line                         # 跨行续拼
                if is_valid_json(json_buffer):
                    consume(json_buffer)
                    json_buffer = ''
    r.mojibake = r.text.count('\uFFFD')
    return r


# ---------------------------------------------------------------- NEW 解析器（现行生产）

def parse_new(chunks):
    """现行 AiStreamProcessor 逻辑：行缓冲 + JSON 缓冲重组 + 清洗 + 末尾冲刷。"""
    r = ParseResult()
    json_buffer = ''
    line_buffer = ''

    def is_valid_json(s):
        s = s.strip()
        if not s:
            return False
        try:
            json.loads(s)
            return True
        except Exception:
            return False

    def consume(buffered):
        nonlocal r
        try:
            node = json.loads(buffered)
            choices = node.get('choices')
            if choices and isinstance(choices, list):
                delta = choices[0].get('delta') or {}
                content = delta.get('content')
                if isinstance(content, str) and content:    # isTextual() 校验
                    r.text += content
                    r.frames_ok += 1
        except Exception:
            r.json_fail += 1

    def handle_line(line):
        nonlocal json_buffer
        if line.startswith('data: '):
            if line == 'data: [DONE]':
                return
            json_buffer += line[6:].strip()
            if is_valid_json(json_buffer):
                consume(json_buffer)
                json_buffer = ''
        elif line.strip() and json_buffer:
            if len(json_buffer) > 10000:                    # 10000 字符上限保护
                json_buffer = ''
                return
            json_buffer += line.strip()
            if is_valid_json(json_buffer):
                consume(json_buffer)
                json_buffer = ''

    for buf in chunks:
        chunk = buf.decode('utf-8', errors='replace')
        chunk = chunk.replace('\uFFFD', '').replace('\x00', '')   # 清洗替换字符/NUL
        line_buffer += chunk
        lines = line_buffer.split('\n')                     # split("\n", -1)
        for l in lines[:-1]:
            l = l.strip()
            if l:
                handle_line(l)
        line_buffer = lines[-1]                             # 不完整行尾留缓冲

    # doOnComplete：冲刷行尾残留
    remaining = line_buffer.strip()
    if remaining.startswith('data: ') and remaining != 'data: [DONE]':
        json_buffer += remaining[6:].strip()
        if is_valid_json(json_buffer):
            consume(json_buffer)
    r.mojibake = r.text.count('\uFFFD')
    return r


# ---------------------------------------------------------------- 对比度量

def diff_stats(expected: str, got: str):
    sm = difflib.SequenceMatcher(a=expected, b=got, autojunk=False)
    matched = sum(m.size for m in sm.get_matching_blocks())
    missing = len(expected) - matched        # 应得但丢了的字符
    garbage = len(got) - matched             # 多出来的字符（乱码/null串等）
    return missing, garbage


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--corpus-dir', default=os.path.join(os.path.dirname(__file__), 'stream_garble_corpus'),
                    help='生产真实回复语料目录（resp_*.sse），默认 scripts/stream_garble_corpus')
    ap.add_argument('--rounds', type=int, default=100, help='每种分片模式的随机轮数')
    ap.add_argument('--modes', default='tiny,mixed,tcp,whole')
    args = ap.parse_args()

    import glob
    corpus_files = sorted(glob.glob(os.path.join(args.corpus_dir, 'resp_*.sse')))
    if not corpus_files:
        print('未找到语料文件', file=sys.stderr)
        sys.exit(1)

    corpora = []
    for p in corpus_files:
        pieces, full = load_corpus_frames(p)
        if full.strip():
            corpora.append((os.path.basename(p), build_upstream_stream(pieces), full))
    print(f'语料 {len(corpora)} 条：' + ', '.join(f'{n}({len(t)}字符)' for n, _, t in corpora))
    print(f'每种分片模式随机回放 {args.rounds} 轮/语料\n')

    modes = args.modes.split(',')
    parsers = [('v0', 'V0无任何缓冲'), ('v05', 'V0.5仅JSON缓冲'), ('v1', 'V1缓冲+清洗'), ('v2', 'V2现行+行缓冲')]
    fn = {'v0': parse_old, 'v05': lambda c: parse_v1(c, strip=False),
          'v1': parse_v1, 'v2': parse_new}
    # agg[mode][parser] = list of per-run dict
    agg = {m: {t: [] for t, _ in parsers} for m in modes}

    for name, stream, expected in corpora:
        for mode in modes:
            for seed in range(args.rounds):
                rnd = random.Random(hash((name, mode, seed)) & 0xFFFFFFFF)
                chunks = fragment(stream, mode, rnd)
                for tag, _label in parsers:
                    r = fn[tag](chunks)
                    missing, garbage = diff_stats(expected, r.text)
                    agg[mode][tag].append({
                        'exp': len(expected), 'got': len(r.text),
                        'mojibake': r.mojibake, 'json_fail': r.json_fail,
                        'missing': missing, 'garbage': garbage,
                        'frames_ok': r.frames_ok,
                    })

    hdr = f'{"分片模式":<8}{"解析器":<16}{"乱码率":>10}{"丢失率":>10}{"综合损坏率":>12}{"帧丢弃/万帧":>12}{"完好率":>10}'
    print(hdr)
    print('-' * len(hdr.expandtabs()) )
    summary = {}
    for mode in modes:
        for tag, label in parsers:
            runs = agg[mode][tag]
            exp_total = sum(x['exp'] for x in runs)
            moj = sum(x['mojibake'] for x in runs)
            mis = sum(x['missing'] for x in runs)
            gar = sum(x['garbage'] for x in runs)
            jf = sum(x['json_fail'] for x in runs)
            frames_ok_total = sum(x['frames_ok'] for x in runs)
            intact = sum(1 for x in runs if x['mojibake'] == 0 and x['missing'] == 0 and x['garbage'] == 0)
            moj_rate = moj / exp_total
            mis_rate = mis / exp_total
            gar_rate = (moj + gar + mis) / exp_total
            jf_per10k = jf / max(1, (jf + frames_ok_total)) * 10000
            intact_rate = intact / len(runs)
            summary[(mode, tag)] = (moj_rate, mis_rate, gar_rate, intact_rate)
            print(f"{mode:<8}{label:<16}{moj_rate:>10.4%}{mis_rate:>10.4%}{gar_rate:>12.4%}"
                  f"{jf_per10k:>12.1f}{intact_rate:>10.2%}")
        print()

    #  headline：mixed 模式（最接近真实网络）下的对比
    for mode in ('mixed', 'tiny'):
        if (mode, 'v0') in summary:
            o, n = summary[(mode, 'v0')], summary[(mode, 'v2')]
            print(f'[{mode}] 修复前(V0)综合损坏率 {o[2]:.2%}（乱码 {o[0]:.2%} + 丢失 {o[1]:.2%}），'
                  f'现行(V2) {n[2]:.2%}（乱码 {n[0]:.2%} + 丢失 {n[1]:.2%}）')


if __name__ == '__main__':
    main()
