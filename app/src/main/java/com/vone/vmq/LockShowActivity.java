package com.vone.vmq;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;

import com.vone.qrcode.R;

public class LockShowActivity extends Activity {
    private TextView showTv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_lock_show);
        registerBroadCast();
        InitView();
        InitData();
    }

    private void registerBroadCast() {
        IntentFilter filter = new IntentFilter(Constant.FINISH_LOCK_SHOW_ACTIVITY);
        registerReceiver(finishActivityBroadcast, filter);
    }

    private void unregisterBroadCast() {
        unregisterReceiver(finishActivityBroadcast);
    }

    private void InitView() {
        showTv = findViewById(R.id.showTv);
    }

    private void InitData() {
        resetData(getIntent());
    }

    /**
     * 显示通知类型：
     * -1 ：检测到对应的配置通知
     * 0 ： 远程的重要通知
     * 1 ： 通知栏通知
     */
    private void resetData(Intent intent) {
        String showTvText = intent.getStringExtra(Constant.GET_MESSAGE_KEY);
        showTv.setText(showTvText);
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        resetData(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterBroadCast();
    }

    private final BroadcastReceiver finishActivityBroadcast = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Constant.FINISH_LOCK_SHOW_ACTIVITY.equals(intent.getAction())) {
                if (!isFinishing()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        finishAndRemoveTask();
                    }
                }
            } else if (Constant.UPDATA_MESSAGE_DATA_ACTION.equals(intent.getAction())) {
                resetData(intent);
            }
        }
    };
}
