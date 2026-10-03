#!/usr/bin/env pwsh
<#
.SYNOPSIS
    把 graph-lib 推送到 GitHub(可选:用 PAT 自动创建远程仓库)。

.DESCRIPTION
    这个脚本解决最常见的坑:"先 push 后建仓库"—— 远程仓库不存在时,
    git 会报 `remote: Repository not found.`,而它其实是"仓库没建"而不是网络问题。

    两种用法:

    1) 不带 Token:只做检查 + 推送(前提是你已经在网页上建好了仓库)
         pwsh scripts/push-to-github.ps1

    2) 带 Token:脚本先调 GitHub API 建仓库,再推送(最省事)
         $env:GITHUB_TOKEN = "ghp_xxxxxxxx"     # 需要 repo 权限的 PAT
         pwsh scripts/push-to-github.ps1

    想用 SSH 而不是 HTTPS:
         pwsh scripts/push-to-github.ps1 -Ssh

.NOTES
    PAT 只在本次运行中使用,不会写入任何文件。建好的仓库默认是 public。
#>
param(
    [string]$Owner = "softwind-wf",
    [string]$Repo = "graph-lib",
    [string]$Description = "图算法库:46 个类覆盖遍历/MST/最短路/SCC/网络流与匹配/全局最小割/指派问题,669 个测试,零运行时依赖",
    [string]$Token = $env:GITHUB_TOKEN,
    [switch]$Ssh,
    [switch]$Private
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Info($m) { Write-Host "  $m" }
function Ok($m) { Write-Host "  [OK] $m" -ForegroundColor Green }
function Warn($m) { Write-Host "  [!] $m" -ForegroundColor Yellow }
function Fail($m) { Write-Host "  [X] $m" -ForegroundColor Red }

Write-Host "== graph-lib 推送到 GitHub =="
Info "仓库目录: $root"
Info "目标: $Owner/$Repo"

# ---- 1. 本地状态检查 -------------------------------------------------------
if (-not (Test-Path ".git")) { Fail "这里不是 git 仓库(缺少 .git);先执行 git init"; exit 1 }
$branch = git rev-parse --abbrev-ref HEAD
$commits = git rev-list --count HEAD
Ok "本地分支 $branch,共 $commits 个提交"
if ((git status --short | Measure-Object).Count -gt 0) {
    Warn "有未提交的改动,它们不会被推送(先 git add / git commit)"
}

# ---- 2. 可选:用 API 建远程仓库 --------------------------------------------
$httpsUrl = "https://github.com/$Owner/$Repo.git"
$sshUrl = "git@github.com:$Owner/$Repo.git"
$remoteUrl = if ($Ssh) { $sshUrl } else { $httpsUrl }

if ($Token) {
    Write-Host "== 用 GitHub API 创建仓库(如果已存在会跳过)=="
    $headers = @{
        Authorization = "Bearer $Token"
        Accept        = "application/vnd.github+json"
        "User-Agent"  = "graph-lib-push-script"
    }
    $body = @{ name = $Repo; description = $Description; private = [bool]$Private } | ConvertTo-Json
    try {
        $created = Invoke-RestMethod -Method Post -Uri "https://api.github.com/user/repos" `
            -Headers $headers -Body $body -ContentType "application/json"
        Ok "已创建: $($created.html_url)($($created.visibility))"
    }
    catch {
        $status = $_.Exception.Response.StatusCode.value__
        if ($status -eq 422) { Warn "仓库 $Owner/$Repo 已存在,继续推送" }
        else { Fail "创建仓库失败(HTTP $status):$($_.Exception.Message)"; exit 1 }
    }
}

# ---- 3. 配置 remote -------------------------------------------------------
$existing = git remote
if ($existing -contains "origin") {
    git remote set-url origin $remoteUrl
    Ok "已更新 origin → $remoteUrl"
}
else {
    git remote add origin $remoteUrl
    Ok "已添加 origin → $remoteUrl"
}

# ---- 4. 预检:远程仓库是否存在(能读到引用就说明存在且有权限) ----------------
Write-Host "== 远程仓库可达性检查 =="
$env:GIT_TERMINAL_PROMPT = "0"
$ls = git ls-remote --heads origin 2>&1
if ($LASTEXITCODE -ne 0) {
    Fail "读不到远程仓库:$ls"
    Write-Host ""
    Write-Host "  三种常见原因与处理:" -ForegroundColor Yellow
    Write-Host "  1) 仓库还没建 → 到 https://github.com/new 建一个名叫 $Repo 的仓库"
    Write-Host "     (Owner 选 $Owner;不要勾 Add README/.gitignore/license,以免和本地提交冲突)"
    Write-Host "     或者带 PAT 重跑本脚本:  `$env:GITHUB_TOKEN='ghp_xxx'; pwsh scripts/push-to-github.ps1"
    Write-Host "  2) 仓库是私有的而当前凭据无权访问 → 用有权限的账号登录:"
    Write-Host "     cmdkey /delete:git:https://github.com    # 清掉旧凭据,下次推送会重新登录"
    Write-Host "  3) 账号/仓库名拼错 → git remote -v 核对,或用 -Owner/-Repo 参数重跑"
    exit 1
}
Ok "远程仓库存在且可访问"

# ---- 5. 推送 ---------------------------------------------------------------
Write-Host "== 推送 =="
git push -u origin $branch
if ($LASTEXITCODE -ne 0) {
    Fail "推送失败。若提示权限相关,清掉旧凭据后重试:cmdkey /delete:git:https://github.com"
    exit 1
}
Ok "推送完成: https://github.com/$Owner/$Repo"
Write-Host ""
Write-Host "接下来(发布到 Maven 中央仓库):" -ForegroundColor Cyan
Write-Host "  1. 在 https://central.sonatype.com 用 GitHub 账号验证命名空间 io.github.$Owner"
Write-Host "  2. gpg --gen-key 生成密钥,并把公钥传到 keyserver"
Write-Host "  3. `$env:GPG_KEYNAME='密钥ID'; `$env:GPG_PASSPHRASE='口令'; mvn -Prelease clean deploy \"-DaltDeploymentRepository=ossrh::default::https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/\""
Write-Host "  4. 回到 Central Portal 点 Publish"
