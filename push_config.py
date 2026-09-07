#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LED 配置文件 ADB 推送工具（稳定版）
解决 ADB 推送配置文件时数据不稳定的问题

核心策略：
1. 原子写入：先推到 /sdcard/LED_CONFIG.txt.tmp，再 mv 成正式文件
2. 重试机制：ADB 命令失败自动重试 3 次
3. 完整性校验：推送后比对设备端文件大小
4. 连接保活：每次操作前检查设备连接
"""

import subprocess
import sys
import time
import os

CONFIG_FILE = "LED_CONFIG.txt"
DEVICE_PATH = "/sdcard/" + CONFIG_FILE
TEMP_PATH = "/sdcard/" + CONFIG_FILE + ".tmp"
MAX_RETRY = 3
RETRY_INTERVAL = 1  # 秒


def run_adb(args, timeout=30):
    """执行 ADB 命令，带重试"""
    cmd = ["adb"] + args
    last_err = None

    for attempt in range(1, MAX_RETRY + 1):
        try:
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
            if result.returncode == 0:
                return result.stdout.strip(), True
            last_err = result.stderr.strip() or result.stdout.strip()
            print(f"  [重试 {attempt}/{MAX_RETRY}] ADB 命令失败: {last_err}")
        except subprocess.TimeoutExpired:
            last_err = "命令超时"
            print(f"  [重试 {attempt}/{MAX_RETRY}] ADB 命令超时")
        except FileNotFoundError:
            print("  [错误] 未找到 adb，请安装 Android SDK 并配置 PATH")
            sys.exit(1)

        if attempt < MAX_RETRY:
            time.sleep(RETRY_INTERVAL)

    return last_err, False


def check_device():
    """检查设备是否连接"""
    print("[1/4] 检查设备连接...")
    output, ok = run_adb(["get-state"])
    if ok and "device" in output:
        print(f"  ✓ 设备已连接: {output}")
        return True
    # 尝试重启 adb server
    print("  设备未连接，尝试重启 ADB server...")
    subprocess.run(["adb", "kill-server"], capture_output=True)
    time.sleep(2)
    subprocess.run(["adb", "start-server"], capture_output=True)
    time.sleep(2)
    output, ok = run_adb(["get-state"])
    if ok and "device" in output:
        print(f"  ✓ 设备已重新连接: {output}")
        return True
    print("  ✗ 未检测到设备，请检查 USB 连接或 WiFi ADB")
    return False


def get_local_size(filepath):
    """获取本地文件大小"""
    return os.path.getsize(filepath)


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
    """推送配置文件（原子写入）"""
    if not os.path.exists(local_file):
        print(f"  [错误] 本地文件不存在: {local_file}")
        return False

    local_size = get_local_size(local_file)
    print(f"[2/4] 本地配置文件大小: {local_size} 字节")

    # 步骤1：推送到临时文件
    print(f"[3/4] 推送到临时文件 {TEMP_PATH} ...")
    _, ok = run_adb(["push", local_file, TEMP_PATH])
    if not ok:
        print("  ✗ 推送临时文件失败")
        return False

    # 校验临时文件大小
    temp_size = get_device_size(TEMP_PATH)
    if temp_size != local_size:
        print(f"  ✗ 临时文件大小不匹配 (期望 {local_size}, 实际 {temp_size})")
        run_adb(["shell", "rm", TEMP_PATH])  # 清理
        return False
    print(f"  ✓ 临时文件推送成功 ({temp_size} 字节)")

    # 步骤2：原子重命名（mv 是原子操作，避免读到半写文件）
    print(f"[4/4] 原子重命名为 {DEVICE_PATH} ...")
    _, ok = run_adb(["shell", "mv", "-f", TEMP_PATH, DEVICE_PATH])
    if not ok:
        print("  ✗ 重命名失败")
        run_adb(["shell", "rm", TEMP_PATH])
        return False

    # 最终校验
    time.sleep(0.3)  # 等待文件系统同步
    final_size = get_device_size(DEVICE_PATH)
    if final_size != local_size:
        print(f"  ✗ 最终文件大小不匹配 (期望 {local_size}, 实际 {final_size})")
        return False

    print(f"  ✓ 配置推送成功! ({final_size} 字节)")
    return True


def main():
    if len(sys.argv) < 2:
        print("用法: python push_config.py <LED_CONFIG.txt 路径>")
        print("示例: python push_config.py ./LED_CONFIG.txt")
        sys.exit(1)

    local_file = sys.argv[1]
    print(f"=== LED 配置推送工具 ===")
    print(f"目标配置: {local_file}")
    print()

    if not check_device():
        sys.exit(1)

    success = push_config(local_file)
    print()
    if success:
        print("=== 推送完成 ===")
        sys.exit(0)
    else:
        print("=== 推送失败，请重试 ===")
        sys.exit(1)


if __name__ == "__main__":
    main()
