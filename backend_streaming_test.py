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
        """模拟AI返回的流式数据块 - 保留换行符和缩进"""
        chunks = []

        # 🔧 修复：按字符分割而不是按单词，保留所有空白字符
        current_chunk = ""
        chunk_size = 0
        max_chunk_size = 30  # 每个块的最大字符数

        for i, char in enumerate(content):
            current_chunk += char
            chunk_size += 1

            # 当达到块大小限制、遇到换行符、或到达末尾时发送块
            if (chunk_size >= max_chunk_size or
                char == '\n' or
                i == len(content) - 1 or
                random.random() < 0.1):  # 随机发送，模拟AI的不规律性

                if current_chunk:  # 只发送非空块
                    # 创建OpenAI格式的JSON响应
                    json_response = {
                        "choices": [{
                            "delta": {
                                "content": current_chunk
                            }
                        }]
                    }

                    # 转换为SSE格式
                    sse_data = f"data: {json.dumps(json_response)}\n\n"

                    # 模拟网络传输可能的分块
                    chunk_bytes = sse_data.encode('utf-8')

                    # 随机分割字节块，模拟网络传输的不完整性
                    if len(chunk_bytes) > 50 and random.random() < 0.3:
                        split_point = random.randint(10, len(chunk_bytes) - 10)
                        chunks.append(chunk_bytes[:split_point])
                        chunks.append(chunk_bytes[split_point:])
                    else:
                        chunks.append(chunk_bytes)

                    current_chunk = ""
                    chunk_size = 0

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
        """模拟内容检测和过滤 - 修复版本"""
        # 简单的模拟，实际后端有复杂的过滤逻辑
        needs_retry = "error" in content.lower() or "failed" in content.lower()
        was_filtered = any(word in content.lower() for word in ["spam", "inappropriate"])

        if needs_retry or was_filtered:
            cleaned_content = ""
        else:
            # 基础清理：移除无效字符
            cleaned_content = content.replace('\uFFFD', '').replace('\u0000', '')

            # 🔧 修复：只清理行内的多余空格，保留行首的缩进空格
            # 原来的错误代码：cleaned = cleaned.replaceAll(" {2,}", " ");
            # 修复后的代码：只清理非行首的连续空格
            import re
            # 使用更简单的方法：只清理行中间的多余空格，不影响行首缩进
            lines = cleaned_content.split('\n')
            processed_lines = []
            for line in lines:
                # 保留行首空格，只清理行内的多余空格
                if line.strip():  # 非空行
                    # 找到第一个非空格字符的位置
                    leading_spaces = len(line) - len(line.lstrip(' '))
                    content_part = line[leading_spaces:]
                    # 只对内容部分清理多余空格
                    cleaned_content_part = re.sub(r' {2,}', ' ', content_part)
                    processed_lines.append(' ' * leading_spaces + cleaned_content_part)
                else:
                    processed_lines.append(line)  # 保留空行
            cleaned_content = '\n'.join(processed_lines)

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
    
    def process_complete_json(self, json_data: str) -> str:
        """模拟processCompleteJson方法"""
        try:
            json_node = json.loads(json_data)
            
            choices = json_node.get("choices")
            if choices and isinstance(choices, list) and len(choices) > 0:
                delta = choices[0].get("delta")
                if delta and "content" in delta:
                    content = delta["content"]
                    if content:
                        # 模拟内容检测
                        detection_result = self.detect_content_and_get_result(content)
                        
                        if detection_result.needs_retry:
                            raise Exception("检测到需要重试的错误消息")
                        
                        if detection_result.was_filtered:
                            print(f"AI响应内容被过滤: {repr(content[:50])}")
                            return None
                        
                        clean_content = detection_result.cleaned_content
                        if clean_content:
                            print(f"处理AI响应内容: {repr(clean_content[:50])}")
                            return clean_content
            
            return None
        except Exception as e:
            print(f"处理完整JSON数据失败: {e}")
            return None
    
    def process_line(self, line: str) -> str:
        """模拟处理单行数据"""
        if line.startswith("data: "):
            if line == "data: [DONE]":
                print("收到AI响应完成标记")
                return None
            
            # 提取JSON数据部分
            json_data = line[6:].strip()
            self.json_buffer += json_data
            
            # 尝试解析完整的JSON
            buffered_json = self.json_buffer
            if self.is_valid_json(buffered_json):
                try:
                    content_sent = self.process_complete_json(buffered_json)
                    if content_sent is not None:
                        self.ai_response_content += content_sent
                        self.json_buffer = ""  # 清空缓冲区
                        return content_sent
                    self.json_buffer = ""
                except Exception as e:
                    print(f"处理JSON失败: {e}")
                    self.json_buffer = ""
        
        elif self.json_buffer and not line.strip().startswith("data:"):
            # 处理跨行的JSON片段
            if len(self.json_buffer) > 10000:
                print("JSON缓冲区过大，清空重新开始")
                self.json_buffer = ""
                return None
            
            self.json_buffer += line.strip()
            
            # 再次尝试解析
            buffered_json = self.json_buffer
            if self.is_valid_json(buffered_json):
                try:
                    content_sent = self.process_complete_json(buffered_json)
                    if content_sent is not None:
                        self.ai_response_content += content_sent
                        self.json_buffer = ""
                        return content_sent
                    self.json_buffer = ""
                except Exception as e:
                    print(f"处理跨行JSON失败: {e}")
                    self.json_buffer = ""
        
        return None
    
    def get_optimal_chunk_size(self, content: str) -> int:
        """计算最优分块大小"""
        length = len(content)

        if length <= 20:
            return length
        elif length <= 100:
            return max(self.MIN_CHUNK_SIZE, min(length // 4, 10))
        elif length <= 300:
            return max(10, min(length // 8, 15))
        else:
            return max(15, min(self.MAX_CHUNK_SIZE, length // 12))

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

                        print(f"发送分块 [{len(sent_chunks)}]: {repr(chunk_content[:30])}... (长度: {len(chunk_content)})")

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

    def process_ai_stream(self, ai_chunks: List[bytes]) -> str:
        """处理AI流式数据的完整流程"""
        print("=== 开始处理AI流式数据 ===")

        accumulated_content = ""

        for i, chunk_bytes in enumerate(ai_chunks):
            print(f"\n--- 处理数据块 {i+1}/{len(ai_chunks)} ---")
            print(f"原始字节数据: {chunk_bytes[:100]}...")

            # 1. 处理DataBuffer
            chunk_str = self.process_data_buffer(chunk_bytes)
            print(f"解码后字符串: {repr(chunk_str[:100])}")

            # 2. 分行处理
            lines = self.process_chunk_to_lines(chunk_str)
            print(f"提取到 {len(lines)} 行完整数据")

            # 3. 处理每一行
            for line in lines:
                content = self.process_line(line)
                if content:
                    accumulated_content += content
                    print(f"累积内容长度: {len(accumulated_content)}")

        # 处理缓冲区中剩余的数据
        if self.line_buffer.strip():
            print(f"处理缓冲区剩余数据: {repr(self.line_buffer[:50])}")
            remaining_lines = [self.line_buffer.strip()]
            for line in remaining_lines:
                content = self.process_line(line)
                if content:
                    accumulated_content += content

        print(f"\n=== AI流式数据处理完成 ===")
        print(f"最终累积内容长度: {len(accumulated_content)}")

        return accumulated_content

def load_test_html() -> str:
    """加载测试HTML文件"""
    try:
        with open('page1.html', 'r', encoding='utf-8') as f:
            content = f.read()
        print(f"成功加载 page1.html，长度: {len(content)}")
        return content
    except FileNotFoundError:
        print("未找到 page1.html 文件")
        return ""

def test_complete_backend_flow():
    """测试完整的后端流程"""
    print("=== 完整后端流式传输测试 ===\n")

    # 1. 加载测试HTML内容
    original_html = load_test_html()
    if not original_html:
        return

    print(f"原始HTML长度: {len(original_html)}")

    # 2. 模拟AI返回流式数据
    print("\n--- 步骤1: 模拟AI流式响应 ---")
    ai_simulator = AIResponseSimulator()
    ai_chunks = ai_simulator.simulate_ai_stream_response(original_html)
    print(f"AI模拟生成了 {len(ai_chunks)} 个数据块")

    # 3. 后端处理AI流式数据
    print("\n--- 步骤2: 后端处理AI流式数据 ---")
    backend_processor = BackendStreamProcessor()
    processed_content = backend_processor.process_ai_stream(ai_chunks)

    # 4. 后端发送给前端
    print("\n--- 步骤3: 后端发送给前端 ---")
    frontend_chunks = backend_processor.send_content_by_smart_chunking(processed_content)

    # 5. 前端重构内容
    print("\n--- 步骤4: 前端重构内容 ---")
    reconstructed_content = ""
    for chunk in frontend_chunks:
        if chunk.startswith('0:'):
            json_part = chunk[2:].strip()
            try:
                content = json.loads(json_part)
                reconstructed_content += content
            except json.JSONDecodeError as e:
                print(f"前端JSON解析错误: {e}")

    # 6. 对比结果
    print("\n=== 结果对比 ===")
    print(f"原始HTML长度: {len(original_html)}")
    print(f"AI处理后长度: {len(processed_content)}")
    print(f"最终重构长度: {len(reconstructed_content)}")

    if original_html == processed_content:
        print("✅ AI处理保持了原始格式")
    else:
        print("❌ AI处理改变了原始格式")
        print(f"长度差异: {len(original_html) - len(processed_content)}")

    if processed_content == reconstructed_content:
        print("✅ 后端流式传输保持了格式")
    else:
        print("❌ 后端流式传输改变了格式")
        print(f"长度差异: {len(processed_content) - len(reconstructed_content)}")

    if original_html == reconstructed_content:
        print("✅ 整个流程完美保持了原始格式")
    else:
        print("❌ 整个流程改变了原始格式")

        # 保存结果用于对比
        with open('ai_processed.html', 'w', encoding='utf-8') as f:
            f.write(processed_content)
        with open('final_reconstructed.html', 'w', encoding='utf-8') as f:
            f.write(reconstructed_content)
        print("已保存 ai_processed.html 和 final_reconstructed.html 用于对比")

if __name__ == "__main__":
    test_complete_backend_flow()
