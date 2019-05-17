package com.vone.vmq;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NeNotificationService2  extends NotificationListenerService {
    private String TAG = "NeNotificationService2";
    private String host = "";
    private String key = "";

    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.d(TAG, "接受到通知消息");
        SharedPreferences read = getSharedPreferences("vone", MODE_PRIVATE);
        host = read.getString("host", "");
        key = read.getString("key", "");


        Notification notification = sbn.getNotification();
        String pkg = sbn.getPackageName();
        if (notification != null) {
            String notitime = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(notification.when));
            Bundle extras = notification.extras;
            if (extras != null) {
                String title = extras.getString(NotificationCompat.EXTRA_TITLE, "");
                String content = extras.getString(NotificationCompat.EXTRA_TEXT, "");
                Log.d(TAG, "**********************");
                Log.d(TAG, "包名:" + pkg);
                Log.d(TAG, "title:" + title);
                Log.d(TAG, "content:" + content);
                Log.d(TAG, "**********************");


                if (pkg.equals("com.eg.android.AlipayGphone")){
                    String text = content;
                    if (text!=null){
                        if (text.indexOf(":")==-1){
                            if (text.indexOf("通过扫码向你付款")!=-1){
                                String money = getSubString(text,"通过扫码向你付款","元");
                                Log.d(TAG, "onAccessibilityEvent: 匹配成功： 支付宝 到账 "+money);
                                appPush(2,Double.valueOf(money));

                            }else if (text.indexOf("成功收款")!=-1){
                                String money = getSubString(text,"成功收款","元");
                                Log.d(TAG, "onAccessibilityEvent: 匹配成功： 支付宝 到账 "+money);
                                appPush(2,Double.valueOf(money));

                            }
                        }
                    }



                }else if(pkg.equals("com.tencent.mm")){
                    String text = content;

                    if (text!=null){
                        //微信支付: 微信支付收款0.01元（朋友到店）
                        String[] tmp = text.split(":");
                        if (tmp.length==2){
                            if (tmp[0].equals("微信支付") || tmp[0].equals("微信收款助手") || tmp[0].equals("微信收款商业版")){
                                if (text.indexOf("微信支付收款")!=-1){
                                    String money = getSubString(text,"微信支付收款","元");
                                    if (money.indexOf("支付")!=-1){
                                        money = getSubString(money+"元","微信支付收款","元");
                                    }

                                    Log.d(TAG, "onAccessibilityEvent: 匹配成功： 微信 到账 "+money);
                                    appPush(1,Double.valueOf(money));
                                }else if (text.indexOf("店员消息")!=-1){

                                    String money = getSubString(text,"[店员消息]收款到账","元");
                                    Log.d(TAG, "onAccessibilityEvent: 匹配成功： 微信店员 到账 "+money);

                                    appPush(1,Double.valueOf(money));
                                }
                            }
                        }
                    }

                }else if(pkg.equals("com.vone.qrcode")){
                    //测试推送
                    String text = content;
                    if (text.equals("这是一条测试推送信息，如果程序正常，则会提示监听权限正常")){
                        Handler handlerThree=new Handler(Looper.getMainLooper());
                        handlerThree.post(new Runnable(){
                            public void run(){
                                Toast.makeText(getApplicationContext() ,"监听权限正常！",Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }
        }

    }

    @Override
    public void onListenerConnected() {
        //当连接成功时调用，一般在开启监听后会回调一次该方法
        Handler handlerThree=new Handler(Looper.getMainLooper());
        handlerThree.post(new Runnable(){
            public void run(){
                Toast.makeText(getApplicationContext() ,"监听服务开启成功！",Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        //当移除一条消息的时候回调，sbn是被移除的消息
    }

    public void appPush(int type,double price){
        //步骤1：创建一个SharedPreferences接口对象
        SharedPreferences read = getSharedPreferences("vone", MODE_WORLD_READABLE);
        //步骤2：获取文件中的值
        host = read.getString("host", "");
        key = read.getString("key", "");

        Log.d(TAG, "onResponse  push: 开始:"+type+"  "+price);

        String t = String.valueOf(new Date().getTime());
        String sign = md5(type+""+ price + t + key);
        String url = "http://"+host+"/appPush?t="+t+"&type="+type+"&price="+price+"&sign="+sign;
        Log.d(TAG, "onResponse  push: 开始:"+url);

        //1.创建OkHttpClient对象
        OkHttpClient okHttpClient = new OkHttpClient();
        //2.创建Request对象，设置一个url地址,设置请求方式。
        Request request = new Request.Builder().url(url).method("GET",null).build();
        //3.创建一个call对象,参数就是Request请求对象
        Call call = okHttpClient.newCall(request);
        //4.请求加入调度，重写回调方法
        call.enqueue(new Callback() {
            //请求失败执行的方法
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d(TAG, "onResponse  push: 请求失败");

            }
            //请求成功执行的方法
            @Override
            public void onResponse(Call call, Response response) throws IOException {

                Log.d(TAG, "onResponse  push: "+response.body().string());

            }
        });
    }

    /**
     * 取两个文本之间的文本值
     *
     * @param text
     * @param left
     * @param right
     * @return
     */
    public static String getSubString(String text, String left, String right) {
        String result = "";
        int zLen;
        if (left == null || left.isEmpty()) {
            zLen = 0;
        } else {
            zLen = text.indexOf(left);
            if (zLen > -1) {
                zLen += left.length();
            } else {
                zLen = 0;
            }
        }
        int yLen = text.indexOf(right, zLen);
        if (yLen < 0 || right == null || right.isEmpty()) {
            yLen = text.length();
        }
        result = text.substring(zLen, yLen);
        return result;
    }


    public static String md5(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
            byte[] bytes = md5.digest(string.getBytes());
            String result = "";
            for (byte b : bytes) {
                String temp = Integer.toHexString(b & 0xff);
                if (temp.length() == 1) {
                    temp = "0" + temp;
                }
                result += temp;
            }
            return result;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

}
