# WireFix —— 3.5mm 有线耳机路由修复

> 修复 MTK 平台 LineageOS / GSI 上「插耳机没声音」或「耳机和扬声器同时出声」的问题。

**实测环境**：酷比魔方 iPlay30（MT6771V）｜ LineageOS 18.1 GSI（Phh-Treble）｜ Android 11 system + Android 10 vendor ｜ Magisk 28.0

---

## 快速开始（3 步）

### ① 把工具包推到设备

```bash
adb push WireFix-kit /data/local/tmp/
```

Windows 用户：把整个 `WireFix-kit` 文件夹拷到任意位置，双击 `scripts\install-windows.bat` 即可（脚本会自动推送 + 安装 + 重启）。

### ② 安装

```bash
adb shell su -c "sh /data/local/tmp/WireFix-kit/scripts/install.sh"
adb reboot
```

### ③ 验证

重启后（等 1~2 分钟）：

```bash
adb shell dumpsys audio | grep setWiredDeviceConnectionState
```

看到下面这行就成功了：

```
setWiredDeviceConnectionState( type:8 state:DEVICE_STATE_AVAILABLE addr: name:WireFix)
```

然后插耳机放音乐 → 耳机响、扬声器静音。拔掉 → 扬声器恢复。

---

## 这是干什么的

**问题**：MTK 平台的 ACCDET 驱动检测到耳机插入（`state=2`），但上报给 Android 框架的 switch 位是 `SW_LINEOUT_INSERT(4)` 而不是 `SW_HEADPHONE_INSERT(1)`，导致 `WiredAccessoryManager` 不认耳机、AudioPolicy 不切换音频路由。

**解法**：装一个特权应用，用 binder 直连 `IAudioService` 补发缺失的 `setWiredDeviceConnectionState` 事件，让 AudioPolicy 正确切到耳机。

```
ACCDET(state=2) → root 守护脚本 → GuardService
    → binder.transact(64) → AudioService → AudioPolicy
        → HAL 关扬声器 / 开耳机 → 耳机出声
```

---

## 前置要求

| 项 | 要求 |
|----|------|
| Root | Magisk 20+（实测 28.0），KernelSU 亦可 |
| 系统 | Android 10+（实测 11） |
| 平台 | **MTK**（检测路径依赖 ACCDET） |
| adb | 可用 `adb root` |

> 非 MTK 设备需要改检测源，见教程 §11.3。

---

## 目录结构

```
WireFix-kit/
├── README.md                  本文件
├── docs/
│   └── 3.5mm音频修复教程.md    完整技术教程（含原理、排查流程、踩坑记录）
├── apk/
│   ├── WireFix.apk            编译好的成品（已签名）
│   ├── AndroidManifest.xml
│   └── debug.keystore         签名密钥（alias=androiddebugkey, pass=android）
├── module/                    Magisk 模块文件
├── src/                       Java 源码
└── scripts/
    ├── install.sh             设备端安装
    ├── uninstall.sh           设备端卸载
    ├── install-windows.bat    Windows 一键安装
    ├── verify.bat             安装后自检
    └── build.sh               从源码重新编译
```

---

## 使用 App（手动控制）

桌面上会出现 **WireFix** 应用：

| 按钮 | 作用 |
|------|------|
| ① 通知插入 | 手动补发耳机插入事件 |
| ② 通知拔出 | 手动补发拔出事件 |
| ③ 启动自动守护 | 启动前台服务（不依赖 root 脚本） |
| ④ 停止守护 + 还原 | 恢复扬声器输出 |
| ⑤ 打印当前状态 | 显示 ACCDET / switch / 设备列表 |

日志区会显示每次 binder 调用的结果，排错时很有用。

---

## 卸载回滚

```bash
adb push WireFix-kit /data/local/tmp/
adb shell su -c "sh /data/local/tmp/WireFix-kit/scripts/uninstall.sh"
adb reboot
```

或者一条命令：

```bash
adb root && adb shell rm -rf /data/adb/modules/wirefix && adb reboot
```

**本方案不改 system 分区**，删除模块目录 + 重启即完全恢复。

---

## 重新编译

```bash
cd WireFix-kit
./scripts/build.sh /path/to/jdk17 /path/to/build-tools /path/to/android.jar
```

需要：JDK 17 + Android SDK build-tools（含 aapt2/d8/zipalign/apksigner）+ `android.jar`（SDK 30+）。

---

## 详细文档

完整的技术细节、四层链路排查方法、7 个踩坑记录、binder 事务号与 Parcel 布局的确认过程，都在：

**[docs/3.5mm音频修复教程.md](docs/3.5mm音频修复教程.md)**

---

## 风险提示

- 涉及系统组件，操作前确保设备可进 fastboot/9008、手上有固件包
- 模块可逆，但个别 ROM 可能有兼容差异
- 请自行确认保修条款与当地法规

---

MIT License · 欢迎 fork 和改进
