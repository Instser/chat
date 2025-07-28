#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
测试缩进修复的简单脚本
"""

import re

def test_java_regex_fix():
    """测试Java正则表达式修复"""
    
    # 测试用例：包含各种缩进情况的HTML
    test_html = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Test</title>
  <style>
    /* CSS with indentation */
    body {
      margin: 0;
      padding: 0;
    }
    
    .container {
      display: flex;
      justify-content: center;
    }
  </style>
</head>
<body>
  <div class="container">
    <h1>Hello    World</h1>
    <p>This  has   multiple    spaces</p>
  </div>
</body>
</html>"""

    print("=== 原始HTML ===")
    print(repr(test_html))
    print(f"长度: {len(test_html)}")
    
    # 模拟原来的错误处理（会破坏缩进）
    old_cleaned = test_html.replace('\uFFFD', '').replace('\u0000', '')
    old_cleaned = re.sub(r' {2,}', ' ', old_cleaned)
    
    print("\n=== 原来的错误处理结果 ===")
    print(repr(old_cleaned))
    print(f"长度: {len(old_cleaned)}")
    
    # 模拟新的修复处理（保留缩进）
    new_cleaned = test_html.replace('\uFFFD', '').replace('\u0000', '')

    # 使用更兼容的方法：逐行处理，只清理行内的多余空格
    lines = new_cleaned.split('\n')
    processed_lines = []
    for line in lines:
        if line.strip():  # 非空行
            # 找到第一个非空格字符的位置
            leading_spaces = len(line) - len(line.lstrip(' '))
            content_part = line[leading_spaces:]
            # 只对内容部分清理多余空格
            cleaned_content_part = re.sub(r' {2,}', ' ', content_part)
            processed_lines.append(' ' * leading_spaces + cleaned_content_part)
        else:
            processed_lines.append(line)  # 保留空行
    new_cleaned = '\n'.join(processed_lines)
    
    print("\n=== 新的修复处理结果 ===")
    print(repr(new_cleaned))
    print(f"长度: {len(new_cleaned)}")
    
    # 验证结果
    print("\n=== 验证结果 ===")
    
    # 检查行首缩进是否保留
    original_lines = test_html.split('\n')
    new_lines = new_cleaned.split('\n')
    
    indentation_preserved = True
    for i, (orig_line, new_line) in enumerate(zip(original_lines, new_lines)):
        orig_indent = len(orig_line) - len(orig_line.lstrip(' '))
        new_indent = len(new_line) - len(new_line.lstrip(' '))
        
        if orig_indent != new_indent:
            print(f"❌ 第{i+1}行缩进不匹配: 原始{orig_indent}空格 -> 修复后{new_indent}空格")
            print(f"   原始: {repr(orig_line)}")
            print(f"   修复: {repr(new_line)}")
            indentation_preserved = False
    
    if indentation_preserved:
        print("✅ 所有行的缩进都得到了正确保留")
    
    # 检查行内多余空格是否被清理
    line_with_multiple_spaces = "    <h1>Hello    World</h1>"
    expected_cleaned = "    <h1>Hello World</h1>"

    # 使用相同的逐行处理方法
    leading_spaces = len(line_with_multiple_spaces) - len(line_with_multiple_spaces.lstrip(' '))
    content_part = line_with_multiple_spaces[leading_spaces:]
    cleaned_content_part = re.sub(r' {2,}', ' ', content_part)
    test_result = ' ' * leading_spaces + cleaned_content_part

    if test_result == expected_cleaned:
        print("✅ 行内多余空格被正确清理，缩进得到保留")
    else:
        print(f"❌ 行内空格清理失败:")
        print(f"   期望: {repr(expected_cleaned)}")
        print(f"   实际: {repr(test_result)}")

if __name__ == "__main__":
    test_java_regex_fix()
