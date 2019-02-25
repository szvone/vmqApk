package com.dommy.vmq;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.io.IOException;
import java.util.Date;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NeNotificationService extends AccessibilityService {

    public static String TAG = "NeNotificationService";

    private String host;
    private String key;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {


        if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            Log.d(TAG, "onAccessibilityEvent: 监控到新的推送");
            //应用包名:com.eg.android.AlipayGphone 推送内容: [vone通过扫码向你付款0.01元]  //支付宝
            //应用包名:com.tencent.mm 推送内容: [微信支付: 微信支付收款0.01元（朋友到店）] //微信

            Log.d(TAG,"onAccessibilityEvent aaa 应用包名:" + event.getPackageName());
            Log.d(TAG,"onAccessibilityEvent aaa 推送内容:" + event.getText());




            String text = null;
            if (event.getText().size()>0){
                text = event.getText().get(0).toString();
            }
            if (event.getPackageName().equals("com.eg.android.AlipayGphone")){
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

            }else if(event.getPackageName().equals("com.tencent.mm")){
                if (text!=null){
                    //微信支付: 微信支付收款0.01元（朋友到店）
                    String[] tmp = text.split(":");
                    if (tmp.length==2){
                        if (tmp[0].equals("微信支付")){
                            if (text.indexOf("微信支付收款")!=-1){
                                String money = getSubString(text,"微信支付收款","元");
                                Log.d(TAG, "onAccessibilityEvent: 匹配成功： 微信 到账 "+money);
                                appPush(1,Double.valueOf(money));
                            }
                        }
                    }
                }
            }else{
                //其他的推送消息
            }

        }

    }


    @Override
    protected void onServiceConnected() {

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        setServiceInfo(info);
    }

    @Override
    public void onInterrupt() {
        Log.d("", "onInterrupt");

    }

    public void appPush(int type,double price){
        //步骤1：创建一个SharedPreferences接口对象
        SharedPreferences read = getSharedPreferences("vone", MODE_WORLD_READABLE);
        //步骤2：获取文件中的值
        host = read.getString("host", "");
        key = read.getString("key", "");

        Log.d(TAG, "onResponse  push: 开始:"+type+"  "+price);

        String t = String.valueOf(new Date().getTime());
        String sign = MainActivity.md5(type+""+ price + t + key);
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





}
