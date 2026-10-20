#!/system/bin/sh
# ============================================================
#  WireFix 一键安装脚本（在设备上以 root 执行）
#  用法：
#    1) 把整个 WireFix-kit 推到设备：
#         adb push WireFix-kit /data/local/tmp/
#    2) 执行：
#         adb shell su -c "sh /data/local/tmp/WireFix-kit/scripts/install.sh"
#    3) 重启：adb reboot
# ============================================================

KIT=$(cd "$(dirname "$0")/.." && pwd)
MOD=/data/adb/modules/wirefix

echo "==> 检查 root"
if [ "$(id -u)" != "0" ]; then
    echo "!! 需要 root。请用: su -c 'sh $0'"
    exit 1
fi

echo "==> 检查 Magisk"
if [ ! -d /data/adb/modules ]; then
    echo "!! 未检测到 Magisk（/data/adb/modules 不存在）"
    echo "   本方案依赖 Magisk systemless 模块把 APK 提为系统特权应用"
    exit 1
fi

echo "==> 清理可能残留的 update 标记（会导致 Magisk 跳过挂载）"
rm -f "$MOD/update"

echo "==> 创建模块目录 $MOD"
mkdir -p "$MOD/system/priv-app/WireFix"
mkdir -p "$MOD/system/etc/permissions"

echo "==> 复制 APK"
cp "$KIT/apk/WireFix.apk" "$MOD/system/priv-app/WireFix/WireFix.apk"
chmod 644 "$MOD/system/priv-app/WireFix/WireFix.apk"
chcon u:object_r:system_file:s0 "$MOD/system/priv-app/WireFix/WireFix.apk" 2>/dev/null

echo "==> 写 module.prop"
cat > "$MOD/module.prop" << 'PROP'
id=wirefix
name=WireFix 耳机路由修复
version=1.0
versionCode=1
author=zhuanjia
description=修复 MTK 平台 LineageOS/GSI 下 3.5mm 有线耳机无声、耳机与扬声器同时出声的问题。
PROP

echo "==> 写权限白名单"
cat > "$MOD/system/etc/permissions/privapp-permissions-wirefix.xml" << 'XML'
<?xml version="1.0" encoding="utf-8"?>
<permissions>
    <privapp-permissions package="com.abnen.wirefix">
        <permission name="android.permission.MODIFY_AUDIO_ROUTING" />
        <permission name="android.permission.MODIFY_AUDIO_SETTINGS" />
        <permission name="android.permission.FOREGROUND_SERVICE" />
    </privapp-permissions>
</permissions>
XML
chmod 644 "$MOD/system/etc/permissions/privapp-permissions-wirefix.xml"
chcon u:object_r:system_file:s0 "$MOD/system/etc/permissions/privapp-permissions-wirefix.xml" 2>/dev/null

echo "==> 复制守护脚本"
cp "$KIT/module/service.sh" "$MOD/service.sh"
chmod 755 "$MOD/service.sh"
chcon u:object_r:system_file:s0 "$MOD/service.sh" 2>/dev/null

echo "==> 卸载可能存在的用户层同包名应用"
pm uninstall com.abnen.wirefix >/dev/null 2>&1

echo ""
echo "============================================"
echo " 安装完成！请重启设备使模块生效："
echo "   adb reboot"
echo ""
echo " 重启后自检："
echo "   pm path com.abnen.wirefix"
echo "   dumpsys package com.abnen.wirefix | grep MODIFY_AUDIO_ROUTING"
echo "============================================"
