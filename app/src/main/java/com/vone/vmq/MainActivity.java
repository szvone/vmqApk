package com.vone.vmq;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.vone.qrcode.R;
import com.vone.vmq.util.Constant;
import com.google.zxing.activity.CaptureActivity;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity{
    Button btnQrCode;
    Button btnStart;
    Button btnInput;

    TextView txthost; // 结果
    TextView txtkey; // 结果

    boolean isOk = false;
    public static String TAG = "MainActivity";

    public static String host;
    public static String key;

    int id = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //初始化界面
        btnQrCode = (Button) findViewById(R.id.btn_qrcode);//扫码配置
        btnInput = (Button) findViewById(R.id.btn_input);//手动配置

        btnStart = (Button) findViewById(R.id.btn_start);//开启服务

        txthost = (TextView) findViewById(R.id.txt_host);
        txtkey = (TextView) findViewById(R.id.txt_key);


        //检测服务是否在线
        if(isAccessibilitySettingsOn(this)){
            btnStart.setText("检测服务状态");
        }


        //读入保存的配置数据并显示
        SharedPreferences read = getSharedPreferences("vone", MODE_PRIVATE);
        host = read.getString("host", "");
        key = read.getString("key", "");

        if (host!=null && key!=null && host!="" && key!=""){
            txthost.setText(" 通知地址："+host);
            txtkey.setText(" 通讯密钥："+key);
            isOk = true;
        }



    }
    //扫码配置
    public void startQrCode(View v) {
        // 申请相机权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // 申请权限
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, Constant.REQ_PERM_CAMERA);
            return;
        }
        // 申请文件读写权限（部分朋友遇到相册选图需要读写权限的情况，这里一并写一下）
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // 申请权限
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, Constant.REQ_PERM_EXTERNAL_STORAGE);
            return;
        }
        // 二维码扫码
        Intent intent = new Intent(MainActivity.this, CaptureActivity.class);
        startActivityForResult(intent, Constant.REQ_QR_CODE);
    }
    //手动配置
    public void doInput(View v){
        final EditText inputServer = new EditText(this);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("请输入配置数据").setView(inputServer)
                .setNegativeButton("取消", null);
        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                String scanResult = inputServer.getText().toString();

                String[] tmp = scanResult.split("/");
                if (tmp.length!=2){
                    Toast.makeText(MainActivity.this, "数据错误，请您输入网站上显示的配置数据!", Toast.LENGTH_SHORT).show();
                    return;
                }

                String t = String.valueOf(new Date().getTime());
                String sign = md5(t+tmp[1]);

                //1.创建OkHttpClient对象
                OkHttpClient okHttpClient = new OkHttpClient();
                //2.创建Request对象，设置一个url地址（百度地址）,设置请求方式。
                Request request = new Request.Builder().url("http://"+tmp[0]+"/appHeart?t="+t+"&sign="+sign).method("GET",null).build();
                //3.创建一个call对象,参数就是Request请求对象
                Call call = okHttpClient.newCall(request);
                //4.请求加入调度，重写回调方法
                call.enqueue(new Callback() {
                    //请求失败执行的方法
                    @Override
                    public void onFailure(Call call, IOException e) {

                    }
                    //请求成功执行的方法
                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        Log.d(TAG, "onResponse: "+response.body().string());
                        isOk = true;

                    }
                });
                if (tmp[0].indexOf("localhost")>=0){
                    Toast.makeText(MainActivity.this, "配置信息错误，本机调试请访问 本机局域网IP:8080(如192.168.1.101:8080) 获取配置信息进行配置!", Toast.LENGTH_LONG).show();

                    return;
                }
                //将扫描出的信息显示出来
                txthost.setText(" 通知地址："+tmp[0]);
                txtkey.setText(" 通讯密钥："+tmp[1]);
                host = tmp[0];
                key = tmp[1];

                //步骤2-1：创建一个SharedPreferences.Editor接口对象，lock表示要写入的XML文件名，MODE_WORLD_WRITEABLE写操作
                SharedPreferences.Editor editor = getSharedPreferences("vone", MODE_PRIVATE).edit();
                //步骤2-2：将获取过来的值放入文件
                editor.putString("host", host);
                editor.putString("key", key);
                //步骤3：提交
                editor.commit();

            }
        });
        builder.show();

    }
    //启动服务
    public void doStart(View view) {
        if (isOk==false){
            Toast.makeText(MainActivity.this, "请您先配置!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isAccessibilitySettingsOn(this)) {
            Toast.makeText(MainActivity.this, "辅助功能未开启，请您前往开启!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            return;
        }

//        if(!notificationListenerEnable()){
//            Toast.makeText(MainActivity.this, "通知使用权权限未授予，请您前往授权!", Toast.LENGTH_SHORT).show();
//            gotoNotificationAccessSetting(this);
//            return;
//        }

        if (btnStart.getText().equals("开启服务")){
            btnStart.setText("检测服务状态");
            Toast.makeText(MainActivity.this, "开启成功!", Toast.LENGTH_SHORT).show();
        }else{

            String t = String.valueOf(new Date().getTime());
            String sign = md5(t+key);

            //1.创建OkHttpClient对象
            OkHttpClient okHttpClient = new OkHttpClient();
            //2.创建Request对象，设置一个url地址（百度地址）,设置请求方式。
            Request request = new Request.Builder().url("http://"+host+"/appHeart?t="+t+"&sign="+sign).method("GET",null).build();
            //3.创建一个call对象,参数就是Request请求对象
            Call call = okHttpClient.newCall(request);
            //4.请求加入调度，重写回调方法
            call.enqueue(new Callback() {
                //请求失败执行的方法
                @Override
                public void onFailure(Call call, IOException e) {
                    Looper.prepare();
                    Toast.makeText(MainActivity.this, "心跳状态错误，请检查配置是否正确!", Toast.LENGTH_SHORT).show();
                    Looper.loop();
                }
                //请求成功执行的方法
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    //Log.d(TAG, "onResponse heard: "+response.body().string());
                    Looper.prepare();
                    Toast.makeText(MainActivity.this, "程序运行正常，心跳返回："+response.body().string(), Toast.LENGTH_LONG).show();
                    Looper.loop();
                }
            });



        }
    }



    private boolean isAccessibilitySettingsOn(Context mContext) {
        int accessibilityEnabled = 0;
        // TestService为对应的服务
        final String service = getPackageName() + "/" + NeNotificationService.class.getCanonicalName();
        Log.i(TAG, "service:" + service);
        // com.z.buildingaccessibilityservices/android.accessibilityservice.AccessibilityService
        try {
            accessibilityEnabled = Settings.Secure.getInt(mContext.getApplicationContext().getContentResolver(),
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
            Log.v(TAG, "accessibilityEnabled = " + accessibilityEnabled);
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "Error finding setting, default accessibility to not found: " + e.getMessage());
        }
        TextUtils.SimpleStringSplitter mStringColonSplitter = new TextUtils.SimpleStringSplitter(':');

        if (accessibilityEnabled == 1) {
            Log.v(TAG, "***ACCESSIBILITY IS ENABLED*** -----------------");
            String settingValue = Settings.Secure.getString(mContext.getApplicationContext().getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            // com.z.buildingaccessibilityservices/com.z.buildingaccessibilityservices.TestService
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue);
                while (mStringColonSplitter.hasNext()) {
                    String accessibilityService = mStringColonSplitter.next();

                    Log.v(TAG, "-------------- > accessibilityService :: " + accessibilityService + " " + service);
                    if (accessibilityService.equalsIgnoreCase(service)) {
                        Log.v(TAG, "We've found the correct setting - accessibility is switched on!");
                        return true;
                    }
                }
            }
        } else {
            Log.v(TAG, "***ACCESSIBILITY IS DISABLED***");
        }
        return false;
    }


    private boolean notificationListenerEnable() {
        boolean enable = false;
        String packageName = getPackageName();
        String flat= Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");
        if (flat != null) {
            enable= flat.contains(packageName);
        }
        return enable;
    }
    private boolean gotoNotificationAccessSetting(Context context) {
        try {
            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch(ActivityNotFoundException e) {
            try {
                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ComponentName cn = new ComponentName("com.android.settings","com.android.settings.Settings$NotificationAccessSettingsActivity");
                intent.setComponent(cn);
                intent.putExtra(":settings:show_fragment", "NotificationAccessSettings");
                context.startActivity(intent);
                return true;
            } catch(Exception ex) {
                ex.printStackTrace();
            }
            return false;
        }
    }




    public void checkPush(View v){
        Notification mNotification;
        NotificationManager mNotificationManager;
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        mNotification = new NotificationCompat.Builder(this)
                // 设置小图标
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker("这是一条测试推送信息")
                // 设置标题
                .setContentTitle("V免签测试推送")
                // 设置内容
                .setContentText("这是一条测试推送信息")
                // 设置Notification提示铃声为系统默认铃声
                .setSound(
                        RingtoneManager.getActualDefaultRingtoneUri(
                                getBaseContext(),
                                RingtoneManager.TYPE_NOTIFICATION))

                // 点击Notification的时候自动移除
                .build();

        Toast.makeText(MainActivity.this, "已推送信息，如果权限，那么将会有下一条提示！", Toast.LENGTH_SHORT).show();



        mNotificationManager.notify(id++, mNotification);
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



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //扫描结果回调
        if (requestCode == Constant.REQ_QR_CODE && resultCode == RESULT_OK) {
            Bundle bundle = data.getExtras();
            String scanResult = bundle.getString(Constant.INTENT_EXTRA_KEY_QR_SCAN);

            String[] tmp = scanResult.split("/");
            if (tmp.length!=2){
                Toast.makeText(MainActivity.this, "二维码错误，请您扫描网站上显示的二维码!", Toast.LENGTH_SHORT).show();
                return;
            }

            String t = String.valueOf(new Date().getTime());
            String sign = md5(t+tmp[1]);

            //1.创建OkHttpClient对象
            OkHttpClient okHttpClient = new OkHttpClient();
            //2.创建Request对象，设置一个url地址（百度地址）,设置请求方式。
            Request request = new Request.Builder().url("http://"+tmp[0]+"/appHeart?t="+t+"&sign="+sign).method("GET",null).build();
            //3.创建一个call对象,参数就是Request请求对象
            Call call = okHttpClient.newCall(request);
            //4.请求加入调度，重写回调方法
            call.enqueue(new Callback() {
                //请求失败执行的方法
                @Override
                public void onFailure(Call call, IOException e) {

                }
                //请求成功执行的方法
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    Log.d(TAG, "onResponse: "+response.body().string());
                    isOk = true;

                }
            });
            //将扫描出的信息显示出来
            txthost.setText(" 通知地址："+tmp[0]);
            txtkey.setText(" 通讯密钥："+tmp[1]);
            host = tmp[0];
            key = tmp[1];

            //步骤2-1：创建一个SharedPreferences.Editor接口对象，lock表示要写入的XML文件名，MODE_WORLD_WRITEABLE写操作
            SharedPreferences.Editor editor = getSharedPreferences("vone", MODE_WORLD_WRITEABLE).edit();
            //步骤2-2：将获取过来的值放入文件
            editor.putString("host", host);
            editor.putString("key", key);
            //步骤3：提交
            editor.commit();

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case Constant.REQ_PERM_CAMERA:
                // 摄像头权限申请
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 获得授权
                    startQrCode(null);
                } else {
                    // 被禁止授权
                    Toast.makeText(MainActivity.this, "请至权限中心打开本应用的相机访问权限", Toast.LENGTH_LONG).show();
                }
                break;
            case Constant.REQ_PERM_EXTERNAL_STORAGE:
                // 文件读写权限申请
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 获得授权
                    startQrCode(null);
                } else {
                    // 被禁止授权
                    Toast.makeText(MainActivity.this, "请至权限中心打开本应用的文件读写权限", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }



}
