package com.abnen.wirefix;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 接收 root 守护脚本广播：am broadcast -a com.abnen.wirefix.ACCDET_STATE --ei state N
 * 2 = 插入, 0 = 拔出。
 */
public class StateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"com.abnen.wirefix.ACCDET_STATE".equals(intent.getAction())) return;
        int s = intent.getIntExtra("state", -1);
        if (s == 2) {
            AudioWire.setWired(context, true);
            AudioWire.forceRoute(context, 1);
        } else if (s == 0) {
            AudioWire.setWired(context, false);
            AudioWire.forceRoute(context, -1);
        }
    }
}
