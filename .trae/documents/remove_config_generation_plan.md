# 移除配置生成功能实施计划

## 需求
用户要求 LED_CONFIG.txt 由手动创建，不需要在程序里构建。程序只保留：
- 推送配置到 Android 设备
- 验证设备端配置

## 仓库研究
当前 `config_tool.py`（313 行）包含 4 个菜单功能：
1. 生成配置文件（`menu_generate` → `generate_config` + `save_config` + `DEFAULT_MAP`）
2. 推送配置到设备（`menu_push`）
3. 验证设备端配置（`menu_verify`）
4. 一键生成+推送（`menu_full` → 调用 `menu_generate` + `menu_push`）

其中菜单 1 和 4 依赖配置生成逻辑，`menu_push` 在文件不存在时会 fallback 到 `menu_generate`。

## 文件和模块
- `config_tool.py`：移除生成相关代码，精简菜单
- `.github/workflows/build_config.yml`：无需修改（push 后自动触发重建）
- `build_config.bat`：无需修改

## 实施步骤
1. **移除配置生成相关函数和常量**
   - 删除 `DEFAULT_MAP` 列表（47 班组默认值）
   - 删除 `generate_config()` 函数
   - 删除 `save_config()` 函数
   - 删除 `input_default()` 辅助函数

2. **移除生成相关菜单**
   - 删除 `menu_generate()` 函数
   - 删除 `menu_full()` 函数

3. **修改 `menu_push()`**
   - 文件不存在时不再调用 `menu_generate()`，改为提示用户手动创建 LED_CONFIG.txt
   - 保留 `load_config()` + `validate_config()` 校验逻辑

4. **更新 `main()` 菜单**
   - 移除选项 1（生成配置）和 4（一键生成+推送）
   - 保留选项 2（推送）、3（验证）、0（退出）
   - 提示语调整为 "请选择 (0/2/3)"

5. **更新文件头部 docstring**
   - 功能描述改为仅推送 + 验证
   - 注明 LED_CONFIG.txt 需手动创建

6. **保留的函数**
   - `load_config()`、`validate_config()`（推送前校验本地文件）
   - `run_adb()`、`check_device()`、`get_device_size()`、`push_config()`、`read_device_config()`
   - `json` 模块仍需保留（load_config / validate_config / read_device_config 使用）

7. **重新构建 exe**
   - 提交修改到 GitHub，触发 Actions 重新构建
   - 下载新的 LEDConfigTool.exe

## 依赖和考虑
- `json` import 保留：load_config、validate_config、read_device_config 仍使用
- `os`、`subprocess`、`sys`、`time` import 均保留
- 不影响 push_config.py 和 push_config.bat（独立的推送脚本）
- Android 端 ConfigManager 无需改动

## 验证
- Python 语法检查：`python3 -m py_compile config_tool.py`
- 功能测试：验证 load_config + validate_config 对手动创建的 LED_CONFIG.txt 正常工作
- GitHub Actions 构建成功

## 风险
- 风险：用户手动创建的 LED_CONFIG.txt 格式不正确
  - 处理：保留 validate_config() 校验，推送前提示具体错误
- 风险：exe 体积可能因移除代码略有减小（无影响）
