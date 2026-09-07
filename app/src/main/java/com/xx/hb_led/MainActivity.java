package com.xx.hb_led;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.ts.hb_led.R;

public class MainActivity extends Activity {

    private static final String TAG = "hongbin";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 启动配置管理（单例，整个应用生命周期内只启动一次）
        // 包含：加载配置 + 启动 FileObserver 监听配置文件变化
        ConfigManager.getInstance().start();

        // 延迟跳转（给串口设备初始化时间）
        getWindow().getDecorView().postDelayed(new Runnable() {
            @Override
            public void run() {
                jumpToMode();
            }
        }, 5000);
    }

    /**
     * 根据模式跳转到对应界面
     */
    private void jumpToMode() {
        int mode = ScreenConstant._MODE;
        if (mode == 1) {
            Log.d(TAG, "滚动模式：" + mode);
            startActivity(new Intent(MainActivity.this, Mode1Activity.class));
        } else if (mode == 2) {
            Log.d(TAG, "翻页模式：" + mode);
            startActivity(new Intent(MainActivity.this, Mode2Activity.class));
        } else {
            Log.d(TAG, "其他模式：" + mode);
        }
        // 注意：不调用 finish()，让 MainActivity 保留在后台
        // ConfigManager 的 FileObserver 是单例级别的，不依赖 Activity 生命周期
    }
}
