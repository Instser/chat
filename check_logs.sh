#!/bin/bash

# 查看包含联网相关日志的最近100行
echo "=== 查找联网相关日志 ==="
ps aux | grep java | grep chat | head -1 | awk '{print $2}' | xargs -I {} tail -f /proc/{}/fd/1 2>/dev/null | grep -E "(联网|网络|network|Network)" | tail -20

echo ""
echo "=== 查找调试相关日志 ==="
ps aux | grep java | grep chat | head -1 | awk '{print $2}' | xargs -I {} tail -f /proc/{}/fd/1 2>/dev/null | grep -E "(调试|DEBUG)" | tail -10
