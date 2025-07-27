# 缓存管理配置测试脚本
# 测试配置文件和代码是否正确

Write-Host "=== 缓存管理配置测试 ===" -ForegroundColor Cyan
Write-Host "时间: $(Get-Date)" -ForegroundColor Gray
Write-Host ""

# 1. 检查配置文件
Write-Host "1. 配置文件检查" -ForegroundColor Yellow
Write-Host "================"

# 检查application.yml
$appConfigPath = "src\main\resources\application.yml"
if (Test-Path $appConfigPath) {
    Write-Host "✓ 找到application.yml配置文件" -ForegroundColor Green
    
    $configContent = Get-Content $appConfigPath -Raw
    if ($configContent -match "cache:\s*management:") {
        Write-Host "✓ 找到缓存管理配置" -ForegroundColor Green
        
        if ($configContent -match "port:\s*8081") {
            Write-Host "✓ 端口配置正确 (8081)" -ForegroundColor Green
        } else {
            Write-Host "✗ 端口配置未找到或不正确" -ForegroundColor Red
        }
        
        if ($configContent -match "enabled:\s*true") {
            Write-Host "✓ 缓存管理已启用" -ForegroundColor Green
        } else {
            Write-Host "⚠ 缓存管理可能未启用" -ForegroundColor Yellow
        }
    } else {
        Write-Host "✗ 未找到缓存管理配置" -ForegroundColor Red
    }
} else {
    Write-Host "✗ 未找到application.yml配置文件" -ForegroundColor Red
}
Write-Host ""

# 2. 检查Java配置类
Write-Host "2. Java配置类检查" -ForegroundColor Yellow
Write-Host "=================="

$configClasses = @(
    @{ Path = "src\main\java\com\instser\chat\config\CacheManagementConfig.java"; Name = "访问控制配置" },
    @{ Path = "src\main\java\com\instser\chat\config\CacheManagementServerConfig.java"; Name = "服务器配置" }
)

foreach ($configClass in $configClasses) {
    if (Test-Path $configClass.Path) {
        Write-Host "✓ 找到$($configClass.Name): $($configClass.Path)" -ForegroundColor Green
        
        $classContent = Get-Content $configClass.Path -Raw
        
        # 检查关键注解和方法
        if ($classContent -match "@Configuration") {
            Write-Host "  ✓ 包含@Configuration注解" -ForegroundColor Green
        }
        
        if ($configClass.Name -eq "访问控制配置") {
            if ($classContent -match "CacheManagementAccessFilter") {
                Write-Host "  ✓ 包含访问控制过滤器" -ForegroundColor Green
            }
            if ($classContent -match "127\.0\.0\.1") {
                Write-Host "  ✓ 包含本地IP检查" -ForegroundColor Green
            }
        }
        
        if ($configClass.Name -eq "服务器配置") {
            if ($classContent -match "8081") {
                Write-Host "  ✓ 包含8081端口配置" -ForegroundColor Green
            }
            if ($classContent -match "TomcatServletWebServerFactory") {
                Write-Host "  ✓ 包含Tomcat配置" -ForegroundColor Green
            }
        }
    } else {
        Write-Host "✗ 未找到$($configClass.Name): $($configClass.Path)" -ForegroundColor Red
    }
}
Write-Host ""

# 3. 检查控制器
Write-Host "3. 控制器检查" -ForegroundColor Yellow
Write-Host "============="

$controllerPath = "src\main\java\com\instser\chat\controller\CacheManagementController.java"
if (Test-Path $controllerPath) {
    Write-Host "✓ 找到缓存管理控制器" -ForegroundColor Green
    
    $controllerContent = Get-Content $controllerPath -Raw
    
    if ($controllerContent -match "@RestController") {
        Write-Host "  ✓ 包含@RestController注解" -ForegroundColor Green
    }
    
    if ($controllerContent -match "@RequestMapping.*admin/cache") {
        Write-Host "  ✓ 包含正确的请求映射" -ForegroundColor Green
    }
    
    # 统计接口数量
    $getMethods = ($controllerContent | Select-String "@GetMapping" -AllMatches).Matches.Count
    $postMethods = ($controllerContent | Select-String "@PostMapping" -AllMatches).Matches.Count
    $deleteMethods = ($controllerContent | Select-String "@DeleteMapping" -AllMatches).Matches.Count
    
    Write-Host "  ✓ 接口统计: GET($getMethods), POST($postMethods), DELETE($deleteMethods)" -ForegroundColor Green
} else {
    Write-Host "✗ 未找到缓存管理控制器" -ForegroundColor Red
}
Write-Host ""

