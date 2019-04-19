package com.vone.vmq;

import android.app.Application;

public class MyData extends Application {
    private String host;
    private String key;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
