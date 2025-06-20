#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MD文件重命名脚本
根据文件内容中的时间信息重命名为 "月-日 小时:分钟 内容" 格式
"""

import os
import re
import shutil
from datetime import datetime
from pathlib import Path

def extract_time_from_content(file_path):
    """从文件内容中提取时间信息"""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()

        # 匹配各种时间格式
        time_patterns = [
            # 标准格式：2025-06-20 22:53:54
            r'创建时间.*?(\d{4})-(\d{2})-(\d{2})\s+(\d{2}):(\d{2})(?::\d{2})?',
            r'完成时间.*?(\d{4})-(\d{2})-(\d{2})\s+(\d{2}):(\d{2})(?::\d{2})?',
            r'执行时间.*?(\d{4})-(\d{2})-(\d{2})\s+(\d{2}):(\d{2})(?::\d{2})?',
            r'生成时间.*?(\d{4})-(\d{2})-(\d{2})\s+(\d{2}):(\d{2})(?::\d{2})?',
            r'最后更新.*?(\d{4})-(\d{2})-(\d{2})\s+(\d{2}):(\d{2})(?::\d{2})?',
            r'时间.*?(\d{4})-(\d{2})-(\d{2})\s+(\d{2}):(\d{2})(?::\d{2})?',
            r'测试时间.*?(\d{4})-(\d{2})-(\d{2})\s+(\d{2}):(\d{2})',
            r'部署时间.*?(\d{4})-(\d{2})-(\d{2})\s+(\d{2}):(\d{2})',
            # 中文格式：2025年6月20日 22:34
            r'(\d{4})年(\d{1,2})月(\d{1,2})日\s+(\d{2}):(\d{2})',
            r'创建时间.*?(\d{4})年(\d{1,2})月(\d{1,2})日\s+(\d{2}):(\d{2})',
            # 简单日期格式：@date 2024-12-19
            r'@date\s+(\d{4})-(\d{2})-(\d{2})',
            # 文件名中已有的时间格式：06-21 00:54
            r'^(\d{2})-(\d{2})\s+(\d{2}):(\d{2})',
        ]

        for pattern in time_patterns:
            match = re.search(pattern, content)
            if match:
                groups = match.groups()

                if len(groups) == 3:  # @date格式或文件名格式
                    if pattern.startswith(r'^'):  # 文件名格式：06-21 00:54
                        month, day, hour, minute = groups[0], groups[1], groups[2], "00"
                        if len(groups) > 3:
                            minute = groups[3]
                    else:  # @date格式
                        year, month, day = groups
                        hour, minute = "00", "00"
                elif len(groups) == 4:  # 文件名格式：06-21 00:54
                    month, day, hour, minute = groups
                elif len(groups) == 5:  # 完整格式
                    if '年' in pattern:  # 中文年月日格式
                        year, month, day, hour, minute = groups
                    else:  # 标准格式：2025-06-20 22:53:54
                        year, month, day, hour, minute = groups
                else:
                    continue

                return f"{int(month):02d}-{int(day):02d} {hour}:{minute}"

        return None
    except Exception as e:
        print(f"读取文件 {file_path} 时出错: {e}")
        return None

def get_content_description(file_path):
    """从文件名或内容中提取内容描述"""
    file_name = os.path.basename(file_path)
    
    # 移除.md后缀
    base_name = file_name.replace('.md', '')
    
    # 如果已经是时间格式开头，提取内容部分
    time_prefix_pattern = r'^\d{2}-\d{2}\s+\d{2}:\d{2}\s+'
    if re.match(time_prefix_pattern, base_name):
        content_part = re.sub(time_prefix_pattern, '', base_name)
        return content_part if content_part else base_name
    
    # 移除一些常见的前缀
    prefixes_to_remove = [
        '06-21 00:54 ',
        'docs/',
        '支付/',
    ]
    
    for prefix in prefixes_to_remove:
        if base_name.startswith(prefix):
            base_name = base_name[len(prefix):]
    
    return base_name

def rename_md_files():
    """重命名所有MD文件"""
    current_dir = Path('.')
    renamed_count = 0
    failed_count = 0
    
    # 获取所有MD文件，排除memory bank文件夹
    md_files = []
    for md_file in current_dir.rglob('*.md'):
        if 'memory bank' not in str(md_file):
            md_files.append(md_file)
    
    print(f"找到 {len(md_files)} 个MD文件需要处理")
    
    for md_file in md_files:
        try:
            # 提取时间信息
            time_str = extract_time_from_content(md_file)
            
            if time_str:
                # 获取内容描述
                content_desc = get_content_description(str(md_file))
                
                # 构建新文件名
                new_name = f"{time_str} {content_desc}.md"
                
                # 构建新路径
                new_path = md_file.parent / new_name
                
                # 检查是否需要重命名
                if md_file.name != new_name:
                    # 检查新文件名是否已存在
                    if new_path.exists():
                        print(f"⚠️  跳过 {md_file}: 目标文件已存在 {new_name}")
                        continue
                    
                    # 重命名文件
                    md_file.rename(new_path)
                    print(f"✅ {md_file.name} -> {new_name}")
                    renamed_count += 1
                else:
                    print(f"⏭️  跳过 {md_file.name}: 已是正确格式")
            else:
                print(f"❌ 无法提取时间信息: {md_file}")
                failed_count += 1
                
        except Exception as e:
            print(f"❌ 处理文件 {md_file} 时出错: {e}")
            failed_count += 1
    
    print(f"\n📊 处理完成:")
    print(f"✅ 成功重命名: {renamed_count} 个文件")
    print(f"❌ 处理失败: {failed_count} 个文件")
    print(f"📁 总计处理: {len(md_files)} 个文件")

if __name__ == "__main__":
    print("🚀 开始重命名MD文件...")
    rename_md_files()
    print("🎉 重命名完成!")
