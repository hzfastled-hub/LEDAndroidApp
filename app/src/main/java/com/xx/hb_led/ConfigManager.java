package com.xx.hb_led;

import android.os.Handler;
import android.os.FileObserver;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.xx.hb_led.ScreenConstant._ID;
import static com.xx.hb_led.ScreenConstant._MAP;
import static com.xx.hb_led.ScreenConstant._MODE;
import static com.xx.hb_led.ScreenConstant._TITLE;
import static com.xx.hb_led.ScreenConstant._TITLE2;
import static com.xx.hb_led.ScreenConstant._UpdateTime;

/**
 * 配置管理器（单例）
 *
 * 解决 ADB 推送配置不稳定的核心问题：
 * 1. 统一管理 FileObserver，避免 Activity.finish() 后监听失效
 * 2. 配置加载带完整性校验 + 重试，避免读到写入中的半文件
 * 3. _MAP 使用 CopyOnWriteArrayList + 同步块，双重保证串口线程读取安全
 * 4. 配置变更时通知所有监听者，UI 可热更新
 */
public class ConfigManager {

    private static final String TAG = "hongbin";
    private static final String CONFIG_FILE = "LED_CONFIG.txt";
    private static final String SERIAL_FLAG = "led_check_serial";
    private static final long RELOAD_INTERVAL = 1000; // 防抖：1秒内只重载一次

    private static ConfigManager sInstance;

    private FileObserver mFileObserver;
    private long mLastLoadTime = 0;
    private long mLastModified = 0;
    private boolean mStarted = false;
    private Handler mPollHandler;

    // 配置变更监听者列表（线程安全）
    private final List<OnConfigChangedListener> mListeners = new CopyOnWriteArrayList<>();

    public interface OnConfigChangedListener {
        void onConfigChanged();
    }

    private ConfigManager() {
    }

    public static synchronized ConfigManager getInstance() {
        if (sInstance == null) {
            sInstance = new ConfigManager();
        }
        return sInstance;
    }

    /**
     * 启动配置管理：加载配置 + 启动文件监听
     * 在 Application 或 MainActivity.onCreate 中调用一次即可
     */
    public synchronized void start() {
        if (mStarted) {
            return;
        }
        loadConfig();
        startFileObserver();
        startPolling();
        mStarted = true;
    }

    /**
     * 停止文件监听
     */
    public synchronized void stop() {
        if (mFileObserver != null) {
            mFileObserver.stopWatching();
            mFileObserver = null;
        }
        if (mPollHandler != null) {
            mPollHandler.removeCallbacksAndMessages(null);
            mPollHandler = null;
        }
        mStarted = false;
    }

    public void addListener(OnConfigChangedListener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(OnConfigChangedListener listener) {
        mListeners.remove(listener);
    }

    private void notifyConfigChanged() {
        for (OnConfigChangedListener listener : mListeners) {
            try {
                listener.onConfigChanged();
            } catch (Exception e) {
                Log.d(TAG, "通知监听者异常: " + e.getMessage());
            }
        }
    }

    /**
     * 加载配置文件（带完整性校验和重试）
     * 所有对 ScreenConstant 静态字段的写操作都在此方法内完成，
     * 通过 synchronized 保证与串口读取线程的可见性。
     */
    public synchronized boolean loadConfig() {
        File sdCard = new File("/sdcard");
        File file = new File(sdCard, CONFIG_FILE);

        if (!file.exists() || !file.canRead()) {
            Log.d(TAG, "配置文件不存在或不可读");
            onError();
            return false;
        }

        mLastModified = file.lastModified();

        // 重试机制：最多读3次，每次间隔200ms，避免读到写入中的文件
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                StringBuilder stringBuilder = new StringBuilder();
                BufferedReader reader = null;
                try {
                    reader = new BufferedReader(new FileReader(file));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stringBuilder.append(line);
                        stringBuilder.append("\n");
                    }
                } finally {
                    if (reader != null) {
                        try { reader.close(); } catch (Exception ignored) {}
                    }
                }

                String config = stringBuilder.toString();

                // 完整性校验
                if (config == null || config.trim().isEmpty()) {
                    Log.d(TAG, "配置文件为空，重试 " + (attempt + 1) + "/3");
                    Thread.sleep(200);
                    continue;
                }

                JSONObject object = new JSONObject(config);

                // 校验关键字段
                String serial = object.optString("_SERIAL", "");
                if (!SERIAL_FLAG.equals(serial)) {
                    Log.d(TAG, "_SERIAL 标识不匹配，重试 " + (attempt + 1) + "/3");
                    Thread.sleep(200);
                    continue;
                }

                JSONArray mapArray = object.optJSONArray("_MAP");
                if (mapArray == null || mapArray.length() == 0) {
                    Log.d(TAG, "_MAP 为空，重试 " + (attempt + 1) + "/3");
                    Thread.sleep(200);
                    continue;
                }

                // 校验通过，解析配置（写操作在 synchronized 块内，保证线程安全）
                _MODE = object.optInt("_MODE", 0);
                _ID = object.optString("_ID", "");
                _TITLE = object.optString("_TITLE", "88");
                _TITLE2 = object.optString("_TITLE2", "AB");
                _UpdateTime = object.optInt("_UpdateTime", 5);

                // 先构建新列表，再整体替换，避免串口线程读到中间状态
                List<String> newMap = new ArrayList<>();
                for (int i = 0; i < mapArray.length(); i++) {
                    newMap.add(mapArray.getString(i));
                }
                _MAP.clear();
                _MAP.addAll(newMap);

                mLastLoadTime = System.currentTimeMillis();
                Log.d(TAG, "配置加载成功: MODE=" + _MODE + " ID=" + _ID
                        + " MAP大小=" + _MAP.size() + " UpdateTime=" + _UpdateTime);

                // 通知监听者配置已变更
                notifyConfigChanged();
                return true;

            } catch (Exception e) {
                Log.d(TAG, "配置解析异常(尝试 " + (attempt + 1) + "/3): " + e.getMessage());
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }

