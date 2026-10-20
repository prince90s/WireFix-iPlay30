@echo off
REM ============================================================
REM  WireFix 一键安装（Windows / 电脑侧执行）
REM  前提：adb 已连接设备，设备已 root（Magisk）
REM ============================================================
setlocal
cd /d "%~dp0\.."

set ADB=adb
where adb >nul 2>&1
if errorlevel 1 (
    if exist "%USERPROFILE%\Downloads\gaojigjx_805084\adb.exe" (
        set ADB=%USERPROFILE%\Downloads\gaojigjx_805084\adb.exe
    ) else (
        echo [X] 找不到 adb，请把 adb 加入 PATH 或修改本脚本的 ADB 变量
        pause & exit /b 1
    )
)

echo ==^> 检查设备
%ADB% devices
%ADB% shell echo ok >nul 2>&1
if errorlevel 1 (
    echo [X] 设备未连接
    pause & exit /b 1
)

echo ==^> 切换到 root
%ADB% root
timeout /t 3 >nul

echo ==^> 推送工具包
%ADB% push . /data/local/tmp/WireFix-kit

echo ==^> 执行安装脚本
%ADB% shell su -c "sh /data/local/tmp/WireFix-kit/scripts/install.sh"

echo ==^> 重启设备
%ADB% reboot

echo.
echo 重启完成后请运行 scripts\verify.bat 自检
pause
