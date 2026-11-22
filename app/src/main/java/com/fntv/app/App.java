package com.fntv.app;

import androidx.multidex.MultiDexApplication;

import com.fntv.app.core.ThemeMode;

/**
 * 应用入口：只负责全局初始化（主题、夜间模式）。
 */
public class App extends MultiDexApplication {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeMode.apply(this);
    }
}
