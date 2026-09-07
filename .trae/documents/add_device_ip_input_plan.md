# 添加设备 IP 手动输入实施计划

## 需求
推送/验证时手动输入目标 Android 设备的 IP 地址，通过 WiFi ADB 连接设备。

## 仓库研究
当前 `config_tool.py` 的 `check_device()` 仅通过 `adb get-state` 检测 USB 连接的设备，不支持 WiFi ADB。需要增加 IP 输入 + `adb connect` 流程。

## 文件和模块
- `config_tool.py`：新增 `connect_device(ip)` 函数，修改 `check_device()` 支持 IP 连接

## 实施步骤
1. **新增 `connect_device(ip)` 函数**
   - 执行 `adb connect <ip>:5555`
   - 返回连接结果

2. **修改 `check_device()`**
   - 增加可选参数 `device_ip=None`
   - 若传入 IP，先执行 `adb connect` 再 `adb get-state`
   - 若未传 IP，保持原 USB 检测逻辑

3. **修改 `menu_push()` 和 `menu_verify()`**
   - 开头提示用户输入设备 IP（留空则使用 USB 连接）
   - 将 IP 传给 `check_device()`

4. **验证**
   - Python 语法检查
   - 推送后重新构建 exe

## 风险
- 设备未开启无线调试：`adb connect` 会失败，提示用户开启 USB 调试并执行 `adb tcpip 5555`
