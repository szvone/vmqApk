package com.vone.vmq;

import static android.content.Context.POWER_SERVICE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import com.vone.qrcode.R;

/**
 * Created by user68 on 2018/7/30.
 * <p>
 * 接收另一个app的广播启动本地服务
 */

public class StartReceive extends BroadcastReceiver {
    public static final String START_SETTING_ACTIVITY_ACTION = "android.provider.Telephony.SECRET_CODE";
    public static final String TRY_CLOSE_ACTIVITY_ACTION = "try_close_activity_action";

    static boolean isBootCompleted = false; // 标志是否已经开机发送过通知

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("StartReceive", "start");
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (!checkBatteryWhiteList(context)) {
                isBootCompleted = true;
                Utils.sendBatteryNotify(context);
            }
            NeNotificationService2.enterForeground(context,
                    context.getString(R.string.app_name),
                    context.getString(R.string.app_is_start), "");
        }
        if (START_SETTING_ACTIVITY_ACTION.equals(intent.getAction())) {
            Log.d("StartReceive", "start");
            Intent startActivityIntent = new Intent(context, MainActivity.class);
            startActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(startActivityIntent);
        }
    }

    static boolean checkBatteryWhiteList(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) context.getSystemService(POWER_SERVICE);
            if (powerManager == null) return true;
            return powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
        }
        return true;
    }
}
