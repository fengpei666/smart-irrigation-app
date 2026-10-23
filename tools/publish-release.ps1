<#
.SYNOPSIS
    一键发版：编译 release APK → 在 GitHub 上建 Release → 上传 APK。

.DESCRIPTION
    App 里的「检查更新」查的就是本脚本发出来的 Release（/releases/latest），
    所以发完这一条，所有装了 App 的人打开就能看到新版本并自助升级。

    发版前请先把 app/build.gradle 里的 versionName 和 versionCode 都 +1，
    脚本会校验两者与 -Version 是否一致，避免出现「发了新包但版本号没变，
    用户端永远提示已是最新」这种坑。

.EXAMPLE
    # 最常用：版本号已改成 1.1 后
    .\tools\publish-release.ps1 -Version 1.1 -Notes "修复首页轮询偶发卡顿；新增在线更新"

.EXAMPLE
    # 说明写长了，用文件
    .\tools\publish-release.ps1 -Version 1.1 -NotesFile .\release-notes.md
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [string]$Notes = "",
    [string]$NotesFile = "",

    # 不给就从环境变量 GITHUB_TOKEN 取，再没有就交互式输入
    [string]$Token = "",

    [string]$Repo = "fengpei666/smart-irrigation-app",

    # 只建草稿，不直接对用户可见
    [switch]$Draft,

    # 已经 build 过、跳过一次编译
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Fail([string]$message) {
    Write-Host "✗ $message" -ForegroundColor Red
    exit 1
}

# ---------- 1. 校验版本号 ----------
$gradlePath = Join-Path $root "app\build.gradle"
$gradleText = Get-Content $gradlePath -Raw
$nameMatch = [regex]::Match($gradleText, "versionName\s+'([^']+)'")
$codeMatch = [regex]::Match($gradleText, "versionCode\s+(\d+)")
if (-not $nameMatch.Success -or -not $codeMatch.Success) {
    Fail "没在 app/build.gradle 里找到 versionName / versionCode"
}
$gradleVersion = $nameMatch.Groups[1].Value
$gradleCode = $codeMatch.Groups[1].Value
if ($gradleVersion -ne $Version) {
    Fail "版本号不一致：build.gradle 里是 $gradleVersion，参数是 $Version。先改 build.gradle 再发版。"
}
Write-Host "✓ 版本 v$Version（versionCode $gradleCode）" -ForegroundColor Green

# ---------- 2. 取 token ----------
if (-not $Token) {
    if ($env:GITHUB_TOKEN) {
        $Token = $env:GITHUB_TOKEN
    } else {
        $secure = Read-Host "粘贴 GitHub Token（需 repo 权限，输入不回显）" -AsSecureString
        $Token = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
            [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
    }
}
if (-not $Token) { Fail "没有拿到 Token" }
$headers = @{
    Authorization          = "token $Token"
    Accept                 = "application/vnd.github+json"
    "User-Agent"           = "smart-irrigation-release"
    "X-GitHub-Api-Version" = "2022-11-28"
}

# ---------- 3. 编译 ----------
$apkPath = Join-Path $root "app\build\outputs\apk\release\app-release.apk"
if (-not $SkipBuild) {
    Write-Host "… 编译 release APK"
    # 跳过 lintVital：它需要联网拉 lint-gradle，离线环境下会整包构建失败，
    # 而这里只关心能不能出可安装的包
    & (Join-Path $root "gradlew.bat") assembleRelease --console=plain `
        -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease
    if ($LASTEXITCODE -ne 0) { Fail "Gradle 编译失败（退出码 $LASTEXITCODE）" }
}
if (-not (Test-Path $apkPath)) { Fail "没找到 APK：$apkPath" }
$apkSizeMb = [Math]::Round((Get-Item $apkPath).Length / 1MB, 2)
Write-Host "✓ APK 就绪：$apkPath（$apkSizeMb MB）" -ForegroundColor Green

# ---------- 4. 整理 Release 说明 ----------
if ($NotesFile) {
    if (-not (Test-Path $NotesFile)) { Fail "说明文件不存在：$NotesFile" }
    $Notes = Get-Content $NotesFile -Raw
}
if (-not $Notes -or $Notes.Trim().Length -eq 0) {
    $Notes = "v$Version（$([DateTime]::Now.ToString('yyyy-MM-dd')) 构建）"
}
$commit = (& git rev-parse --short HEAD) 2>$null
$body = @"
$Notes

---
- 版本：v$Version（versionCode $gradleCode）
- 构建提交：$commit
- 安装包：app-release.apk（$apkSizeMb MB）

> 手机端打开 App → 设置 → 检查更新，即可自动下载安装；也可直接下载页面底部 APK。
"@

# ---------- 5. 建 Release ----------
Write-Host "… 在 GitHub 创建 Release v$Version"
$payload = @{
    tag_name         = "v$Version"
    name             = "v$Version"
    body             = $body
    draft            = [bool]$Draft
    prerelease       = $false
    target_commitish = "main"
} | ConvertTo-Json -Depth 4

try {
    $release = Invoke-RestMethod -Method Post `
        -Uri "https://api.github.com/repos/$Repo/releases" `
        -Headers $headers -Body ([Text.Encoding]::UTF8.GetBytes($payload)) `
        -ContentType "application/json; charset=utf-8" -TimeoutSec 60
} catch {
    $detail = $_.Exception.Message
    if ($_.Exception.Response) {
        $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
        $detail = $reader.ReadToEnd()
    }
    Fail "创建 Release 失败（tag v$Version 已存在？）：$detail"
}
Write-Host "✓ Release 已创建：$($release.html_url)" -ForegroundColor Green

# ---------- 6. 上传 APK ----------
$assetName = "smart-irrigation-v$Version.apk"
Write-Host "… 上传 $assetName"
try {
    $asset = Invoke-RestMethod -Method Post `
        -Uri "https://uploads.github.com/repos/$Repo/releases/$($release.id)/assets?name=$assetName" `
        -Headers $headers -ContentType "application/vnd.android.package-archive" `
        -InFile $apkPath -TimeoutSec 600
    Write-Host "✓ APK 已上传（$([Math]::Round($asset.size / 1MB, 2)) MB）" -ForegroundColor Green
} catch {
    Write-Host "! Release 建好了但 APK 上传失败：$($_.Exception.Message)" -ForegroundColor Yellow
    Write-Host "  可手动拖到 $($release.html_url) 补传" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "发版完成 🎉" -ForegroundColor Cyan
Write-Host "发布页：$($release.html_url)"
Write-Host "App 内路径：设置 → 检查更新（或重启 App 自动提示）"
