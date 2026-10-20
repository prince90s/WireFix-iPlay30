@echo off
REM ============================================================
REM  WireFix 安装后自检（Windows / 电脑侧执行）
REM  重启完成后运行
REM ============================================================
setlocal
cd /d "%~dp0\.."

set ADB=adb
where adb >nul 2>&1
if errorlevel 1 (
    if exist "%USERPROFILE%\Downloads\gaojigjx_805084\adb.exe" (
        set ADB=%USERPROFILE%\Downloads\gaojigjx_805084\adb.exe
    )
)

echo ===== 1. 模块是否挂载 =====
%ADB% shell su -c "ls /data/adb/modules/wirefix"

echo.
echo ===== 2. APK 安装路径（应为 /system/priv-app/WireFix）=====
%ADB% shell pm path com.abnen.wirefix

echo.
echo ===== 3. 关键权限是否授予 =====
%ADB% shell dumpsys package com.abnen.wirefix | findstr /C:"MODIFY_AUDIO_ROUTING"

echo.
echo ===== 4. ACCDET 耳机状态（2 = 已插入）=====
%ADB% shell su -c "cat /sys/bus/platform/drivers/Accdet_Driver/state"

echo.
echo ===== 5. AudioService 是否收到插入事件 =====
%ADB% shell dumpsys audio | findstr /C:"setWiredDeviceConnectionState"

echo.
echo ===== 6. 当前音频输出设备（应出现 headphone）=====
%ADB% shell dumpsys audio | findstr /C:"Devices:"

echo.
echo 判定：第5项有 setWiredDeviceConnectionState(type:8 ... AVAILABLE) = 修复生效
pause
