@echo off
chcp 65001 >nul
REM LED 配置文件 ADB 推送工具（Windows 版，原子写入 + 重试）
REM 用法: push_config.bat LED_CONFIG.txt

setlocal

set "CONFIG_FILE=%~1"
set "DEVICE_PATH=/sdcard/LED_CONFIG.txt"
set "TEMP_PATH=/sdcard/LED_CONFIG.txt.tmp"
set "MAX_RETRY=3"

if "%CONFIG_FILE%"=="" (
    echo 用法: push_config.bat ^<LED_CONFIG.txt 路径^>
    pause
    exit /b 1
)

if not exist "%CONFIG_FILE%" (
    echo [错误] 本地文件不存在: %CONFIG_FILE%
    pause
    exit /b 1
)

echo === LED 配置推送工具 ===
echo 目标配置: %CONFIG_FILE%
echo.

REM 步骤1：检查设备
echo [1/4] 检查设备连接...
adb get-state >nul 2>&1
if errorlevel 1 (
    echo   设备未连接，尝试重启 ADB server...
    adb kill-server >nul 2>&1
    timeout /t 2 /nobreak >nul
    adb start-server >nul 2>&1
    timeout /t 2 /nobreak >nul
)
adb get-state >nul 2>&1
if errorlevel 1 (
    echo   [错误] 未检测到设备，请检查 USB 连接
    pause
    exit /b 1
)
echo   ✓ 设备已连接

REM 步骤2：推送到临时文件（重试3次）
echo [2/4] 推送到临时文件...
set "PUSH_OK=0"
for /l %%i in (1,1,%MAX_RETRY%) do (
    adb push "%CONFIG_FILE%" "%TEMP_PATH%" >nul 2>&1
    if not errorlevel 1 (
        set "PUSH_OK=1"
        goto :push_done
    )
    echo   重试 %%i/%MAX_RETRY%...
    timeout /t 1 /nobreak >nul
)
:push_done
if "%PUSH_OK%"=="0" (
    echo   [错误] 推送临时文件失败
    pause
    exit /b 1
)
echo   ✓ 临时文件推送成功

REM 步骤3：原子重命名
echo [3/4] 原子重命名为正式文件...
adb shell "mv -f %TEMP_PATH% %DEVICE_PATH%" >nul 2>&1
if errorlevel 1 (
    echo   [错误] 重命名失败
    adb shell "rm %TEMP_PATH%" >nul 2>&1
    pause
    exit /b 1
)
echo   ✓ 原子重命名完成

REM 步骤4：等待同步
echo [4/4] 等待文件系统同步...
timeout /t 1 /nobreak >nul
echo   ✓ 推送完成

echo.
echo === 配置推送成功 ===
pause
endlocal