# 4. 检查测试文件
Write-Host "4. 测试文件检查" -ForegroundColor Yellow
Write-Host "==============="

$testFiles = @(
    "src\test\java\com\instser\chat\config\CacheManagementAccessTest.java",
    "src\test\java\com\instser\chat\service\cache\GlobalCacheManagerTest.java",
    "src\test\java\com\instser\chat\service\cache\CachePerformanceTest.java"
)

foreach ($testFile in $testFiles) {
    if (Test-Path $testFile) {
        Write-Host "✓ 找到测试文件: $(Split-Path $testFile -Leaf)" -ForegroundColor Green
        
        $testContent = Get-Content $testFile -Raw
        $testMethods = ($testContent | Select-String "@Test" -AllMatches).Matches.Count
        Write-Host "  - 测试方法数量: $testMethods" -ForegroundColor Gray
    } else {
        Write-Host "✗ 未找到测试文件: $(Split-Path $testFile -Leaf)" -ForegroundColor Red
    }
}
Write-Host ""

# 5. 检查文档
Write-Host "5. 文档检查" -ForegroundColor Yellow
Write-Host "==========="

$docFiles = @(
    "docs\07-24 13_13 缓存管理接口安全配置说明.md",
    "docs\07-24 13_13 缓存管理接口端口和安全配置完成报告.md"
)

foreach ($docFile in $docFiles) {
    if (Test-Path $docFile) {
        Write-Host "✓ 找到文档: $(Split-Path $docFile -Leaf)" -ForegroundColor Green
        
        $docContent = Get-Content $docFile -Raw
        if ($docContent -match "8081") {
            Write-Host "  ✓ 文档包含8081端口说明" -ForegroundColor Green
        }
        if ($docContent -match "127\.0\.0\.1") {
            Write-Host "  ✓ 文档包含本地访问说明" -ForegroundColor Green
        }
    } else {
        Write-Host "✗ 未找到文档: $(Split-Path $docFile -Leaf)" -ForegroundColor Red
    }
}
Write-Host ""

# 6. 检查项目结构
Write-Host "6. 项目结构检查" -ForegroundColor Yellow
Write-Host "==============="

$requiredDirs = @("src\main\java", "src\main\resources", "src\test\java", "docs", "scripts")

foreach ($dir in $requiredDirs) {
    if (Test-Path $dir) {
        Write-Host "✓ 目录存在: $dir" -ForegroundColor Green
    } else {
        Write-Host "✗ 目录缺失: $dir" -ForegroundColor Red
    }
}
Write-Host ""

# 7. 生成配置报告
Write-Host "7. 配置完整性报告" -ForegroundColor Yellow
Write-Host "=================="

$configScore = 0
$totalChecks = 10

# 检查各项配置
if (Test-Path "src\main\resources\application.yml") { $configScore++ }
if (Test-Path "src\main\java\com\instser\chat\config\CacheManagementConfig.java") { $configScore++ }
if (Test-Path "src\main\java\com\instser\chat\config\CacheManagementServerConfig.java") { $configScore++ }
if (Test-Path "src\main\java\com\instser\chat\controller\CacheManagementController.java") { $configScore++ }
if (Test-Path "src\test\java\com\instser\chat\config\CacheManagementAccessTest.java") { $configScore++ }
if (Test-Path "docs\07-24 13_13 缓存管理接口安全配置说明.md") { $configScore++ }
if (Test-Path "src\main\java") { $configScore++ }
if (Test-Path "src\test\java") { $configScore++ }
if (Test-Path "docs") { $configScore++ }
if (Test-Path "scripts") { $configScore++ }

$percentage = [math]::Round(($configScore / $totalChecks) * 100, 1)

Write-Host "配置完整性评分: $configScore/$totalChecks ($percentage%)" -ForegroundColor $(if ($percentage -ge 80) { "Green" } elseif ($percentage -ge 60) { "Yellow" } else { "Red" })

if ($percentage -ge 90) {
    Write-Host "✓ 配置完整，可以进行应用启动测试" -ForegroundColor Green
} elseif ($percentage -ge 70) {
    Write-Host "⚠ 配置基本完整，建议检查缺失项" -ForegroundColor Yellow
} else {
    Write-Host "✗ 配置不完整，需要补充缺失项" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== 配置测试完成 ===" -ForegroundColor Cyan
Write-Host "时间: $(Get-Date)" -ForegroundColor Gray
Write-Host ""
Write-Host "下一步:" -ForegroundColor Yellow
Write-Host "1. 如果配置完整，可以启动应用进行功能测试"
Write-Host "2. 启动命令: mvn spring-boot:run"
Write-Host "3. 启动后运行: .\scripts\verify-cache-management-security.ps1"
