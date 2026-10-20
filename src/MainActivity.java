package com.abnen.wirefix;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 极简 UI：三个按钮 + 日志区。
 * 真正的逻辑在 AudioWire 里。
 */
public class MainActivity extends Activity {

    private TextView log;
    private ScrollView sv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (12 * getResources().getDisplayMetrics().density);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("WireFix  —  耳机路由修复");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, p, 0, p);
        root.addView(title);

        Button bConn = new Button(this);
        bConn.setText("① 通知插入 (connect)");
        bConn.setOnClickListener(v -> {
            append(AudioWire.logHeader());
            append(AudioWire.setWired(this, true));
            // 顺带设置一次输出路由，逼近 system_server 的行为
            append(AudioWire.forceRoute(this, 0));
            dumpState();
        });
        root.addView(bConn);

        Button bDisc = new Button(this);
        bDisc.setText("② 通知拔出 (disconnect)");
        bDisc.setOnClickListener(v -> {
            append(AudioWire.logHeader());
            append(AudioWire.setWired(this, false));
            append(AudioWire.forceRoute(this, -1));
            dumpState();
        });
        root.addView(bDisc);

        Button bAuto = new Button(this);
        bAuto.setText("③ 启动自动守护（插拔自动切）");
        bAuto.setOnClickListener(v -> {
            startService(new Intent(this, GuardService.class));
            append(">> GuardService 已启动");
        });
        root.addView(bAuto);

        Button bStop = new Button(this);
        bStop.setText("④ 停止守护 + 还原到扬声器");
        bStop.setOnClickListener(v -> {
            stopService(new Intent(this, GuardService.class));
            append(">> GuardService 已停止");
            append(AudioWire.forceRoute(this, -1));
            dumpState();
        });
        root.addView(bStop);

        Button bDump = new Button(this);
        bDump.setText("⑤ 打印当前状态");
        bDump.setOnClickListener(v -> dumpState());
        root.addView(bDump);

        log = new TextView(this);
        log.setTextSize(11);
        log.setPadding(p, p, p, p);

        sv = new ScrollView(this);
        sv.addView(log);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(sv, lp);

        setContentView(root);

        append("ready.  root=" + AudioWire.hasRoot());
        dumpState();
    }

    private void dumpState() {
        append(AudioWire.state());
        sv.post(() -> sv.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void append(String s) {
        if (log.getText().length() > 6000) log.setText("");
        log.append(s + "\n");
        sv.post(() -> sv.fullScroll(ScrollView.FOCUS_DOWN));
    }
}
