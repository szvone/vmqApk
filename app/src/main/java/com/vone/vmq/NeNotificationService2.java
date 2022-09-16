package com.vone.vmq;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.vone.qrcode.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class NeNotificationService2 extends NotificationListenerService {
    private static String TAG = "NeNotificationService2";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String host = "";
    private String key = "";
    private Thread newThread = null;
    private PowerManager.WakeLock mWakeLock = null;
    public static boolean isRunning;

    //申请设备电源锁
    @SuppressLint("InvalidWakeLockTag")
    public void acquireWakeLock(final Context context) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (null == mWakeLock) {
                    PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                    if (pm != null) {
                        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "WakeLock");
                    }
                }
                if (null != mWakeLock) {
                    mWakeLock.acquire(5000);
                }
            }
        });

    }

    //释放设备电源锁
    public void releaseWakeLock() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (null != mWakeLock) {
                    mWakeLock.release();
                    mWakeLock = null;
                }
            }
        });
    }

    //心跳进程
    public void initAppHeart() {
        Log.d(TAG, "开始启动心跳线程");
        newThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "心跳线程启动！");
                while (isRunning && newThread == Thread.currentThread()) {
                    SharedPreferences read = getSharedPreferences("vone", MODE_PRIVATE);
                    host = read.getString("host", "");
                    key = read.getString("key", "");

                    //这里写入子线程需要做的工作
                    String t = String.valueOf(new Date().getTime());
                    String sign = md5(t + key);

                    final String url = "http://" + host + "/appHeart?t=" + t + "&sign=" + sign;
                    Request request = new Request.Builder().url(url).method("GET", null).build();
                    Call call = Utils.getOkHttpClient().newCall(request);
                    call.enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            // final String error = e.getMessage();
                            // Toast.makeText(getApplicationContext(), "心跳状态错误，请检查配置是否正确!" + error, Toast.LENGTH_LONG).show();
                            foregroundHeart(url);
                        }

                        //请求成功执行的方法
                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            try {
                                Log.d(TAG, "onResponse heard: " + response.body().string());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            if (!response.isSuccessful()) {
                                foregroundHeart(url);
                            }
                        }
                    });
                    try {
                        Thread.sleep(30 * 1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        newThread.start(); //启动线程
    }


    //当收到一条消息的时候回调，sbn是收到的消息
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.d(TAG, "接受到通知消息");
        writeNotifyToFile(sbn);
        SharedPreferences read = getSharedPreferences("vone", MODE_PRIVATE);
        host = read.getString("host", "");
        key = read.getString("key", "");

        Notification notification = sbn.getNotification();
        String pkg = sbn.getPackageName();
        if (notification != null) {
            Bundle extras = notification.extras;
            if (extras != null) {
                CharSequence _title = extras.getCharSequence(NotificationCompat.EXTRA_TITLE, "");
                CharSequence _content = extras.getCharSequence(NotificationCompat.EXTRA_TEXT, "");
                Log.d(TAG, "**********************");
                Log.d(TAG, "包名:" + pkg);
                Log.d(TAG, "标题:" + _title);
                Log.d(TAG, "内容:" + _content);
                Log.d(TAG, "**********************");
                // to string (企业微信之类的 getString 会出错，换getCharSequence)
                String title = _title.toString();
                String content = _content.toString();
                if ("com.eg.android.AlipayGphone".equals(pkg)) {
                    if (!content.equals("")) {
                        if (content.contains("通过扫码向你付款") || content.contains("成功收款")
                                || title.contains("通过扫码向你付款") || title.contains("成功收款")
                                || content.contains("店员通") || title.contains("店员通")) {
                            String money = getMoney(content);
                            if (money == null) {
                                money = getMoney(title);
                            }
                            if (money != null) {
                                Log.d(TAG, "onAccessibilityEvent: 匹配成功： 支付宝 到账 " + money);
                                appPush(2, Double.parseDouble(money));
                            } else {
                                handler.post(new Runnable() {
                                    public void run() {
                                        Toast.makeText(getApplicationContext(), "监听到支付宝消息但未匹配到金额！", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }
                    }
                } else if ("com.tencent.mm".equals(pkg)
                        || "com.tencent.wework".equals(pkg)) {
                    if (!content.equals("")) {
                        if (title.equals("微信支付") || title.equals("微信收款助手") || title.equals("微信收款商业版")
                                || (title.equals("对外收款") || title.equals("企业微信")) && content.contains("成功收款")) {
                            String money = getMoney(content);
                            if (money != null) {
                                Log.d(TAG, "onAccessibilityEvent: 匹配成功： 微信到账 " + money);
                                try {
                                    appPush(1, Double.parseDouble(money));
                                } catch (Exception e) {
                                    Log.d(TAG, "app push 错误！！！");
                                }
                            } else {
                                handler.post(new Runnable() {
                                    public void run() {
                                        Toast.makeText(getApplicationContext(), "监听到微信消息但未匹配到金额！", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                        }
                    }
                } else if ("com.vone.qrcode".equals(pkg)) {
                    if (content.equals("这是一条测试推送信息，如果程序正常，则会提示监听权限正常")) {
                        handler.post(new Runnable() {
                            public void run() {
                                Toast.makeText(getApplicationContext(), "监听正常，如无法正常回调请联系作者反馈！", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }
        }
    }

    //当移除一条消息的时候回调，sbn是被移除的消息
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {

    }

    //当连接成功时调用，一般在开启监听后会回调一次该方法
    @Override
    public void onListenerConnected() {
        isRunning = true;
        //开启心跳线程
        initAppHeart();

        handler.post(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), "监听服务开启成功！", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        isRunning = false;
        if (newThread != null) {
            newThread.interrupt();
        }
        newThread = null;
    }

    private void writeNotifyToFile(StatusBarNotification sbn) {
        if (!sbn.isClearable()) {
            return;
        }
        Log.i(TAG, "write notify message to file");
        //            具有写入权限，否则不写入
        CharSequence notificationTitle = null;
        CharSequence notificationText = null;
        CharSequence subText = null;

        Bundle extras = sbn.getNotification().extras;
        if (extras != null) {
            notificationTitle = extras.getCharSequence(Notification.EXTRA_TITLE);
            notificationText = extras.getCharSequence(Notification.EXTRA_TEXT);
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
        }
        String packageName = sbn.getPackageName();
        String time = Utils.formatTime(Calendar.getInstance().getTime());

        String writText = "\n" + "[" + time + "]" + "[" + packageName + "]" + "\n" +
                "[" + notificationTitle + "]" + "\n" + "[" + notificationText + "]" + "\n" +
                "[" + subText + "]" + "\n";

        // 使用 post 异步的写入
        Utils.putStr(this, writText);
    }

    /**
     * 通知服务器收款到账
     */
    public void appPush(int type, double price) {
        acquireWakeLock(this);
        SharedPreferences read = getSharedPreferences("vone", MODE_PRIVATE);
        host = read.getString("host", "");
        key = read.getString("key", "");

        Log.d(TAG, "onResponse  push: 开始:" + type + "  " + price);

        String t = String.valueOf(new Date().getTime());
        String sign = md5(type + "" + price + t + key);
        final String url = "http://" + host + "/appPush?t=" + t + "&type=" + type + "&price=" + price + "&sign=" + sign;
        Log.d(TAG, "onResponse  push: 开始:" + url);
        Request request = new Request.Builder().url(url).method("GET", null).build();
        Call call = Utils.getOkHttpClient().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d(TAG, "onResponse  push: 请求失败");
                foregroundPost(url + "&force_push=true");
                releaseWakeLock();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    Log.d(TAG, "onResponse  push: " + response.body().string());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                // 如果返回状态不是成功的。同样要回调
                if (!response.isSuccessful()) {
                    foregroundPost(url + "&force_push=true");
                }
                releaseWakeLock();
            }
        });
    }

    private void foregroundHeart(String url) {
        final Context context = NeNotificationService2.this;
        if (isRunning) {
            final JSONObject extraJson = new JSONObject();
            try {
                extraJson.put("url", url);
                extraJson.put("show", false);
            } catch (JSONException jsonException) {
                jsonException.printStackTrace();
            }
            handler.post(new Runnable() {
                @Override
                public void run() {
                    enterForeground(context,
                            context.getString(R.string.app_name),
                            context.getString(R.string.app_is_heart), extraJson.toString());
                }
            });
        }
    }

    /**
     * 当通知失败的时候，前台强制通知
     */
    private void foregroundPost(String url) {
        final Context context = NeNotificationService2.this;
        if (isRunning) {
            final JSONObject extraJson = new JSONObject();
            try {
                extraJson.put("url", url);
                extraJson.put("try_count", 5);
            } catch (JSONException jsonException) {
                jsonException.printStackTrace();
            }
            handler.post(new Runnable() {
                @Override
                public void run() {
                    enterForeground(context,
                            context.getString(R.string.app_name),
                            context.getString(R.string.app_is_post), extraJson.toString());
                }
            });
        }
    }

    /**
     * 如果出现无法通知的情况，进入前台，然后主动打开通知
     */
    public static void enterForeground(Context context, String title, String text, String extra) {
        if (context == null) return;
        Log.i(TAG, "enter fore ground");
        Intent intent = new Intent(context, ForegroundServer.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(ForegroundServer.GET_NOTIFY_TITLE, title == null ? "" : title);
        intent.putExtra(ForegroundServer.GET_NOTIFY_TEXT, text == null ? "" : text);
        intent.putExtra(ForegroundServer.GET_NOTIFY_EXTRA, extra == null ? "" : extra);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void exitForeground(Context context) {
        if (context == null) return;
        Log.i(TAG, "exitForeground");

        Intent intent1 = new Intent();
        intent1.setAction(Constant.FINISH_FOREGROUND_SERVICE);
        context.sendBroadcast(intent1);
    }

    public static String getMoney(String content) {
        List<String> ss = new ArrayList<>();
        for (String sss : content.replaceAll(",", "")
                .replaceAll("[^0-9.]", ",").split(",")) {
            if (sss.length() > 0)
                ss.add(sss);
        }
        if (ss.size() < 1) {
            return null;
        } else {
            return ss.get(ss.size() - 1);
        }
    }

    public static String md5(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
            byte[] bytes = md5.digest(string.getBytes());
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) {
                String temp = Integer.toHexString(b & 0xff);
                if (temp.length() == 1) {
                    temp = "0" + temp;
                }
                result.append(temp);
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

}
