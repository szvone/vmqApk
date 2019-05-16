package com.vone.vmq;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NeNotificationService extends AccessibilityService {

    public static String TAG = "NeNotificationService";

    private String host = "";
    private String key = "";

    private Thread newThread = null; //心跳线程

    PowerManager.WakeLock mWakeLock = null;

    //申请设备电源锁
    @SuppressLint("InvalidWakeLockTag")
    public void acquireWakeLock(Context context)
    {
        if (null == mWakeLock)
        {
            PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK|PowerManager.ON_AFTER_RELEASE, "WakeLock");
            if (null != mWakeLock)
            {
                mWakeLock.acquire();
            }
        }
    }
    //释放设备电源锁
    public void releaseWakeLock()
    {
        if (null != mWakeLock)
        {
            mWakeLock.release();
            mWakeLock = null;
        }
    }



    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Log.d(TAG, "onAccessibilityEvent: "+event);

        if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            Log.d(TAG, "onAccessibilityEvent: 监控到新的推送");
            //应用包名:com.eg.android.AlipayGphone 推送内容: [vone通过扫码向你付款0.01元]  //支付宝
            //应用包名:com.tencent.mm 推送内容: [微信支付: 微信支付收款0.01元（朋友到店）] //微信

            Log.d(TAG,"onAccessibilityEvent: 应用包名:" + event.getPackageName());
            Log.d(TAG,"onAccessibilityEvent: 推送内容:" + event.getText());

            /*

            List<CharSequence> texts = event.getText();

            if (event.getPackageName().equals("com.eg.android.AlipayGphone")){
                if (!texts.isEmpty()) {
                    for (CharSequence ctext : texts) {
                        String text = ctext.toString();
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
                    }
                }



            }else if(event.getPackageName().equals("com.tencent.mm")){
                if (!texts.isEmpty()) {
                    for (CharSequence ctext : texts) {
                        String text = ctext.toString();
                        if (text!=null){
                            //微信支付: 微信支付收款0.01元（朋友到店）
                            String[] tmp = text.split(":");
                            if (tmp.length==2){
                                if (tmp[0].equals("微信支付") || tmp[0].equals("微信收款助手") ){
                                    if (text.indexOf("微信支付收款")!=-1){
                                        String money = getSubString(text,"微信支付收款","元");
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
                    }
                }

            }else if(event.getPackageName().equals("com.vone.qrcode")){
                //测试推送
                if (!texts.isEmpty()) {
                    for (CharSequence ctext : texts) {
                        String text = ctext.toString();
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
            */

        }

    }


    @Override
    protected void onServiceConnected() {
        //设置监听配置
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;

        info.notificationTimeout = 10;
        setServiceInfo(info);

        //读入保存的配置数据并显示
        SharedPreferences read = getSharedPreferences("vone", MODE_PRIVATE);
        host = read.getString("host", "");
        key = read.getString("key", "");


        //开启心跳线程
        initAppHeart();


        Handler handlerThree = new Handler(Looper.getMainLooper());
        handlerThree.post(new Runnable(){
            public void run(){
                Toast.makeText(getApplicationContext() ,"监听服务开启成功！",Toast.LENGTH_LONG).show();
            }
        });

    }

    @Override
    public void onInterrupt() {
        Log.d("", "onInterrupt");

    }



    public void initAppHeart(){
        Log.d(TAG, "run: init");
        if (newThread!=null){
            return;
        }
        acquireWakeLock(this);
        newThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "run: 123123");
                while (true){
                    //这里写入子线程需要做的工作
                    String t = String.valueOf(new Date().getTime());
                    String sign = md5(t+key);


                    OkHttpClient okHttpClient = new OkHttpClient();
                    Request request = new Request.Builder().url("http://"+host+"/appHeart?t="+t+"&sign="+sign).method("GET",null).build();
                    Call call = okHttpClient.newCall(request);
                    call.enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            final String error = e.getMessage();
                            Handler handlerThree=new Handler(Looper.getMainLooper());
                            handlerThree.post(new Runnable(){
                                public void run(){
                                    Toast.makeText(getApplicationContext() ,"心跳状态错误，请检查配置是否正确!"+error,Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                        //请求成功执行的方法
                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            Log.d(TAG, "onResponse heard: "+response.body().string());
                        }
                    });
                    try {
                        Thread.sleep(30*1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }
        });

        newThread.start(); //启动线程
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
