# PowerShell启动脚本 - 解决中文乱码问题

# 设置控制台编码为UTF-8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding = [System.Text.Encoding]::UTF8

# 设置环境变量
$env:JAVA_OPTS = "-Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai -Dconsole.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"

Write-Host "正在启动 Jinyu Chat 应用..." -ForegroundColor Green
Write-Host "控制台编码已设置为 UTF-8" -ForegroundColor Yellow

# 启动Spring Boot应用
mvn spring-boot:run

Read-Host "按任意键退出"
