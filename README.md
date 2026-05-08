# TouchDX for Android

**TouchDX for Android** 是专门为 **maimai DX**（舞萌 DX）开发的安卓端串流触摸控制器客户端，旨在解决使用串流工具游玩时 Windows 默认手势对游戏判定的干扰，提供最原汁原味的打歌体验。

## 功能特性

*   **1:1 还原官机的触摸方案**: 采用了与 maimai DX 街机一致的 A-E 区块触摸判定方案（基于 SVG 路径读取），独立与 PC 端通信，完全避免 Windows 手势干扰。
*   **智能操控模式转换 (黑科技)**: 客户端会实时获取 PC 游戏状态：
    *   **游玩界面下**：采用模拟区域触摸，单点可以跨越触发多个相邻区块，极大提高滑动流畅度，防止高速滑星时断触。
    *   **交互界面下 (选歌/结算)**：采用单区块精准触发，确保 UI 交互的精确性，避免不小心摸到隔壁区块导致选错歌或设置错误。
*   **内置诊断测试模式**: 当客户端连接到专门的诊断工具时，会自动激活“测试模式”，并在悬浮窗解锁隐藏的“测网络延迟”按钮，方便玩家使用 Ping 原理精确测量当前的物理网络延迟（极其适合用来对比 Wi-Fi 与 ADB 的延迟差距）。
*   **高精度、低延迟的触摸模拟**: 采用精简的二进制网络协议和原生的触摸事件处理机制，确保每一次点击、滑动都能被精准、迅速地传递到 PC 端，响应速度媲美物理按键。
*   **全功能多点触控支持**: 完美支持屏幕上多点同时触摸（上限受设备硬件影响），轻松应对铺天盖地的星星和双押配置。
*   **轻量化与低资源占用**: 客户端本身经过精心优化，无冗余动画，确保在提供强大功能的同时尽可能减少设备发热和电量消耗。

## 使用教程

### 准备工作

1.  **安装 TouchDX PC 端 Mod**: 确保你已经在 PC 上的 maimai DX 游戏环境中安装好了 [TouchDX-Mod](https://github.com/YubaiNya/TouchDX-Mod)。
2.  **网络环境选择**: 
    *   **【强烈推荐】有线连接 (ADB Reverse Forwarding)**: 为了获得音游所需的极致低延迟，我们强烈建议使用 USB 数据线连接安卓设备和 PC，并使用 ADB 的反向代理功能。这可以提供最稳定、最快的连接。
    *   **无线连接 (Wi-Fi)**: 仅供测试使用。将安卓设备和 PC 连接到同一 Wi-Fi。不推荐在实战中使用，可能会受到网络波动的严重影响。
3.  **获取连接地址**: 可以运行 [PCDiagnosticTool](https://github.com/YubaiNya/TouchDX-DiagnosticTool) 来获取本机的 IP 地址。

### 安装与配置客户端

1. **下载并安装**: 从本项目的 [Releases](https://github.com/YubaiNya/TouchDX-AndroidClient/releases) 页面，下载最新的 `.apk` 安装包，并在安卓设备上安装。

2. **【推荐】配置 ADB 有线连接**: 
   * 在 PC 上安装 [Android SDK Platform Tools](https://developer.android.com/studio/releases/platform-tools)。
   * 使用 USB 数据线连接设备和 PC，并在手机开发者选项中开启“USB 调试”模式。
   * 在 PC 的终端中输入以下命令：
     ```
     adb reverse tcp:4321 tcp:4321
     ```
   * 这会将 PC 上的 4321 端口转发到安卓设备上的 4321 端口。

3. **打开应用并配置**: 
   *   打开手机上的 “TouchDX” 应用。
   *   **如果使用了 ADB 有线连接**，在 IP 地址输入框中输入 `127.0.0.1`。
   *   **如果使用 Wi-Fi 连接**，填入 PC 的局域网 IP 地址。
   *   点击“连接”按钮。

4. **确认连接状态**: 
   *   屏幕上会显示“已连接”的提示。你可以使用 `PCDiagnosticTool` 工具测试触摸反馈是否正常。

### 开始游戏！

1.  启动带有 TouchDX Mod 的 maimai DX。
2.  你的安卓屏幕现在已经变成了一块标准的 maimai DX 触摸屏外设！
3.  享受打歌的乐趣吧！

## 常见问题 (FAQ)

**Q: 连接失败，或者连接后没有反应怎么办？**

A: 请按照以下步骤进行排查：
      1.  **ADB 用户**: 确保终端中输入 `adb devices` 可以看到设备处于 `device` 状态，并且你输入了正确的转发命令。
      2.  **检查 IP 地址**: 确认填入的 IP 无误。
      3.  **检查防火墙**: 检查 Windows 防火墙是否阻止了 `4321` 端口的通信。
      4.  **检查 Mod 是否加载**: 查看 MelonLoader 启动日志，确认 `MaiRemoteTouchMod` 是否加载并成功在端口监听。

**Q: 延迟很高，操作不跟手怎么办？**

A: **必须使用 ADB 有线连接！** 音游对延迟的要求极其苛刻，Wi-Fi 的波动必然导致判定偏移。请确保你通过 USB 线连接并成功执行了 `adb reverse`。

## 💖 赞助与支持

如果您觉得 TouchDX 解决了您的痛点，带来了丝滑的打歌体验，欢迎通过**爱发电**支持作者：

<a href="https://ifdian.net/a/YubaiNya" target="_blank">
  <img src="https://img.shields.io/badge/%E7%88%B1%E5%8F%91%E7%94%B5-%E8%B5%9E%E5%8A%A9%E4%BD%9C%E8%80%85-946ce6?style=for-the-badge&logo=buy-me-a-coffee&logoColor=white" alt="赞助作者">
</a>

## 贡献

我们欢迎任何形式的贡献！无论是提交 Bug 还是代码，请在本项目的主仓库中进行操作。

## 开源致谢

本项目在开发过程中，使用了 AOSP (Android Open Source Project) 中的部分源代码以实现核心功能，在此表示衷心的感谢。
*   **`PathParser`**: 用于解析和处理SVG路径数据的核心工具类，源代码直接取自 AOSP，遵循 `Apache License 2.0` 许可。

---

## 许可

本项目采用 [AGPL-3.0 License](LICENSE) 开源许可。
