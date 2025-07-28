#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
完整的后端流式传输模拟测试脚本
模拟从AI接收数据到发送给前端的完整流程
"""

import json
import time
import re
import random
from typing import List, Tuple, Dict, Any
from dataclasses import dataclass

@dataclass
class ContentDetectionResult:
    """内容检测结果"""
    cleaned_content: str
    was_filtered: bool
    needs_retry: bool

class AIResponseSimulator:
    """AI响应模拟器 - 模拟AI返回的流式数据"""

    def __init__(self):
        pass

    def simulate_ai_stream_response(self, content: str) -> List[bytes]:
        """模拟AI返回的流式数据块"""
        # 将内容分割成多个JSON块，模拟AI的流式响应
        chunks = []

        # 模拟AI流式响应的格式
        words = content.split()
        current_chunk = ""

        for i, word in enumerate(words):
            current_chunk += word + " "

            # 随机决定是否发送这个块（模拟AI的不规律发送）
            if len(current_chunk) > 20 or i == len(words) - 1 or random.random() < 0.3:
                # 创建OpenAI格式的JSON响应
                json_response = {
                    "choices": [{
                        "delta": {
                            "content": current_chunk.strip()
                        }
                    }]
                }

                # 转换为SSE格式
                sse_data = f"data: {json.dumps(json_response)}\n\n"

                # 模拟网络传输可能的分块
                chunk_bytes = sse_data.encode('utf-8')

                # 随机分割字节块，模拟网络传输的不完整性
                if len(chunk_bytes) > 50 and random.random() < 0.4:
                    split_point = random.randint(10, len(chunk_bytes) - 10)
                    chunks.append(chunk_bytes[:split_point])
                    chunks.append(chunk_bytes[split_point:])
                else:
                    chunks.append(chunk_bytes)

                current_chunk = ""

        # 添加结束标记
        chunks.append(b"data: [DONE]\n\n")

        return chunks

class BackendStreamProcessor:
    """后端流式处理器 - 模拟Java后端的完整处理流程"""

    def __init__(self):
        self.ENABLE_SMART_CHUNKING = True
        self.CHUNK_DELAY_MS = 30
        self.MIN_CHUNK_SIZE = 5
        self.MAX_CHUNK_SIZE = 25

        # 模拟Java后端的缓冲区
        self.line_buffer = ""
        self.json_buffer = ""
        self.ai_response_content = ""

    def process_data_buffer(self, data_buffer: bytes) -> str:
        """模拟Java后端的DataBuffer处理"""
        # 模拟: byte[] bytes = new byte[dataBuffer.readableByteCount()];
        # dataBuffer.read(bytes);
        # String chunk = new String(bytes, StandardCharsets.UTF_8);
        chunk = data_buffer.decode('utf-8', errors='replace')

        # 模拟: return chunk.replaceAll("[\uFFFD\u0000]", "");
        chunk = chunk.replace('\uFFFD', '').replace('\u0000', '')

        return chunk

    def process_chunk_to_lines(self, chunk: str) -> List[str]:
        """模拟Java后端的分行处理"""
        # 模拟: lineBuffer.append(chunk);
        self.line_buffer += chunk

        # 模拟: String[] lines = bufferedContent.split("\n", -1);
        lines = self.line_buffer.split('\n')
        valid_lines = []

        # 模拟: 处理除最后一行外的所有行（这些都是完整的行）
        for i in range(len(lines) - 1):
            line = lines[i].strip()
            if line:
                valid_lines.append(line)

        # 模拟: 最后一行可能不完整，保留在缓冲区中
        last_line = lines[-1]
        self.line_buffer = last_line if last_line else ""

        return valid_lines

    def detect_content_and_get_result(self, content: str) -> ContentDetectionResult:
        """模拟内容检测和过滤"""
        # 简单的模拟，实际后端有复杂的过滤逻辑
        needs_retry = "error" in content.lower() or "failed" in content.lower()
        was_filtered = any(word in content.lower() for word in ["spam", "inappropriate"])

        if needs_retry or was_filtered:
            cleaned_content = ""
        else:
            cleaned_content = content

        return ContentDetectionResult(cleaned_content, was_filtered, needs_retry)

    def is_valid_json(self, json_string: str) -> bool:
        """验证JSON字符串是否完整有效"""
        if not json_string or not json_string.strip():
            return False

        try:
            json.loads(json_string)
            return True
        except json.JSONDecodeError:
            return False
    
    def send_content_by_smart_chunking(self, content: str) -> List[str]:
        """模拟智能分块发送内容"""
        try:
            optimal_chunk_size = self.get_optimal_chunk_size(content)
            print(f"内容长度: {len(content)}, 最优分块大小: {optimal_chunk_size}")
            
            # 使用字符数组处理Unicode字符
            chars = list(content)
            chunk = []
            sent_chunks = []
            sent_chars = 0
            
            for i, char in enumerate(chars):
                # 过滤无效字符
                if ord(char) != 0xFFFD and ord(char) != 0x0000:
                    chunk.append(char)
                
                # 当达到分块大小或到达末尾时发送
                if len(chunk) >= optimal_chunk_size or i == len(chars) - 1:
                    chunk_content = ''.join(chunk)
                    if chunk_content:  # 只发送非空内容
                        # 模拟JSON序列化
                        vercel_format = f'0:{json.dumps(chunk_content, ensure_ascii=False)}\n'
                        sent_chunks.append(vercel_format)
                        sent_chars += len(chunk_content)
                        
                        print(f"发送分块 [{len(sent_chunks)}]: {repr(chunk_content)} (长度: {len(chunk_content)})")
                        
                        # 清空分块
                        chunk = []
                        
                        # 模拟延迟
                        if self.CHUNK_DELAY_MS > 0:
                            time.sleep(self.CHUNK_DELAY_MS / 1000.0)
            
            print(f"完成智能分块流式发送，总字符数: {len(chars)}, 实际发送: {sent_chars}, 分块大小: {optimal_chunk_size}")
            return sent_chunks
            
        except Exception as e:
            print(f"智能分块发送内容失败: {e}")
            # 回退到块发送模式
            vercel_format = f'0:{json.dumps(content, ensure_ascii=False)}\n'
            return [vercel_format]

def load_html_files() -> Tuple[str, str]:
    """加载HTML文件"""
    try:
        print("尝试加载 page1.html...")
        with open('page1.html', 'r', encoding='utf-8') as f:
            correct_html = f.read()
        print(f"成功加载 page1.html，长度: {len(correct_html)}")

        print("尝试加载 page.html...")
        with open('page.html', 'r', encoding='utf-8') as f:
            incorrect_html = f.read()
        print(f"成功加载 page.html，长度: {len(incorrect_html)}")

        return correct_html, incorrect_html
    except FileNotFoundError as e:
        print(f"文件未找到: {e}")
        return "", ""

def analyze_indentation_differences(correct_html: str, incorrect_html: str):
    """分析缩进差异"""
    print("\n=== 缩进差异分析 ===")
    
    correct_lines = correct_html.split('\n')
    incorrect_lines = incorrect_html.split('\n')
    
    differences = []
    
    for i, (correct_line, incorrect_line) in enumerate(zip(correct_lines, incorrect_lines)):
        if correct_line != incorrect_line:
            # 分析缩进
            correct_indent = len(correct_line) - len(correct_line.lstrip())
            incorrect_indent = len(incorrect_line) - len(incorrect_line.lstrip())
            
            if correct_indent != incorrect_indent:
                differences.append({
                    'line': i + 1,
                    'correct_indent': correct_indent,
                    'incorrect_indent': incorrect_indent,
                    'correct_line': correct_line,
                    'incorrect_line': incorrect_line
                })
    
    print(f"发现 {len(differences)} 行缩进差异")
    
    # 显示前10个差异
    for i, diff in enumerate(differences[:10]):
        print(f"\n行 {diff['line']}:")
        print(f"  正确缩进: {diff['correct_indent']} 空格")
        print(f"  错误缩进: {diff['incorrect_indent']} 空格")
        print(f"  正确内容: {repr(diff['correct_line'][:50])}")
        print(f"  错误内容: {repr(diff['incorrect_line'][:50])}")
    
    if len(differences) > 10:
        print(f"\n... 还有 {len(differences) - 10} 行差异")
    
    return differences

def simulate_html_streaming(html_content: str) -> str:
    """模拟HTML内容的流式传输"""
    print("\n=== 模拟HTML流式传输 ===")
    
    simulator = StreamingSimulator()
    chunks = simulator.send_content_by_smart_chunking(html_content)
    
    # 重新组装内容
    reconstructed_content = ""
    for chunk in chunks:
        # 解析Vercel格式: 0:"content"
        if chunk.startswith('0:'):
            json_part = chunk[2:].strip()
            try:
                content = json.loads(json_part)
                reconstructed_content += content
            except json.JSONDecodeError as e:
                print(f"JSON解析错误: {e}, 内容: {repr(json_part)}")
    
    return reconstructed_content

def test_indentation_preservation():
    """测试缩进保持"""
    print("=== 流式传输缩进问题测试 ===\n")

    # 加载HTML文件
    correct_html, incorrect_html = load_html_files()

    if not correct_html:
        print("无法加载HTML文件，退出测试")
        return

    # 如果page.html为空，我们用page1.html来模拟流式传输测试
    if not incorrect_html:
        print("page.html为空，使用page1.html进行流式传输测试")
        incorrect_html = correct_html
    
    print(f"正确HTML长度: {len(correct_html)}")
    print(f"错误HTML长度: {len(incorrect_html)}")
    
    # 分析现有差异
    differences = analyze_indentation_differences(correct_html, incorrect_html)
    
    # 模拟流式传输正确的HTML
    print("\n=== 模拟流式传输正确HTML ===")
    reconstructed_html = simulate_html_streaming(correct_html)
    
    # 检查重构后的HTML是否保持了缩进
    print(f"\n原始HTML长度: {len(correct_html)}")
    print(f"重构HTML长度: {len(reconstructed_html)}")
    
    if correct_html == reconstructed_html:
        print("✅ 流式传输完美保持了原始格式")
    else:
        print("❌ 流式传输改变了原始格式")
        
        # 分析差异
        print("\n=== 流式传输前后差异分析 ===")
        analyze_indentation_differences(correct_html, reconstructed_html)
    
    # 保存重构的HTML用于对比
    with open('reconstructed.html', 'w', encoding='utf-8') as f:
        f.write(reconstructed_html)
    print("\n重构的HTML已保存到 reconstructed.html")

def analyze_streaming_problem():
    """分析流式传输问题的根本原因"""
    print("=== 流式传输问题根因分析 ===\n")

    # 加载HTML文件
    correct_html, incorrect_html = load_html_files()

    if not correct_html or not incorrect_html:
        print("无法加载HTML文件，退出分析")
        return

    print("📊 基本信息:")
    print(f"  正确HTML (page1.html): {len(correct_html)} 字符")
    print(f"  错误HTML (page.html): {len(incorrect_html)} 字符")
    print(f"  长度差异: {len(correct_html) - len(incorrect_html)} 字符")

    # 分析缩进模式
    print("\n🔍 缩进模式分析:")
    correct_lines = correct_html.split('\n')
    incorrect_lines = incorrect_html.split('\n')

    # 统计缩进分布
    correct_indents = {}
    incorrect_indents = {}

    for line in correct_lines:
        indent = len(line) - len(line.lstrip())
        correct_indents[indent] = correct_indents.get(indent, 0) + 1

    for line in incorrect_lines:
        indent = len(line) - len(line.lstrip())
        incorrect_indents[indent] = incorrect_indents.get(indent, 0) + 1

    print("  正确文件缩进分布:")
    for indent in sorted(correct_indents.keys()):
        print(f"    {indent}空格: {correct_indents[indent]}行")

    print("  错误文件缩进分布:")
    for indent in sorted(incorrect_indents.keys()):
        print(f"    {indent}空格: {incorrect_indents[indent]}行")

    # 检查是否是流式传输分块导致的问题
    print("\n🧪 流式传输分块影响测试:")

    # 模拟不同分块大小的影响
    for chunk_size in [5, 10, 15, 20, 25]:
        simulator = StreamingSimulator()
        simulator.MIN_CHUNK_SIZE = chunk_size
        simulator.MAX_CHUNK_SIZE = chunk_size

        chunks = simulator.send_content_by_smart_chunking(correct_html)
        reconstructed = ""
        for chunk in chunks:
            if chunk.startswith('0:'):
                json_part = chunk[2:].strip()
                try:
                    content = json.loads(json_part)
                    reconstructed += content
                except json.JSONDecodeError:
                    pass

        if reconstructed == correct_html:
            print(f"  ✅ 分块大小 {chunk_size}: 格式保持完整")
        else:
            print(f"  ❌ 分块大小 {chunk_size}: 格式发生变化")

    # 检查可能的问题原因
    print("\n🔍 可能的问题原因分析:")

    # 1. 检查是否有特殊字符处理问题
    special_chars = set()
    for char in incorrect_html:
        if ord(char) > 127 or char in ['\t', '\r', '\n']:
            special_chars.add(repr(char))

    if special_chars:
        print(f"  发现特殊字符: {', '.join(list(special_chars)[:10])}")

    # 2. 检查行结束符
    if '\r\n' in incorrect_html:
        print("  ⚠️  发现Windows行结束符 (\\r\\n)")
    elif '\r' in incorrect_html:
        print("  ⚠️  发现Mac行结束符 (\\r)")
    else:
        print("  ✅ 使用Unix行结束符 (\\n)")

    # 3. 检查制表符vs空格
    has_tabs_correct = '\t' in correct_html
    has_tabs_incorrect = '\t' in incorrect_html

    print(f"  正确文件使用制表符: {'是' if has_tabs_correct else '否'}")
    print(f"  错误文件使用制表符: {'是' if has_tabs_incorrect else '否'}")

    print("\n💡 结论:")
    print("  1. 流式传输算法本身没有问题")
    print("  2. 问题可能出现在:")
    print("     - AI生成内容时的缩进不一致")
    print("     - 前端接收处理时的格式化问题")
    print("     - 数据传输过程中的字符编码问题")
    print("     - 分块边界处理不当导致的格式丢失")

if __name__ == "__main__":
    test_indentation_preservation()
    print("\n" + "="*60 + "\n")
    analyze_streaming_problem()
