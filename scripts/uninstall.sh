#!/system/bin/sh
# ============================================================
#  WireFix 一键卸载 / 回滚脚本
#  用法：adb shell su -c "sh /data/local/tmp/WireFix-kit/scripts/uninstall.sh"
#  然后：adb reboot
# ============================================================

echo "==> 停止守护脚本"
pkill -f "modules/wirefix/service.sh" 2>/dev/null

echo "==> 停止前台服务"
am stop-service com.abnen.wirefix/.GuardService >/dev/null 2>&1

echo "==> 还原音频强制路由"
# 调用 App 内逻辑不方便，这里用 cmd 侧无实现，交由重启自然恢复
echo "    （重启后 AudioPolicy 自动回到默认扬声器路由）"

echo "==> 删除 Magisk 模块"
rm -rf /data/adb/modules/wirefix

echo "==> 清理残留包数据"
rm -rf /data/user/0/com.abnen.wirefix /data/data/com.abnen.wirefix 2>/dev/null

echo ""
echo "============================================"
echo " 卸载完成，请重启：adb reboot"
echo " 重启后应恢复为：耳机无声 / 耳机与扬声器同时出声（原始状态）"
echo "============================================"
