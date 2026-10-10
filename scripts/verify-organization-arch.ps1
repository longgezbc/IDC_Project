# 组织架构升级功能验证脚本（PowerShell 包装）
# 用法：.\scripts\verify-organization-arch.ps1

# 切换到 UTF-8 代码页，避免中文输出乱码
chcp 65001 | Out-Null

$ErrorActionPreference = 'Stop'

Write-Host '═══════════════════════════════════════════════'
Write-Host '  组织架构升级功能验证（4 阶段）'
Write-Host '  前置：1) 已执行 V20260705 迁移脚本'
Write-Host '        2) 后端服务已启动（localhost:8080）'
Write-Host '        3) 测试账号：admin/pm_zhangming/engineer_chenqianlong（密码 admin123）'
Write-Host '═══════════════════════════════════════════════'
Write-Host ''

# 调用 Node.js 脚本
$scriptPath = Join-Path $PSScriptRoot 'verify-organization-arch.js'
if (-not (Test-Path $scriptPath)) {
    Write-Host "✗ 找不到测试脚本: $scriptPath" -ForegroundColor Red
    exit 1
}

node $scriptPath
$exitCode = $LASTEXITCODE

if ($exitCode -eq 0) {
    Write-Host ''
    Write-Host '✓ 所有测试通过' -ForegroundColor Green
} else {
    Write-Host ''
    Write-Host '✗ 测试失败，请查看上方输出' -ForegroundColor Red
}

exit $exitCode
