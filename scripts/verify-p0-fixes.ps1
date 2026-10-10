# P0 修复验证脚本（PowerShell 包装）
# 自动切换 UTF-8 代码页，解决中文乱码，然后运行 Node 脚本
#
# 运行方式：
#   .\scripts\verify-p0-fixes.ps1
#
# 若 PowerShell 执行策略限制，可临时绕过：
#   powershell -ExecutionPolicy Bypass -File .\scripts\verify-p0-fixes.ps1

chcp 65001 | Out-Null
node "$PSScriptRoot\verify-p0-fixes.js"
exit $LASTEXITCODE
