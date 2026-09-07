#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LED 屏配置工具（Windows 版）
功能：
  1. 推送 LED_CONFIG.txt 到 Android 设备（原子写入 + 重试 + 校验）
  2. 从设备读取并验证配置

注意：LED_CONFIG.txt 需手动创建，格式示例：
{
  "_SERIAL": "led_check_serial",
  "_MODE": 1,
  "_ID": "1",
  "_TITLE": "88",
  "_TITLE2": "AB",
  "_UpdateTime": 5,
  "_MAP": ["检修","临修","质检", ...]
}

打包 exe：
  pip install pyinstaller
  pyinstaller -F -n LEDConfigTool config_tool.py
"""

import json
import os
import subprocess
import sys
import time

# ==================== 常量 ====================
CONFIG_FILE = "LED_CONFIG.txt"
DEVICE_PATH = "/sdcard/" + CONFIG_FILE
TEMP_PATH = "/sdcard/" + CONFIG_FILE + ".tmp"
SERIAL_FLAG = "led_check_serial"
MAX_RETRY = 3
RETRY_INTERVAL = 1  # 秒


# ==================== 配置校验 ====================
def load_config(filepath):
    """从本地文件加载配置"""
    if not os.path.exists(filepath):
        return None
    with open(filepath, "r", encoding="utf-8") as f:
        return json.load(f)


def validate_config(config):
    """校验配置完整性"""
    if not isinstance(config, dict):
        return False, "配置不是 JSON 对象"
    if config.get("_SERIAL") != SERIAL_FLAG:
        return False, "_SERIAL 标识不匹配"
    _map = config.get("_MAP")
    if not isinstance(_map, list) or len(_map) == 0:
        return False, "_MAP 为空"
    return True, f"校验通过（{len(_map)} 个班组）"


# ==================== ADB 操作 ====================
DEVICE_SERIAL = None  # 目标设备序列号，WiFi 连接时设为 IP:5555


def run_adb(args, timeout=30):
    """执行 ADB 命令，带重试。若指定了 DEVICE_SERIAL 则自动加 -s 参数"""
    global DEVICE_SERIAL
    cmd = ["adb"]
    if DEVICE_SERIAL:
        cmd += ["-s", DEVICE_SERIAL]
    cmd += args
    last_err = None
    for attempt in range(1, MAX_RETRY + 1):
        try:
            # 显式使用 UTF-8 解码，避免 Windows GBK 解码中文配置失败
            result = subprocess.run(cmd, capture_output=True, encoding='utf-8', errors='replace', timeout=timeout)
            stdout = (result.stdout or "").strip()
            stderr = (result.stderr or "").strip()
            if result.returncode == 0:
                return stdout, True
            last_err = stderr or stdout
            print(f"  [重试 {attempt}/{MAX_RETRY}] ADB 命令失败: {last_err}")
        except subprocess.TimeoutExpired:
            last_err = "命令超时"
            print(f"  [重试 {attempt}/{MAX_RETRY}] ADB 命令超时")
        except FileNotFoundError:
            print("  [错误] 未找到 adb，请安装 Android SDK platform-tools 并配置 PATH")
            return None, False
        if attempt < MAX_RETRY:
            time.sleep(RETRY_INTERVAL)
    return last_err, False


def connect_device(ip):
    """通过 WiFi ADB 连接设备"""
    global DEVICE_SERIAL
    serial = f"{ip}:5555"
    print(f"  正在连接 {serial} ...")
    # connect 命令不能带 -s，临时清空
    saved = DEVICE_SERIAL
    DEVICE_SERIAL = None
    output, ok = run_adb(["connect", serial])
    DEVICE_SERIAL = saved
    if ok and ("connected" in output.lower() or "already" in output.lower()):
        DEVICE_SERIAL = serial  # 固定操作此设备
        print(f"  OK WiFi ADB 连接成功: {output}")
        return True
    print(f"  X WiFi ADB 连接失败: {output}")
    print("  提示: 请确保设备已开启 USB 调试，且通过 USB 执行过 'adb tcpip 5555'")
    return False


def list_devices():
    """列出已连接的设备，返回序列号列表"""
    output, ok = run_adb(["devices"])
    if not ok:
        return []
    devices = []
    for line in output.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append(parts[0])
    return devices


def check_device(device_ip=None):
    """检查设备是否连接（支持 USB 和 WiFi ADB）"""
    global DEVICE_SERIAL
    DEVICE_SERIAL = None  # 每次检查前清空，避免上次残留
    print("[1/4] 检查设备连接...")

    # 如果指定了 IP，先走 WiFi ADB 连接
    if device_ip:
        if not connect_device(device_ip):
            return False
    else:
        # USB 连接：列出设备，自动选择第一个
        devices = list_devices()
        if len(devices) == 1:
            DEVICE_SERIAL = devices[0]
            print(f"  OK 设备已连接: {DEVICE_SERIAL}")
            return True
        elif len(devices) > 1:
            DEVICE_SERIAL = devices[0]
            print(f"  检测到多个设备，使用第一个: {DEVICE_SERIAL}")
            return True
        else:
            print("  未检测到 USB 设备，尝试重启 ADB server...")
            subprocess.run(["adb", "kill-server"], capture_output=True)
            time.sleep(2)
            subprocess.run(["adb", "start-server"], capture_output=True)
            time.sleep(2)
            devices = list_devices()
            if devices:
                DEVICE_SERIAL = devices[0]
                print(f"  OK 设备已重新连接: {DEVICE_SERIAL}")
                return True
            print("  X 未检测到 USB 设备，请检查连接或输入设备 IP 使用 WiFi ADB")
            return False

    # WiFi 连接后确认状态
    output, ok = run_adb(["get-state"])
    if ok and "device" in output:
        return True
    print("  X 设备状态异常")
    return False


def get_device_size(path):
    """获取设备端文件大小"""
    output, ok = run_adb(["shell", "stat", "-c", "%s", path])
    if ok:
        try:
            return int(output.strip())
        except ValueError:
            return -1
    return -1


def push_config(local_file):
    """推送配置文件（原子写入 + 校验）"""
    if not os.path.exists(local_file):
        print(f"  [错误] 本地文件不存在: {local_file}")
        return False

    local_size = os.path.getsize(local_file)
    print(f"[2/4] 本地配置文件大小: {local_size} 字节")

    # 步骤1：推送到临时文件
    print(f"[3/4] 推送到临时文件 {TEMP_PATH} ...")
    _, ok = run_adb(["push", local_file, TEMP_PATH])
    if not ok:
        print("  X 推送临时文件失败")
        return False

    # 校验临时文件大小
    temp_size = get_device_size(TEMP_PATH)
    if temp_size != local_size:
        print(f"  X 临时文件大小不匹配 (期望 {local_size}, 实际 {temp_size})")
        run_adb(["shell", "rm", TEMP_PATH])
        return False
    print(f"  OK 临时文件推送成功 ({temp_size} 字节)")

    # 步骤2：原子重命名
    print(f"[4/4] 原子重命名为 {DEVICE_PATH} ...")
    _, ok = run_adb(["shell", "mv", "-f", TEMP_PATH, DEVICE_PATH])
    if not ok:
        print("  X 重命名失败")
        run_adb(["shell", "rm", TEMP_PATH])
        return False

    # 最终校验
    time.sleep(0.3)
    final_size = get_device_size(DEVICE_PATH)
    if final_size != local_size:
        print(f"  X 最终文件大小不匹配 (期望 {local_size}, 实际 {final_size})")
        return False

    print(f"  OK 配置推送成功! ({final_size} 字节)")
    return True


def read_device_config():
    """从设备读取配置文件"""
    output, ok = run_adb(["shell", "cat", DEVICE_PATH])
    if not ok:
        print("  X 读取设备配置失败")
        return None
    try:
        return json.loads(output)
    except json.JSONDecodeError as e:
        print(f"  X 设备配置 JSON 解析失败: {e}")
        return None


# ==================== 交互菜单 ====================
def input_device_ip():
    """提示用户输入设备 IP（留空使用 USB）"""
    ip = input("请输入设备 IP（留空使用 USB 连接）: ").strip()
    return ip if ip else None


def menu_push():
    """推送配置到设备"""
    print("\n=== 推送配置到 Android 设备 ===")
    filepath = os.path.join(os.getcwd(), CONFIG_FILE)
    if not os.path.exists(filepath):
        print(f"  未找到 {CONFIG_FILE}")
        print(f"  请在程序同目录下手动创建 {CONFIG_FILE}（JSON 格式）")
        return

    config = load_config(filepath)
    ok, msg = validate_config(config)
    print(f"本地配置校验: {msg}")
    if not ok:
        print("  配置不合法，终止推送")
        return

    device_ip = input_device_ip()
    if not check_device(device_ip):
        return

    if push_config(filepath):
        print("\n=== 推送成功 ===")
    else:
        print("\n=== 推送失败，请重试 ===")


def menu_verify():
    """验证设备端配置"""
    print("\n=== 验证设备端配置 ===")
    device_ip = input_device_ip()
    if not check_device(device_ip):
        return

    config = read_device_config()
    if config is None:
        return

    ok, msg = validate_config(config)
    print(f"设备配置校验: {msg}")
    print(f"  模式: {config.get('_MODE')}")
    print(f"  屏号: {config.get('_ID')}")
    print(f"  标题1: {config.get('_TITLE')}")
    print(f"  标题2: {config.get('_TITLE2')}")
    print(f"  更新时间: {config.get('_UpdateTime')}秒")
    print(f"  班组数: {len(config.get('_MAP', []))}")


def main():
    while True:
        print("\n" + "=" * 40)
        print("  LED 屏配置工具")
        print("=" * 40)
        print("  2. 推送配置到 Android 设备")
        print("  3. 验证设备端配置")
        print("  0. 退出")
        print("-" * 40)

        choice = input("请选择 (0/2/3): ").strip()

        if choice == "2":
            menu_push()
        elif choice == "3":
            menu_verify()
        elif choice == "0":
            print("再见！")
            break
        else:
            print("无效选择，请重新输入")


def pause_exit():
    """程序结束前暂停，防止窗口立即关闭"""
    try:
        input("\n按回车键退出...")
    except (EOFError, OSError):
        pass


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n已取消")
    except EOFError:
        # 无标准输入时（如某些双击运行场景），提示后退出
        print("\n[错误] 无法读取输入，请在命令行窗口中运行本程序")
    except Exception as e:
        print(f"\n[程序异常] {type(e).__name__}: {e}")
    finally:
        pause_exit()
