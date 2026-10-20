#!/system/bin/sh
# WireFix 守护：root 下轮询 ACCDET，状态变化时拉起 GuardService 处理
# am 失败（如开机时 AMS 未就绪）不更新 last，下轮重试

SLEEP=2
last=""
while true; do
    s=$(cat /sys/bus/platform/drivers/Accdet_Driver/state 2>/dev/null)
    if [ -n "$s" ] && [ "$s" != "$last" ]; then
        if am start-foreground-service -n com.abnen.wirefix/.GuardService --ei state "$s" >/dev/null 2>&1; then
            last=$s
        fi
    fi
    sleep $SLEEP
done
