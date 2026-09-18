import serial
import serial.tools.list_ports
import time
import random
import platform


def calculate_xor(data):
    result = 0
    for byte in data:
        result ^= byte
    return result


def generate_packet(prefix, value):
    data = list(prefix)
    data.append(value)
    checksum = calculate_xor(data)
    data.append(checksum)
    return bytes(data)


def send_to_serial(port, baudrate=9600, interval=10.0, screen_id=1):
    try:
        ser = serial.Serial(port, baudrate, timeout=1)
        print(f"串口已打开: {port}, 波特率: {baudrate}")
        print(f"目标屏号(ID): {screen_id}")
        print(f"发送间隔: {interval}秒")

        prefix1 = [0x57, 0x4B, 0x4C, 0x59, 0x09, screen_id, 0x88]
        values1 = list(range(1, 128))
        random.shuffle(values1)

        print("\n=== 第一组数据发送 ===")
        for i, value in enumerate(values1, 1):
            packet = generate_packet(prefix1, value)
            ser.write(packet)
            hex_str = " ".join(f"{byte:02X}" for byte in packet)
            print(f"[{time.strftime('%H:%M:%S')}] 第{i}/127组: {hex_str}")
            time.sleep(interval)

        print("\n第一组数据发送完成！")

        prefix2 = [0x57, 0x4B, 0x4C, 0x59, 0x09, screen_id, 0x89]
        values2 = list(range(1, 128))

        print("\n=== 第二组数据发送 ===")
        for i, value in enumerate(values2, 1):
            packet = generate_packet(prefix2, value)
            ser.write(packet)
            hex_str = " ".join(f"{byte:02X}" for byte in packet)
            print(f"[{time.strftime('%H:%M:%S')}] 第{i}/127组: {hex_str}")
            time.sleep(interval)

        print("\n第二组数据发送完成！")

    except serial.SerialException as e:
        print(f"串口错误: {e}")
        print("请检查串口是否存在或被其他程序占用")
    except KeyboardInterrupt:
        print("\n程序已停止")
    finally:
        if 'ser' in locals() and ser.is_open:
            ser.close()
            print("串口已关闭")


def list_serial_ports():
    """列出所有可用串口"""
    ports = list(serial.tools.list_ports.comports())
    return ports


def select_serial_port():
    """交互式选择串口"""
    ports = list_serial_ports()

    if not ports:
        print("未检测到任何串口！")
        print("请检查 USB 转串口线是否已连接，或驱动是否安装。")
        return None

    print("\n=== 可用串口列表 ===")
    for i, port in enumerate(ports, 1):
        desc = port.description if port.description else "未知设备"
        print(f"  [{i}] {port.device} - {desc}")

    default = 1
    print(f"\n请选择串口编号（直接回车默认 [{default}] {ports[0].device}）:")

    try:
        choice = input("> ").strip()
        if choice == "":
            idx = default - 1
        else:
            idx = int(choice) - 1
            if idx < 0 or idx >= len(ports):
                print(f"选择无效，使用默认: {ports[0].device}")
                idx = 0
    except (ValueError, EOFError):
        idx = 0

    selected = ports[idx].device
    print(f"已选择: {selected}")
    return selected


def select_screen_id():
    """交互式输入目标屏号 ID（1-255，默认1），须与设备 LED_CONFIG.txt 的 _ID 一致"""
    default_id = 1
    print(f"\n请输入目标屏号 ID（1-255，直接回车默认 {default_id}）:")
    print("提示: ID 必须与设备配置 LED_CONFIG.txt 中的 _ID 一致，否则屏幕不响应")
    while True:
        try:
            choice = input("> ").strip()
            if choice == "":
                screen_id = default_id
            else:
                screen_id = int(choice)
            if 1 <= screen_id <= 255:
                print(f"已选择屏号: {screen_id}")
                return screen_id
            print("ID 超出范围，请输入 1-255 之间的数字")
        except ValueError:
            print("输入无效，请输入数字")
        except EOFError:
            print(f"未输入，使用默认屏号: {default_id}")
            return default_id


if __name__ == "__main__":
    selected_port = select_serial_port()
    if selected_port:
        selected_id = select_screen_id()
        send_to_serial(selected_port, baudrate=9600, interval=10.0, screen_id=selected_id)
    else:
        print("程序退出。")
        try:
            input("按回车键关闭...")
        except EOFError:
            pass