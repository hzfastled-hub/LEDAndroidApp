@echo off
chcp 65001 >nul
REM LED 屏配置工具 - Windows 构建脚本
REM 使用 PyInstaller 将 config_tool.py 打包为独立 exe
REM 前提：已安装 Python 3.8+ 且 pip 可用

setlocal

echo ========================================
echo   LED Config Tool - Windows Build
echo ========================================
echo.

REM 步骤1：检查 Python
echo [1/4] 检查 Python 环境...
python --version >nul 2>&1
if errorlevel 1 (
    echo   [错误] 未找到 Python，请先安装 Python 3.8+
    echo   下载地址: https://www.python.org/downloads/
    pause
    exit /b 1
)
for /f "tokens=*" %%i in ('python --version 2^>^&1') do set PYVER=%%i
echo   ✓ %PYVER%

REM 步骤2：安装 PyInstaller
echo [2/4] 检查/安装 PyInstaller...
python -m PyInstaller --version >nul 2>&1
if errorlevel 1 (
    echo   正在安装 PyInstaller...
    python -m pip install pyinstaller
    if errorlevel 1 (
        echo   [错误] PyInstaller 安装失败
        pause
        exit /b 1
    )
)
echo   ✓ PyInstaller 已就绪

REM 步骤3：打包
echo [3/4] 正在打包 config_tool.py ...
python -m PyInstaller -F -n LEDConfigTool --clean config_tool.py
if errorlevel 1 (
    echo   [错误] 打包失败，请检查错误信息
    pause
    exit /b 1
)

REM 步骤4：完成
echo [4/4] 构建完成
echo.
echo ========================================
echo   构建成功！
echo   输出文件: dist\LEDConfigTool.exe
echo ========================================
echo.
echo 使用方法:
echo   1. 双击 dist\LEDConfigTool.exe 运行
echo   2. 连接 Android 设备（USB 调试模式）
echo   3. 按菜单提示生成配置并推送
echo.
pause
endlocal
