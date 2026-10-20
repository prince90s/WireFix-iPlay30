package com.abnen.wirefix;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * 常驻守护：每 1.5s 轮询 ACCDET state，检测到插/拔就补发一次路由事件。
 * 只做必要动作，空闲时开销可忽略。
 */
public class GuardService extends Service {

    private static final int CH_ID = 1;
    private static final String TAG = "WireFix";

    private Handler h;
    private Runnable loop;
    private int last = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannel ch = new NotificationChannel(
                "wirefix", "WireFix 耳机守护", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);

        Notification n = new Notification.Builder(this, "wirefix")
                .setContentTitle("WireFix 运行中")
                .setContentText("插拔耳机自动切换音频路由")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setOngoing(true)
                .build();
        startForeground(CH_ID, n);

        h = new Handler(Looper.getMainLooper());
        loop = new Runnable() {
            @Override public void run() {
                try { tick(); } catch (Throwable ignored) {}
                h.postDelayed(this, 3000);
            }
        };
        h.post(loop);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // root 守护脚本通过 am start-foreground-service 传状态进来
        if (intent != null && intent.hasExtra("state")) {
            int s = intent.getIntExtra("state", -1);
            last = s;
            if (s == 2) {
                AudioWire.setWired(this, true);
                AudioWire.forceRoute(this, 1);
            } else if (s == 0) {
                AudioWire.setWired(this, false);
                AudioWire.forceRoute(this, -1);
            }
        }
        return START_STICKY;
    }

    private void tick() {
        // App 内读不到 ACCDET（SELinux），交给 root 脚本广播；
        // 这里仅作为兜底：尝试直读，失败则跳过
        int s = AudioWire.accdetStateDirect();
        if (s == last) return;
        last = s;
        if (s == 2) {
            AudioWire.setWired(this, true);
            AudioWire.forceRoute(this, 1);
        } else if (s == 0) {
            AudioWire.setWired(this, false);
            AudioWire.forceRoute(this, -1);
        }
    }

    @Override
    public void onDestroy() {
        if (h != null && loop != null) h.removeCallbacks(loop);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
