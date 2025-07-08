import socket
import threading
from queue import Queue
import time
import sys

# --- 配置区 ---
TARGET_IP = '203.0.113.13'
# 扫描所有 TCP 端口
PORTS_TO_SCAN = range(1, 65536)

NUM_THREADS = 100        # 对于全端口扫描，可以适当增加线程数以加快速度
CONNECTION_TIMEOUT = 1.0 # 适当缩短超时时间，避免在关闭的端口上浪费太多时间
# --- 配置区结束 ---

# 待扫描端口的队列
port_queue = Queue()
# 存储开放的 HTTP 端口
open_http_ports = []

# --- 用于进度显示 ---
total_ports = len(PORTS_TO_SCAN)
scanned_count = 0
start_time = 0
scan_lock = threading.Lock()
# ---

def check_port(port):
    """
    检查单个端口是否开放并且是 HTTP 服务
    """
    global scanned_count
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(CONNECTION_TIMEOUT)
            if s.connect_ex((TARGET_IP, port)) == 0:
                # 端口开放，尝试识别 HTTP 服务
                try:
                    request = f"HEAD / HTTP/1.1\r\nHost: {TARGET_IP}\r\n\r\n".encode()
                    s.sendall(request)
                    response = s.recv(1024)
                    if response.startswith(b'HTTP/'):
                        with scan_lock:
                            print(f"\n[+] 发现 HTTP 服务: {TARGET_IP}:{port}")
                            print(f"    └── 响应: {response.split(b'\\r\\n')[0].decode()}")
                            open_http_ports.append(port)
                    else:
                        with scan_lock:
                            print(f"\n[!] 端口开放但非标准 HTTP 或为 SSL/TLS: {TARGET_IP}:{port}")
                            open_http_ports.append(port)
                except (socket.timeout, socket.error):
                    with scan_lock:
                        print(f"\n[!] 端口开放但无法获取 HTTP 响应: {TARGET_IP}:{port} (可能是 HTTPS?)")
                        open_http_ports.append(port)
    except Exception:
        pass
    finally:
        with scan_lock:
            scanned_count += 1

def worker():
    """
    线程工作函数，从队列中获取端口并扫描
    """
    while not port_queue.empty():
        port = port_queue.get()
        check_port(port)
        port_queue.task_done()

def print_progress():
    """
    在单独的线程中打印扫描进度
    """
    while scanned_count < total_ports:
        # 使用 sys.stdout.write 和 \r 来实现单行刷新
        progress = (scanned_count / total_ports) * 100
        elapsed_time = time.time() - start_time
        # 避免除以零
        speed = scanned_count / elapsed_time if elapsed_time > 0 else 0
        
        # 打印进度条
        bar_len = 40
        filled_len = int(round(bar_len * progress / 100))
        bar = '█' * filled_len + '-' * (bar_len - filled_len)
        
        sys.stdout.write(f"\r进度: [{bar}] {progress:.2f}% ({scanned_count}/{total_ports}) | 速度: {speed:.2f} p/s ")
        sys.stdout.flush()
        time.sleep(0.5)
    # 确保最后打印 100%
    sys.stdout.write(f"\r进度: [{'█' * 40}] 100.00% ({total_ports}/{total_ports})" + " " * 20 + "\n")
    sys.stdout.flush()


def main():
    global start_time
    print(f"[*] 开始扫描 IP: {TARGET_IP}")
    print(f"[*] 扫描端口范围: 1-65535")
    print(f"[*] 线程数: {NUM_THREADS}")
    print("\n" + "="*50)
    print("警告：全端口扫描非常耗时，请耐心等待...")
    print("="*50 + "\n")

    start_time = time.time()

    # 将所有端口放入队列
    for port in PORTS_TO_SCAN:
        port_queue.put(port)

    # 启动进度显示线程
    progress_thread = threading.Thread(target=print_progress, daemon=True)
    progress_thread.start()

    # 创建并启动工作线程
    threads = []
    for _ in range(NUM_THREADS):
        t = threading.Thread(target=worker, daemon=True)
        t.start()
        threads.append(t)

    # 等待所有端口扫描完成
    port_queue.join()

    # 等待进度条线程完成最后的打印
    time.sleep(0.6)

    end_time = time.time()

    print("\n" + "-"*50)
    print(f"[*] 扫描完成，总耗时: {end_time - start_time:.2f} 秒")

    if open_http_ports:
        open_http_ports.sort()
        print(f"\n[✓] 总结：在 {TARGET_IP} 上发现以下可能开放 HTTP/HTTPS 访问的端口:")
        print("    " + ", ".join(map(str, open_http_ports)))
    else:
        print(f"\n[✗] 总结：在 {TARGET_IP} 上未发现任何开放的端口。")

if __name__ == "__main__":
    main()
