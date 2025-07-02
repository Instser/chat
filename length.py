import re
import json
from typing import Dict, List, Tuple
from dataclasses import dataclass
from enum import Enum

class TruncationReason(Enum):
    NOT_TRUNCATED = "not_truncated"
    INCOMPLETE_SYNTAX = "incomplete_syntax"
    INCOMPLETE_SENTENCE = "incomplete_sentence"
    ABRUPT_ENDING = "abrupt_ending"
    INCOMPLETE_CODE_BLOCK = "incomplete_code_block"
    INCOMPLETE_HTML_TAG = "incomplete_html_tag"
    INCOMPLETE_JSON = "incomplete_json"
    LENGTH_LIMIT = "length_limit"

@dataclass
class TruncationResult:
    is_truncated: bool
    confidence: float  # 0.0 - 1.0
    reason: TruncationReason
    details: str
    suggested_continuation: str = ""

class AdvancedTruncationDetector:
    def __init__(self):
        # 完整句子结束标点
        self.sentence_endings = {'.', '!', '?', '。', '！', '？', '…'}
        
        # 代码块标识符
        self.code_block_patterns = [
            r'```[\w]*\n',  # Markdown代码块开始
            r'```$',        # Markdown代码块结束
            r'<script[^>]*>',  # HTML script标签
            r'</script>',      # HTML script结束标签
            r'<style[^>]*>',   # HTML style标签
            r'</style>',       # HTML style结束标签
        ]
        
        # HTML标签模式
        self.html_tag_pattern = r'<[^>]*$'  # 未闭合的HTML标签
        
        # JSON模式
        self.json_patterns = {
            'open_brace': r'\{[^}]*$',
            'open_bracket': r'\[[^\]]*$',
            'incomplete_string': r'"[^"]*$'
        }

    def detect_truncation(self, content: str) -> TruncationResult:
        """
        综合检测文本是否被截断
        """
        if not content or len(content.strip()) == 0:
            return TruncationResult(False, 0.0, TruncationReason.NOT_TRUNCATED, "空内容")
        
        # 多层检测策略
        detectors = [
            self._detect_incomplete_html_tags,
            self._detect_incomplete_code_blocks,
            self._detect_incomplete_json,
            self._detect_incomplete_sentences,
            self._detect_abrupt_ending,
            self._detect_syntax_incompleteness
        ]
        
        max_confidence = 0.0
        best_result = None
        
        for detector in detectors:
            result = detector(content)
            if result.is_truncated and result.confidence > max_confidence:
                max_confidence = result.confidence
                best_result = result
        
        if best_result:
            return best_result
        else:
            return TruncationResult(False, 1.0, TruncationReason.NOT_TRUNCATED, "内容完整")

    def _detect_incomplete_html_tags(self, content: str) -> TruncationResult:
        """检测未闭合的HTML标签"""
        lines = content.strip().split('\n')
        if not lines:
            return TruncationResult(False, 0.0, TruncationReason.NOT_TRUNCATED, "无内容")
        
        last_line = lines[-1].strip()
        
        # 检查是否以未闭合的HTML标签结束
        if re.search(self.html_tag_pattern, last_line):
            return TruncationResult(
                True, 0.95, 
                TruncationReason.INCOMPLETE_HTML_TAG,
                f"最后一行包含未闭合的HTML标签: {last_line}"
            )
        
        # 检查HTML标签配对
        tag_stack = []
        for line in lines:
            # 查找开始标签
            open_tags = re.findall(r'<(\w+)[^>]*>', line)
            # 查找结束标签
            close_tags = re.findall(r'</(\w+)>', line)
            
            for tag in open_tags:
                if tag.lower() not in ['br', 'hr', 'img', 'input', 'meta', 'link']:  # 自闭合标签
                    tag_stack.append(tag)
            
            for tag in close_tags:
                if tag_stack and tag_stack[-1] == tag:
                    tag_stack.pop()
        
        if tag_stack:
            return TruncationResult(
                True, 0.85,
                TruncationReason.INCOMPLETE_HTML_TAG,
                f"存在未闭合的HTML标签: {tag_stack}"
            )
        
        return TruncationResult(False, 0.0, TruncationReason.NOT_TRUNCATED, "HTML标签完整")

    def _detect_incomplete_code_blocks(self, content: str) -> TruncationResult:
        """检测未完成的代码块"""
        # 检查Markdown代码块
        code_block_starts = len(re.findall(r'```[\w]*\n', content))
        code_block_ends = len(re.findall(r'\n```\s*$', content, re.MULTILINE))
        
        if code_block_starts > code_block_ends:
            return TruncationResult(
                True, 0.90,
                TruncationReason.INCOMPLETE_CODE_BLOCK,
                f"代码块未闭合: 开始{code_block_starts}个，结束{code_block_ends}个"
            )
        
        # 检查HTML script/style标签
        script_starts = len(re.findall(r'<script[^>]*>', content, re.IGNORECASE))
        script_ends = len(re.findall(r'</script>', content, re.IGNORECASE))
        
        if script_starts > script_ends:
            return TruncationResult(
                True, 0.88,
                TruncationReason.INCOMPLETE_CODE_BLOCK,
                f"Script标签未闭合: 开始{script_starts}个，结束{script_ends}个"
            )
        
        style_starts = len(re.findall(r'<style[^>]*>', content, re.IGNORECASE))
        style_ends = len(re.findall(r'</style>', content, re.IGNORECASE))
        
        if style_starts > style_ends:
            return TruncationResult(
                True, 0.88,
                TruncationReason.INCOMPLETE_CODE_BLOCK,
                f"Style标签未闭合: 开始{style_starts}个，结束{style_ends}个"
            )
        
        return TruncationResult(False, 0.0, TruncationReason.NOT_TRUNCATED, "代码块完整")

    def _detect_incomplete_json(self, content: str) -> TruncationResult:
        """检测不完整的JSON结构"""
        lines = content.strip().split('\n')
        if not lines:
            return TruncationResult(False, 0.0, TruncationReason.NOT_TRUNCATED, "无内容")
        
        last_few_lines = '\n'.join(lines[-5:])  # 检查最后5行
        
        # 检查未闭合的大括号
        open_braces = last_few_lines.count('{')
        close_braces = last_few_lines.count('}')
        
        # 检查未闭合的中括号
        open_brackets = last_few_lines.count('[')
        close_brackets = last_few_lines.count(']')
        
        # 检查未闭合的引号
        quotes = last_few_lines.count('"')
        
        if open_braces > close_braces:
            return TruncationResult(
                True, 0.80,
                TruncationReason.INCOMPLETE_JSON,
                f"JSON对象未闭合: 开括号{open_braces}个，闭括号{close_braces}个"
            )
        
        if open_brackets > close_brackets:
            return TruncationResult(
                True, 0.80,
                TruncationReason.INCOMPLETE_JSON,
                f"JSON数组未闭合: 开括号{open_brackets}个，闭括号{close_brackets}个"
            )
        
        if quotes % 2 != 0:
            return TruncationResult(
                True, 0.75,
                TruncationReason.INCOMPLETE_JSON,
                "JSON字符串未闭合: 引号数量为奇数"
            )
        
        return TruncationResult(False, 0.0, TruncationReason.NOT_TRUNCATED, "JSON结构完整")

    def _detect_incomplete_sentences(self, content: str) -> TruncationResult:
        """检测不完整的句子"""
        content = content.strip()
        if len(content) < 10:  # 太短的内容不检测
            return TruncationResult(False, 0.0, TruncationReason.NOT_TRUNCATED, "内容太短")
        
        # 检查最后一个字符是否是句子结束标点
        last_char = content[-1]
        
        # 如果以完整标点结束，可能是完整的
        if last_char in self.sentence_endings:
            return TruncationResult(False, 0.0, TruncationReason.NOT_TRUNCATED, "以完整标点结束")
        
        # 检查最后一句话的完整性
        sentences = re.split(r'[.!?。！？]', content)
        if len(sentences) > 1:
            last_sentence = sentences[-1].strip()
            
            # 如果最后一句话太短且不以标点结束，可能被截断
            if len(last_sentence) > 5 and not last_sentence.endswith(('...', '…')):
                # 检查是否包含常见的不完整模式
                incomplete_patterns = [
                    r'\w+\s*$',  # 以单词结束但没有标点
                    r'[,，]\s*$',  # 以逗号结束
                    r'[;；]\s*$',  # 以分号结束
                    r':\s*$',     # 以冒号结束
                ]
                
                for pattern in incomplete_patterns:
                    if re.search(pattern, last_sentence):
                        return TruncationResult(
                            True, 0.70,
                            TruncationReason.INCOMPLETE_SENTENCE,
                            f"最后一句话可能不完整: '{last_sentence}'"
                        )
        
        return TruncationResult(False, 0.0, TruncationReason.NOT_TRUNCATED, "句子结构完整")

    def _detect_abrupt_ending(self, content: str) -> TruncationResult:
        """检测突然结束的内容"""
        lines = content.strip().split('\n')
        if len(lines) < 3:
            return TruncationResult(False, 0.0, TruncationReason.NOT_TRUNCATED, "内容太短")
        
        last_line = lines[-1].strip()
        
        # 检查最后一行是否异常短
        avg_line_length = sum(len(line) for line in lines) / len(lines)
        
        if len(last_line) < avg_line_length * 0.3 and len(last_line) > 0:
            return TruncationResult(
                True, 0.60,
                TruncationReason.ABRUPT_ENDING,
                f"最后一行异常短: '{last_line}' (平均长度: {avg_line_length:.1f})"
            )
        
        # 检查是否以不完整的单词结束
        if re.search(r'\w+\s*$', last_line) and not re.search(r'[.!?。！？]', last_line):
            return TruncationResult(
                True, 0.65,
                TruncationReason.ABRUPT_ENDING,
                f"以不完整的单词结束: '{last_line}'"
            )
        
        return TruncationResult(False, 0.0, TruncationReason.NOT_TRUNCATED, "结束正常")

    def _detect_syntax_incompleteness(self, content: str) -> TruncationResult:
        """检测语法不完整性"""
        # 检查括号配对
        parentheses = content.count('(') - content.count(')')
        square_brackets = content.count('[') - content.count(']')
        curly_braces = content.count('{') - content.count('}')
        
        if parentheses > 0:
            return TruncationResult(
                True, 0.75,
                TruncationReason.INCOMPLETE_SYNTAX,
                f"圆括号未配对: 多{parentheses}个开括号"
            )
        
        if square_brackets > 0:
            return TruncationResult(
                True, 0.75,
                TruncationReason.INCOMPLETE_SYNTAX,
                f"方括号未配对: 多{square_brackets}个开括号"
            )
        
        if curly_braces > 0:
            return TruncationResult(
                True, 0.75,
                TruncationReason.INCOMPLETE_SYNTAX,
                f"花括号未配对: 多{curly_braces}个开括号"
            )
        
        return TruncationResult(False, 0.0, TruncationReason.NOT_TRUNCATED, "语法完整")

