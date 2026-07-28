@echo off
chcp 65001 >nul
setlocal
cd /d "E:\AI\hermes-workspace\bookkeeper"

echo ========================================
echo   记账单 - 推送到 GitHub
echo ========================================
echo.

set "HTTPS_PROXY=http://127.0.0.1:7890"
set "HTTP_PROXY=http://127.0.0.1:7890"
set "GIT_SSH_COMMAND=ssh -o ProxyCommand=connect -H 127.0.0.1:7890 %%h %%p -i %USERPROFILE%\.ssh\id_ed25519 -o StrictHostKeyChecking=no"

echo [1/4] 登录 GitHub CLI（浏览器会打开，按提示授权）...
gh auth status >nul 2>&1
if errorlevel 1 (
  gh auth login --hostname github.com --git-protocol ssh --web
  if errorlevel 1 (
    echo 登录失败，请重试
    pause
    exit /b 1
  )
)

echo [2/4] 创建远程仓库 BuZhiJvDou/bookkeeper ...
gh repo view BuZhiJvDou/bookkeeper >nul 2>&1
if errorlevel 1 (
  gh repo create bookkeeper --public --source=. --remote=origin --description "记账单 - Android + Desktop 多端记账应用" --push
  if errorlevel 1 (
    echo 创建失败，尝试仅创建再推送...
    gh repo create bookkeeper --public --description "记账单 - Android + Desktop 多端记账应用"
    git remote remove origin 2>nul
    git remote add origin git@github.com:BuZhiJvDou/bookkeeper.git
    git branch -M main
    git push -u origin main
  )
) else (
  echo 仓库已存在，直接推送...
  git remote remove origin 2>nul
  git remote add origin git@github.com:BuZhiJvDou/bookkeeper.git
  git branch -M main
  git push -u origin main
)

echo.
echo [3/4] 完成！
echo 仓库地址: https://github.com/BuZhiJvDou/bookkeeper
echo.
start https://github.com/BuZhiJvDou/bookkeeper
pause
