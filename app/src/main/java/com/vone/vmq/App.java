package com.vone.vmq;

import android.app.Application;
import android.os.Process;
import android.util.Log;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                Utils.putStr(App.this, "exception:" + Log.getStackTraceString(throwable));
                Process.killProcess(Process.myPid());
            }
        });

    }
}
