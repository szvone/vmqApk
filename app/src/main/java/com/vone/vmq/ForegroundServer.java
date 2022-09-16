package com.vone.vmq;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.Log;

import com.vone.qrcode.R;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

@SuppressWarnings("FieldCanBeLocal")
public class ForegroundServer extends Service {
    public static final String GET_NOTIFY_TITLE = "get_notify_title";
    public static final String GET_NOTIFY_TEXT = "get_notify_text";
    //这里传 json 过来
    public static final String GET_NOTIFY_EXTRA = "get_notify_extra";

    private final int FOREGROUND_ID = 1;

    private final String channel_name = "ForegoundServer";
    private final String CHANNEL_ID = "service";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final long MIN_SHOW_TIME = 2000;
    private final long MAX_SHOW_TIME = 20000;

    private long enterTime;

    @Override
    public void onCreate() {
        super.onCreate();
        enterTime = SystemClock.elapsedRealtime();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setForegroundService();
        }
        registerFinishBroad();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handler.removeCallbacks(stopServerRunnable);
        handler.postDelayed(stopServerRunnable, MAX_SHOW_TIME);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            updateNotify(intent);
        }
        startForegroundActivity(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        unregisterFinishBroad();
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Log.i("ForegoundServer", "stop notification");
        if (notificationManager != null) {
            notificationManager.cancel(FOREGROUND_ID);
        }
        handler.removeCallbacks(stopServerRunnable);
        super.onDestroy();
    }

    private void startForegroundActivity(Intent intent) {
        if (intent == null) {
            return;
        }
        String extraStr = intent.getStringExtra(GET_NOTIFY_EXTRA);
        if (TextUtils.isEmpty(extraStr)) {
            return;
        }
        try {
            JSONObject jsonObject = new JSONObject(extraStr);
            final String url = jsonObject.optString("url");
            if (url == null) {
                return;
            }
            if (jsonObject.optBoolean("show", true)) {
                startLockActivity(this.getString(R.string.app_is_post));
            }
            tryPushByUrl(url, jsonObject.optInt("try_count", 1));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void tryPushByUrl(final String url, final int count) {
        if (count <= 0) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    NeNotificationService2.exitForeground(App.getContext());
                }
            });
            return;
        }
        // 进行一个短暂的延迟再通知过去
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Request request = new Request.Builder().url(url).method("GET", null).build();
                Call call = Utils.getOkHttpClient().newCall(request);
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.d("ForegroundServer", "onResponse  push: 请求失败");
                        tryPushByUrl(url, count - 1);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        try {
                            Log.d("ForegroundServer", "onResponse  push: " + response.body().string());
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            if (!response.isSuccessful()) {
                                tryPushByUrl(url, count - 1);
                            } else {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        NeNotificationService2.exitForeground(App.getContext());
                                    }
                                });
                            }
                        }
                    }
                });
            }
        }, MIN_SHOW_TIME);
    }

    /**
     * 通过通知启动服务
     */
    @TargetApi(Build.VERSION_CODES.O)
    private void setForegroundService() {
        Utils.createNotificationChannel(this, CHANNEL_ID, channel_name
                , NotificationManager.IMPORTANCE_DEFAULT);
        Notification notification = getNotifycation(null);

        startForeground(FOREGROUND_ID, notification);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void updateNotify(Intent intent) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            Notification notification = getNotifycation(intent);

            notificationManager.notify(FOREGROUND_ID, notification);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Notification getNotifycation(Intent intent) {
        String title = intent == null ? getString(R.string.app_name) :
                TextUtils.isEmpty(intent.getStringExtra(GET_NOTIFY_TITLE)) ? getString(R.string.app_name)
                        : intent.getStringExtra(GET_NOTIFY_TITLE);
        String text = intent == null ? getString(R.string.click_close_notify) :
                TextUtils.isEmpty(intent.getStringExtra(GET_NOTIFY_TEXT)) ? getString(R.string.click_close_notify)
                        : intent.getStringExtra(GET_NOTIFY_TEXT);
        Notification.Builder notificationBuilder = new Notification.Builder(this)
                .setContentTitle(title)//设置通知标题
                .setContentText(text)//设置通知内容
                .setPriority(Notification.PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationBuilder.setChannelId(CHANNEL_ID);
        }

        Notification notification = notificationBuilder.build();

        Intent notificationIntent = new Intent(getApplicationContext(), StartReceive.class);
        notificationIntent.setAction(StartReceive.TRY_CLOSE_ACTIVITY_ACTION);
        notification.contentIntent = PendingIntent.getBroadcast(getApplicationContext(),
                0, notificationIntent, 0);
        return notification;
    }

    private void registerFinishBroad() {
        registerReceiver(finishServiceBroadcast,
                new IntentFilter(Constant.FINISH_FOREGROUND_SERVICE));
    }

    private void unregisterFinishBroad() {
        try {
            this.unregisterReceiver(finishServiceBroadcast);
        } catch (Exception ignore) {
        }
    }

    private final Runnable stopServerRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                finishLockActivity();
                Intent intent1 = new Intent(App.getContext(), ForegroundServer.class);
                stopService(intent1);
            } catch (Exception ignore) {
            }
        }
    };

    private final BroadcastReceiver finishServiceBroadcast = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Constant.FINISH_FOREGROUND_SERVICE.equals(intent.getAction())) {
                long temp = SystemClock.elapsedRealtime() - enterTime;
                handler.removeCallbacks(stopServerRunnable);
                if (temp > MIN_SHOW_TIME) {
                    handler.post(stopServerRunnable);
                } else {
                    handler.postDelayed(stopServerRunnable, MIN_SHOW_TIME - temp);
                }
            }
        }
    };

    private void startLockActivity(String msg) {
        Intent intent = new Intent(this, LockShowActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Constant.GET_MESSAGE_KEY, msg);
        startActivity(intent);
    }

    private void finishLockActivity() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Constant.FINISH_LOCK_SHOW_ACTIVITY);
        sendBroadcast(sendIntent);
    }

    private void updataMessageData(String msg) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Constant.UPDATA_MESSAGE_DATA_ACTION);
        sendIntent.putExtra(Constant.GET_MESSAGE_KEY, msg);
        sendBroadcast(sendIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