def analyze_test_file():
    """分析test.txt文件"""
    try:
        with open('test.txt', 'r', encoding='utf-8') as f:
            content = f.read()
        
        detector = AdvancedTruncationDetector()
        result = detector.detect_truncation(content)
        
        print("=== AI回复截断检测结果 ===")
        print(f"是否被截断: {'是' if result.is_truncated else '否'}")
        print(f"置信度: {result.confidence:.2%}")
        print(f"截断原因: {result.reason.value}")
        print(f"详细信息: {result.details}")
        
        if result.is_truncated:
            print("\n=== 截断分析 ===")
            print("检测到的问题:")
            print(f"- {result.details}")
            
            # 分析内容特征
            lines = content.strip().split('\n')
            print(f"- 总行数: {len(lines)}")
            print(f"- 最后一行: '{lines[-1].strip()}'")
            print(f"- 内容长度: {len(content)} 字符")
            
            # 建议续写提示
            print("\n=== 建议的续写提示 ===")
            print("请继续完成以下HTML代码，从截断处开始继续，确保所有标签正确闭合：")
            print(f"[原始内容的最后50个字符: ...{content[-50:]}]")
        
        return result
        
    except FileNotFoundError:
        print("错误: 找不到test.txt文件")
        return None
    except Exception as e:
        print(f"错误: {e}")
        return None

if __name__ == "__main__":
    analyze_test_file()