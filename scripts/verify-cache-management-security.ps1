# 缓存管理接口安全验证脚本 (PowerShell版本)
# 验证端口配置和访问控制是否正确

Write-Host "=== 缓存管理接口安全验证 ===" -ForegroundColor Cyan
Write-Host "时间: $(Get-Date)" -ForegroundColor Gray
Write-Host ""

# 配置
$MAIN_PORT = 8080
$CACHE_MGMT_PORT = 8081
$LOCAL_HOST = "127.0.0.1"

# 检查端口函数
function Check-Port {
    param(
        [int]$Port,
        [string]$Description
    )
    
    Write-Host "检查 $Description (端口 $Port): " -NoNewline
    
    try {
        $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        if ($connections) {
            Write-Host "✓ 端口已监听" -ForegroundColor Green
            return $true
        } else {
            Write-Host "✗ 端口未监听" -ForegroundColor Red
            return $false
        }
    } catch {
        Write-Host "✗ 检查失败: $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

# HTTP请求检查函数
function Check-Http {
    param(
        [string]$Url,
        [string]$Description,
        [int]$ExpectedStatus,
        [hashtable]$Headers = @{}
    )
    
    Write-Host "检查 $Description`: " -NoNewline
    
    try {
        $response = Invoke-WebRequest -Uri $Url -Method GET -Headers $Headers -TimeoutSec 10 -ErrorAction SilentlyContinue
        $statusCode = $response.StatusCode
        
        if ($statusCode -eq $ExpectedStatus) {
            Write-Host "✓ 状态码 $statusCode" -ForegroundColor Green
            return $true
        } else {
            Write-Host "✗ 状态码 $statusCode (期望 $ExpectedStatus)" -ForegroundColor Red
            return $false
        }
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -eq $ExpectedStatus) {
            Write-Host "✓ 状态码 $statusCode" -ForegroundColor Green
            return $true
        } else {
            Write-Host "✗ 状态码 $statusCode (期望 $ExpectedStatus)" -ForegroundColor Red
            return $false
        }
    }
}

# 1. 检查端口监听状态
Write-Host "1. 端口监听检查" -ForegroundColor Yellow
Write-Host "=================="
Check-Port -Port $MAIN_PORT -Description "主应用服务"
Check-Port -Port $CACHE_MGMT_PORT -Description "缓存管理服务"
Write-Host ""

# 2. 检查应用是否启动
Write-Host "2. 应用启动检查" -ForegroundColor Yellow
Write-Host "=================="

# 检查Java进程
$javaProcesses = Get-Process -Name "java" -ErrorAction SilentlyContinue
if ($javaProcesses) {
    Write-Host "✓ 找到Java进程: $($javaProcesses.Count) 个" -ForegroundColor Green
    foreach ($process in $javaProcesses) {
        Write-Host "  - PID: $($process.Id), 内存: $([math]::Round($process.WorkingSet64/1MB, 2)) MB" -ForegroundColor Gray
    }
} else {
    Write-Host "✗ 未找到Java进程" -ForegroundColor Red
}
Write-Host ""

# 3. 检查缓存管理接口本地访问
Write-Host "3. 缓存管理接口本地访问检查" -ForegroundColor Yellow
Write-Host "=========================="

$cacheEndpoints = @(
    @{ Url = "http://$LOCAL_HOST`:$CACHE_MGMT_PORT/api/admin/cache/stats"; Description = "缓存统计接口" },
    @{ Url = "http://$LOCAL_HOST`:$CACHE_MGMT_PORT/api/admin/cache/health"; Description = "缓存健康检查" },
    @{ Url = "http://$LOCAL_HOST`:$CACHE_MGMT_PORT/api/admin/cache/config"; Description = "缓存配置接口" }
)

foreach ($endpoint in $cacheEndpoints) {
    Check-Http -Url $endpoint.Url -Description $endpoint.Description -ExpectedStatus 200
}
Write-Host ""

# 4. 检查访问控制 (模拟远程访问)
Write-Host "4. 访问控制检查" -ForegroundColor Yellow
Write-Host "==============="
Write-Host "注意: 以下测试模拟远程访问，应该被拒绝" -ForegroundColor Gray

$remoteIPs = @("192.168.1.100", "10.0.0.1", "172.16.0.1", "8.8.8.8")

foreach ($ip in $remoteIPs) {
    $headers = @{ "X-Forwarded-For" = $ip }
    Write-Host "模拟从 $ip 访问: " -NoNewline
    
    try {
        $response = Invoke-WebRequest -Uri "http://$LOCAL_HOST`:$CACHE_MGMT_PORT/api/admin/cache/stats" -Headers $headers -TimeoutSec 5 -ErrorAction SilentlyContinue
        Write-Host "✗ 状态码 $($response.StatusCode) (期望 403)" -ForegroundColor Red
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -eq 403) {
            Write-Host "✓ 正确拒绝 (403)" -ForegroundColor Green
        } else {
            Write-Host "✗ 状态码 $statusCode (期望 403)" -ForegroundColor Red
        }
    }
}
Write-Host ""

