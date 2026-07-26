@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

:: ==============================================
:: JDK 版本切换脚本
:: 支持 JDK 8 / 21 / 25 三版本循环切换
:: 系统环境变量: jdk-8, jdk-21, jdk-25
:: 请右键 → 以管理员身份运行
:: ==============================================

:: 获取当前JAVA_HOME的值
for /f "tokens=2*" %%A in ('reg query "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" /v JAVA_HOME 2^>nul') do (
    set "CURRENT_VALUE=%%B"
)

:: 去掉引号
set "CURRENT_VALUE=!CURRENT_VALUE:"=!"

echo 当前JAVA_HOME: !CURRENT_VALUE!
echo.

:: 判断当前是哪个版本，切换到下一个
echo !CURRENT_VALUE! | findstr /c:"jdk1.8" >nul
if %errorlevel% equ 0 (
    :: jdk-8 → jdk-21
    set "NEW_VALUE=%%jdk-21%%"
    set "NEW_NAME=JDK 21"
    goto :switch
)

echo !CURRENT_VALUE! | findstr /c:"jdk-21" >nul
if %errorlevel% equ 0 (
    :: jdk-21 → jdk-25
    set "NEW_VALUE=%%jdk-25%%"
    set "NEW_NAME=JDK 25"
    goto :switch
)

echo !CURRENT_VALUE! | findstr /c:"jdk-25" >nul
if %errorlevel% equ 0 (
    :: jdk-25 → jdk-8
    set "NEW_VALUE=%%jdk-8%%"
    set "NEW_NAME=JDK 8"
    goto :switch
)

:: 兜底：默认切到 jdk-21
set "NEW_VALUE=%%jdk-21%%"
set "NEW_NAME=JDK 21"

:switch
echo 正在切换到 !NEW_NAME! ...
setx JAVA_HOME "!NEW_VALUE!" /M >nul

if %errorlevel% equ 0 (
    echo.
    echo ✅ 切换成功！现在JAVA_HOME = !NEW_VALUE!
    echo    8  → 21 → 25 → 8  循环切换
    echo.
    echo ⚠️ 请关闭所有命令行和IDE，重新打开后生效
) else (
    echo.
    echo ❌ 切换失败！请右键点击脚本 → "以管理员身份运行"
)

echo.
pause
