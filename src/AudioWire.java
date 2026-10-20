package com.abnen.wirefix;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;

/**
 * 核心：读 ACCDET 状态 + 主动通知 AudioPolicy 切换有线耳机路由。
 *
 * 背景（本机实测）：
 *   - MTK ACCDET 驱动正常，插耳机后 /sys/.../Accdet_Driver/state = 2
 *   - 但 InputReader 只上报 SwitchValues=4 (SW_MICROPHONE_INSERT)，
 *     缺 SW_HEADPHONE_INSERT(bit0)，导致 WiredAccessoryManager 不切路由
 *   - AudioPolicy 一直认为只有 SPEAKER，所以耳机和平板同时出声
 *
 * 对策：
 *   用普通 App 身份（有 AppOps）+ 反射调用隐藏 API
 *   AudioManager.setWiredDeviceConnectionState(AudioDeviceAttributes, int, String)
 *   手动补一次"插入"事件，让 AudioPolicy 把输出切到 WIRED_HEADPHONE。
 */
public final class AudioWire {

    private AudioWire() {}

    private static boolean exempted = false;

    /** 绕过 Hidden API 限制：VMRuntime.setHiddenApiExemptions("L") */
    static synchronized boolean bypassHiddenApi() {
        if (exempted) return true;
        try {
            Class<?> vmr = Class.forName("dalvik.system.VMRuntime");
            java.lang.reflect.Method getRt = vmr.getDeclaredMethod("getRuntime");
            getRt.setAccessible(true);
            Object rt = getRt.invoke(null);
            java.lang.reflect.Method setEx = vmr.getDeclaredMethod(
                    "setHiddenApiExemptions", String[].class);
            setEx.setAccessible(true);
            setEx.invoke(rt, new Object[]{new String[]{"L"}});
            exempted = true;
            return true;
        } catch (Throwable t) {
            android.util.Log.e(TAG, "bypassHiddenApi failed", t);
            return false;
        }
    }

    private static final String TAG = "WireFix";

    private static final String ACCDET_STATE =
            "/sys/bus/platform/drivers/Accdet_Driver/state";
    private static final String ACCDET_MODE =
            "/sys/bus/platform/drivers/Accdet_Driver/set_headset_mode";

    /** AudioDeviceInfo.TYPE_WIRED_HEADPHONES = 4（不是 DEVICE_OUT_WIRED_HEADPHONE=8！） */
    private static final int TYPE_WIRED_HEADPHONES = 4;
    private static final int ROLE_OUTPUT = 2;   // AudioDeviceAttributes.ROLE_OUTPUT

    /** AudioSystem.DEVICE_OUT_WIRED_HEADPHONE = 8，用于 setForceUse / setPreferredDevice */
    private static final int DEVICE_OUT_WIRED_HEADPHONE = 8;
    private static final int DEVICE_OUT_SPEAKER = 2;

    // ------------------------------------------------------------------ 基础

    static String logHeader() {
        return "—— " + new java.util.Date().toString() + " ——";
    }

    /** ACCDET 原始状态：0=无, 1=?, 2=已插入（实测）。App 读 sysfs 被 SELinux 拒，走 su。 */
    static int accdetState() {
        // 先试直读（system 权限下可用）
        int v = readInt(ACCDET_STATE, -1);
        if (v >= 0) return v;
        // 回落：su 读（Magisk 会弹授权）
        String out = suCmd("cat " + ACCDET_STATE);
        try { return Integer.parseInt(out.trim()); } catch (Throwable t) { return -1; }
    }

    /** 仅直读，不碰 su（App 进程内 su 不可靠） */
    static int accdetStateDirect() {
        return readInt(ACCDET_STATE, -1);
    }