        // 3次都失败
        Log.d(TAG, "配置加载最终失败");
        onError();
        return false;
    }

    /**
     * 启动文件监听器，支持配置热重载
     */
    private void startFileObserver() {
        File configFile = new File("/sdcard", CONFIG_FILE);
        String parentPath = configFile.getParent();

        if (parentPath == null) {
            Log.d(TAG, "无法获取配置文件父目录");
            return;
        }

        mFileObserver = new FileObserver(parentPath, FileObserver.CLOSE_WRITE
                | FileObserver.MODIFY | FileObserver.MOVED_TO) {
            @Override
            public void onEvent(int event, String path) {
                if (path == null || !CONFIG_FILE.equals(path)) {
                    return;
                }

                // 防抖：避免短时间内多次触发
                long now = System.currentTimeMillis();
                if (now - mLastLoadTime < RELOAD_INTERVAL) {
                    return;
                }

                Log.d(TAG, "检测到配置文件变化，重新加载...");
                loadConfig();
            }
        };
        mFileObserver.startWatching();
        Log.d(TAG, "文件监听器已启动: " + parentPath);
    }

    /**
     * 定时轮询：每 3 秒检查配置文件修改时间，变化则重载。
     * 作为 FileObserver 的兜底方案（adb push 有时不触发 inotify 事件）。
     */
    private void startPolling() {
        mPollHandler = new Handler(Looper.getMainLooper());
        final Runnable pollTask = new Runnable() {
            @Override
            public void run() {
                try {
                    File file = new File("/sdcard", CONFIG_FILE);
                    if (file.exists()) {
                        long modified = file.lastModified();
                        if (modified != 0 && modified != mLastModified) {
                            long now = System.currentTimeMillis();
                            if (now - mLastLoadTime >= RELOAD_INTERVAL) {
                                Log.d(TAG, "轮询检测到配置变化，重新加载...");
                                loadConfig();
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.d(TAG, "轮询异常: " + e.getMessage());
                }
                if (mPollHandler != null) {
                    mPollHandler.postDelayed(this, 3000);
                }
            }
        };
        mPollHandler.postDelayed(pollTask, 3000);
        Log.d(TAG, "定时轮询已启动（每3秒）");
    }

    /**
     * 安全地获取班组名称（线程安全，越界返回空字符串）
     */
    public String getClassName(int index) {
        synchronized (this) {
            if (index < 0 || index >= _MAP.size()) {
                return "";
            }
            return _MAP.get(index);
        }
    }

    /**
     * 安全地获取班组数量
     */
    public int getMapSize() {
        synchronized (this) {
            return _MAP.size();
        }
    }

    /**
     * 安全地获取屏号（整数），解析失败返回 -1
     */
    public int getScreenId() {
        synchronized (this) {
            try {
                return Integer.parseInt(_ID);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    }

    public int getUpdateTime() {
        synchronized (this) {
            return _UpdateTime;
        }
    }

    public String getTitle() {
        synchronized (this) {
            return _TITLE;
        }
    }

    public String getTitle2() {
        synchronized (this) {
            return _TITLE2;
        }
    }

    public int getMode() {
        synchronized (this) {
            return _MODE;
        }
    }

    private void onError() {
        Log.d(TAG, "onError");
        _ID = "-1";
        _TITLE = "异常";
        _TITLE2 = "00";
    }
}
