#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
网络搜索功能日志监控脚本
监控时间: 2025-07-20 11:40
作者: Augment Agent
"""

import subprocess
import time
import re
import threading
from datetime import datetime
from collections import defaultdict, deque

class LogMonitor:
    def __init__(self):
        self.log_patterns = {
            'web_search_update': r'网络搜索修改|WebSearch|updateContentByMessageId',
            'rate_limit': r'频率限制|rate.*limit|RATE_LIMIT_EXCEEDED',
            'cache_stats': r'权限缓存|WebSearchCache|缓存统计',
            'performance': r'WebSearchMetrics|性能监控|监控切面',
            'errors': r'ERROR|Exception|异常|失败',
            'warnings': r'WARN|警告',
            'database': r'数据库|Database|SQL',
            'redis': r'Redis|redis'
        }
        
        self.stats = defaultdict(int)
        self.recent_logs = deque(maxlen=100)
        self.monitoring = True
        
    def log(self, message):
        """打印带时间戳的日志"""
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"[{timestamp}] {message}")
        
    def analyze_log_line(self, line):
        """分析日志行"""
        timestamp = datetime.now().strftime("%H:%M:%S")
        
        # 检查各种模式
        for pattern_name, pattern in self.log_patterns.items():
            if re.search(pattern, line, re.IGNORECASE):
                self.stats[pattern_name] += 1
                
                # 特殊处理重要日志
                if pattern_name == 'web_search_update':
                    self.log(f"🔍 [网络搜索] {line.strip()}")
                elif pattern_name == 'rate_limit':
                    self.log(f"🚫 [频率限制] {line.strip()}")
                elif pattern_name == 'errors':
                    self.log(f"❌ [错误] {line.strip()}")
                elif pattern_name == 'cache_stats':
                    self.log(f"📊 [缓存] {line.strip()}")
                elif pattern_name == 'performance':
                    self.log(f"⚡ [性能] {line.strip()}")
        
        # 保存最近的日志
        self.recent_logs.append(f"[{timestamp}] {line.strip()}")
    
    def monitor_application_logs(self):
        """监控应用日志"""
        self.log("📋 开始监控应用日志...")
        
        try:
            # 使用tail -f监控日志文件
            process = subprocess.Popen(
                ['tail', '-f', 'logs/application.log'],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                universal_newlines=True,
                bufsize=1
            )
            
            while self.monitoring:
                line = process.stdout.readline()
                if line:
                    self.analyze_log_line(line)
                else:
                    time.sleep(0.1)
                    
        except FileNotFoundError:
            self.log("⚠️ 日志文件不存在，尝试监控控制台输出...")
            # 如果日志文件不存在，可以监控其他方式
            time.sleep(1)
        except Exception as e:
            self.log(f"❌ 日志监控异常: {str(e)}")
    
    def monitor_system_metrics(self):
        """监控系统指标"""
        self.log("📊 开始监控系统指标...")
        
        while self.monitoring:
            try:
                # 检查应用健康状态
                import requests
                health_response = requests.get("http://localhost:8081/actuator/health", timeout=2)
                if health_response.status_code == 200:
                    health_data = health_response.json()
                    status = health_data.get('status', 'UNKNOWN')
                    if status != 'UP':
                        self.log(f"⚠️ 应用健康状态异常: {status}")
                
                # 检查自定义指标
                metrics_response = requests.get("http://localhost:8081/actuator/metrics", timeout=2)
                if metrics_response.status_code == 200:
                    metrics_data = metrics_response.json()
                    web_search_metrics = [name for name in metrics_data.get('names', []) 
                                        if 'web_search' in name.lower()]
                    
                    for metric_name in web_search_metrics:
                        try:
                            metric_response = requests.get(
                                f"http://localhost:8081/actuator/metrics/{metric_name}", 
                                timeout=2
                            )
                            if metric_response.status_code == 200:
                                metric_data = metric_response.json()
                                measurements = metric_data.get('measurements', [])
                                for measurement in measurements:
                                    value = measurement.get('value', 0)
                                    if value > 0:
                                        self.log(f"📈 指标 {metric_name}: {value}")
                        except:
                            pass
                
            except Exception as e:
                # 静默处理网络错误
                pass
            
            time.sleep(10)  # 每10秒检查一次
    
    def print_statistics(self):
        """打印统计信息"""
        while self.monitoring:
            time.sleep(30)  # 每30秒打印一次统计
            
            self.log("=" * 50)
            self.log("📊 日志监控统计报告")
            self.log("=" * 50)
            
            for pattern_name, count in self.stats.items():
                if count > 0:
                    self.log(f"📋 {pattern_name}: {count} 次")
            
            if not any(self.stats.values()):
                self.log("📋 暂无相关日志记录")
            
            self.log("=" * 50)
    
    def search_logs(self, keyword):
        """搜索日志"""
        self.log(f"🔍 搜索关键词: {keyword}")
        
        matching_logs = [log for log in self.recent_logs if keyword.lower() in log.lower()]
        
        if matching_logs:
            self.log(f"📋 找到 {len(matching_logs)} 条匹配日志:")
            for log in matching_logs[-10:]:  # 显示最近10条
                print(f"  {log}")
        else:
            self.log("📋 未找到匹配的日志")
    
    def start_monitoring(self):
        """开始监控"""
        self.log("🚀 启动网络搜索功能日志监控")
        self.log("监控内容:")
        self.log("  - 网络搜索内容修改相关日志")
        self.log("  - 频率限制和缓存统计")
        self.log("  - 性能监控和错误日志")
        self.log("  - 系统健康状态")
        self.log("=" * 60)
        
        # 启动监控线程
        log_thread = threading.Thread(target=self.monitor_application_logs)
        metrics_thread = threading.Thread(target=self.monitor_system_metrics)
        stats_thread = threading.Thread(target=self.print_statistics)
        
        log_thread.daemon = True
        metrics_thread.daemon = True
        stats_thread.daemon = True
        
        log_thread.start()
        metrics_thread.start()
        stats_thread.start()
        
        try:
            # 主线程等待用户输入
            while True:
                user_input = input("\n输入命令 (search <关键词> | stats | quit): ").strip()
                
                if user_input.lower() == 'quit':
                    break
                elif user_input.lower() == 'stats':
                    self.print_current_stats()
                elif user_input.startswith('search '):
                    keyword = user_input[7:]
                    self.search_logs(keyword)
                else:
                    self.log("可用命令:")
                    self.log("  search <关键词> - 搜索日志")
                    self.log("  stats - 显示当前统计")
                    self.log("  quit - 退出监控")
                    
        except KeyboardInterrupt:
            pass
        
        self.monitoring = False
        self.log("🛑 停止日志监控")
    
    def print_current_stats(self):
        """打印当前统计信息"""
        self.log("📊 当前统计信息:")
        for pattern_name, count in self.stats.items():
            if count > 0:
                self.log(f"  {pattern_name}: {count} 次")
        
        if not any(self.stats.values()):
            self.log("  暂无统计数据")

if __name__ == "__main__":
    monitor = LogMonitor()
    monitor.start_monitoring()