    static String suCmd(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            java.io.InputStream in = p.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[256];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            p.waitFor();
            return bo.toString("UTF-8").trim();
        } catch (Throwable t) {
            return "";
        }
    }

    static boolean hasRoot() {
        return new java.io.File("/system/xbin/su").exists()
                || new java.io.File("/system/bin/su").exists()
                || new java.io.File("/sbin/su").exists()
                || canRead(ACCDET_STATE);
    }

    private static boolean canRead(String p) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(p)) {
            return in.read() >= 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static int readInt(String path, int def) {
        try (java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.FileReader(path))) {
            String s = r.readLine();
            if (s == null) return def;
            return Integer.parseInt(s.trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private static AudioManager am(Context c) {
        return (AudioManager) c.getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
    }

    // ------------------------------------------------- 核心：补发插入/拔出事件

    /**
     * 反射调用 AudioManager#setWiredDeviceConnectionState(AudioDeviceAttributes, int, String)
     * 返回可读的结果字符串。
     */
    static String setWired(Context c, boolean connect) {
        StringBuilder sb = new StringBuilder();
        sb.append("setWired(").append(connect ? "connect" : "disconnect").append(")\n");
        sb.append("  hiddenApiExempt=").append(bypassHiddenApi()).append('\n');

        AudioManager m = am(c);
        if (m == null) return sb.append("  !! AudioManager null\n").toString();

        try {
            sb.append(binderSetWired(connect));
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable t = e.getCause() == null ? e : e.getCause();
            sb.append("  !! 调用被拒: ").append(t.getClass().getSimpleName())
              .append(": ").append(t.getMessage()).append('\n');
        } catch (Throwable t) {
            sb.append("  !! 反射失败: ").append(t.getClass().getSimpleName())
              .append(": ").append(t.getMessage()).append('\n');
        }
        return sb.toString();
    }

    /**
     * 直连 IAudioService binder，transact 64 = setWiredDeviceConnectionState。
     * Parcel 布局（与 framework.jar 反编译结果一致）：
     *   AudioDeviceAttributes{int role, int type, String address} + int state + String caller
     */
    @SuppressWarnings("unchecked")
    static String binderSetWired(boolean connect) throws Exception {
        StringBuilder sb = new StringBuilder("  [binder] ");
        Class<?> sm = Class.forName("android.os.ServiceManager");
        java.lang.reflect.Method gs;
        try {
            gs = sm.getMethod("getService", String.class);
        } catch (NoSuchMethodException e) {
            // Hidden API 拦截时用 meta-reflection 绕过
            java.lang.reflect.Method meta = Class.class.getDeclaredMethod(
                    "getDeclaredMethod", String.class, Class[].class);
            gs = (java.lang.reflect.Method) meta.invoke(sm,
                    "getService", new Class[]{String.class});
        }
        gs.setAccessible(true);
        android.os.IBinder b = (android.os.IBinder) gs.invoke(null, "audio");
        if (b == null) return sb.append("audio binder == null").toString();

        android.os.Parcel data = android.os.Parcel.obtain();
        android.os.Parcel reply = android.os.Parcel.obtain();
        try {
            data.writeInterfaceToken("android.media.IAudioService");
            // 本机 GSI 实际签名: setWiredDeviceConnectionState(int device, int state,
            //                    String address, String caller)   —— dexdump 实证
            // device = AudioSystem.DEVICE_OUT_WIRED_HEADPHONE = 8
            data.writeInt(DEVICE_OUT_WIRED_HEADPHONE);  // device
            data.writeInt(connect ? 1 : 0);             // state
            data.writeString("");                       // address
            data.writeString("WireFix");                // caller
            boolean oneWay = b.transact(64, data, reply, 0);
            reply.readException();
            int ret = reply.readInt();
            return sb.append("transact(64) ok, oneway=").append(oneWay)
                     .append(", ret=").append(ret).append('\n').toString();
        } finally {
            data.recycle();
            reply.recycle();
        }
    }


    /**
     * 双保险：直接用 AudioSystem.setForceUse(FOR_MEDIA, FORCE_HEADPHONES)
     * 强制 AudioPolicy 走耳机。mode < 0 表示恢复默认。
     *   AudioSystem.FOR_MEDIA = 1, FORCE_NONE = 0, FORCE_HEADPHONES = 4
     */
    static String forceRoute(Context c, int mode) {
        StringBuilder sb = new StringBuilder();
        sb.append("forceRoute(").append(mode < 0 ? "restore" : mode).append(")\n");
        try {
            Class<?> as = Class.forName("android.media.AudioSystem");
            java.lang.reflect.Method md = as.getMethod("setForceUse", int.class, int.class);
            md.setAccessible(true);
            md.invoke(null, 1 /*FOR_MEDIA*/, mode < 0 ? 0 : 4 /*FORCE_HEADPHONES*/);
            sb.append("  setForceUse OK\n");
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable t = e.getCause() == null ? e : e.getCause();
            sb.append("  !! ").append(t.getClass().getSimpleName())
              .append(": ").append(t.getMessage()).append('\n');
        } catch (Throwable t) {
            sb.append("  !! ").append(t).append('\n');
        }
        return sb.toString();
    }

    /** 用 setPreferredDevice 再兜一层（走 AIDL，一般无需额外权限） */
    static String preferHeadphone(Context c, boolean on) {
        StringBuilder sb = new StringBuilder();
        try {
            AudioManager m = am(c);
            AudioDeviceInfo[] outs = m.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            AudioDeviceInfo target = null;
            for (AudioDeviceInfo d : outs) {
                int t = d.getType();
                if (on && t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) { target = d; break; }
                if (!on && t == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) { target = d; break; }
            }
            sb.append("  preferHeadphone -> ").append(target);
            if (target != null) {
                java.lang.reflect.Method sp = AudioManager.class
                        .getMethod("setPreferredDevice", AudioDeviceInfo.class);
                sp.setAccessible(true);
                sb.append(" =").append(sp.invoke(m, target));
            }
            sb.append('\n');
        } catch (Throwable t) {
            sb.append("  !! ").append(t).append('\n');
        }
        return sb.toString();
    }

    // --------------------------------------------------------------- 状态输出

    static String state() {
        StringBuilder sb = new StringBuilder();
        sb.append("ACCDET state = ").append(accdetState())
          .append("   (2 = 已插入)\n");
        sb.append("switch       = ").append(readStr("/sys/class/switch/h2w/state"))
          .append("   (bit0=SW_HEADPHONE_INSERT, bit1=SW_MICROPHONE_INSERT)\n");
        sb.append("hw_state     = ").append(readStr("/sys/class/switch/h2w/hw_state")).append('\n');
        return sb.toString();
    }

    private static String readStr(String p) {
        try (java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.FileReader(p))) {
            String s = r.readLine();
            return s == null ? "(null)" : s.trim();
        } catch (Throwable t) {
            return "(不可读)";
        }
    }

    /** 列出当前输出设备，便于判断路由是否切过去 */
    static String deviceList(Context c) {
        StringBuilder sb = new StringBuilder("outputs: ");
        try {
            AudioManager m = am(c);
            for (AudioDeviceInfo d : m.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                sb.append(typeName(d.getType())).append('#').append(d.getId()).append(' ');
            }
        } catch (Throwable t) {
            sb.append("err ").append(t);
        }
        return sb.append('\n').toString();
    }

    static String typeName(int t) {
        switch (t) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "SPEAKER";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: return "HP";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "HEADSET";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "BT_A2DP";
            case AudioDeviceInfo.TYPE_USB_DEVICE: return "USB";
            default: return "T" + t;
        }
    }
}