# 5. 检查本地地址变体
Write-Host "5. 本地地址变体检查" -ForegroundColor Yellow
Write-Host "=================="

$localVariants = @("127.0.0.1", "localhost")

foreach ($addr in $localVariants) {
    $headers = @{ "X-Forwarded-For" = $addr }
    Write-Host "使用 $addr 访问: " -NoNewline
    
    try {
        $response = Invoke-WebRequest -Uri "http://$LOCAL_HOST`:$CACHE_MGMT_PORT/api/admin/cache/stats" -Headers $headers -TimeoutSec 5 -ErrorAction SilentlyContinue
        if ($response.StatusCode -eq 200) {
            Write-Host "✓ 允许访问 (200)" -ForegroundColor Green
        } else {
            Write-Host "✗ 状态码 $($response.StatusCode) (期望 200)" -ForegroundColor Red
        }
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        Write-Host "✗ 状态码 $statusCode (期望 200)" -ForegroundColor Red
    }
}
Write-Host ""

# 6. 检查网络连接
Write-Host "6. 网络连接检查" -ForegroundColor Yellow
Write-Host "==============="

try {
    $connections = Get-NetTCPConnection -LocalPort $CACHE_MGMT_PORT -ErrorAction SilentlyContinue
    if ($connections) {
        Write-Host "✓ 找到缓存管理端口连接:" -ForegroundColor Green
        foreach ($conn in $connections) {
            Write-Host "  - 本地地址: $($conn.LocalAddress):$($conn.LocalPort), 状态: $($conn.State)" -ForegroundColor Gray
        }
    } else {
        Write-Host "✗ 未找到缓存管理端口连接" -ForegroundColor Red
    }
} catch {
    Write-Host "✗ 网络连接检查失败: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 7. 生成验证报告
Write-Host "7. 验证报告" -ForegroundColor Yellow
Write-Host "==========="

# 检查应用日志
$logPaths = @("logs\application.log", "application.log", ".\logs\application.log")
$foundLog = $false

foreach ($logPath in $logPaths) {
    if (Test-Path $logPath) {
        Write-Host "✓ 找到日志文件: $logPath" -ForegroundColor Green
        
        # 查找缓存管理相关日志
        $logContent = Get-Content $logPath -Tail 50 | Where-Object { $_ -match "缓存管理|8081|访问控制" }
        if ($logContent) {
            Write-Host "最近的缓存管理日志:" -ForegroundColor Gray
            $logContent | Select-Object -Last 5 | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }
        }
        $foundLog = $true
        break
    }
}

if (-not $foundLog) {
    Write-Host "⚠ 未找到应用日志文件" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== 验证完成 ===" -ForegroundColor Cyan
Write-Host "时间: $(Get-Date)" -ForegroundColor Gray
Write-Host ""
Write-Host "建议:" -ForegroundColor Yellow
Write-Host "1. 确保防火墙也阻止外部访问8081端口"
Write-Host "2. 监控8081端口的访问日志"
Write-Host "3. 定期运行此脚本验证安全配置"
Write-Host ""
Write-Host "如需远程管理，请使用:" -ForegroundColor Yellow
Write-Host "  SSH隧道: ssh -L 8081:127.0.0.1:8081 user@server"
Write-Host "  然后访问: http://127.0.0.1:8081/api/admin/cache/stats"
