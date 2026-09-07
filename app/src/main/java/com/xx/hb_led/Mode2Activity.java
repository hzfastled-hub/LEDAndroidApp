package com.xx.hb_led;


import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.serialport.SerialPort;
import android.serialport.SerialPortFinder;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.ts.hb_led.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Mode2Activity extends Activity implements ConfigManager.OnConfigChangedListener {
    public SerialPortFinder mSerialPortFinder = new SerialPortFinder();
    private SerialPort mSerialPort = null;
    protected OutputStream mOutputStream;
    private InputStream mInputStream;
    private ReadThread mReadThread;

    private TextView titleView;
    private int layoutIndex = 0;//用于翻页下标
    private FrameLayout layout;
    private final List<String> showClass = new ArrayList<>();
    private final List<TextView> tvList = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mode2);
        titleView = (TextView) findViewById(R.id.tv1);
        layout = (FrameLayout) findViewById(R.id.main_layout);

        // 监听配置变更，热更新标题
        ConfigManager.getInstance().addListener(this);

        new Thread(new Runnable() {
            @Override
            public void run() {

                String[] entries = mSerialPortFinder.getAllDevices();
                String[] entryValues = mSerialPortFinder.getAllDevicesPath();

                Log.d("hongbin", Arrays.toString(entries));
                Log.d("hongbin", Arrays.toString(entryValues));

                try {
                    SerialPort serialPort = SerialPort //
                            .newBuilder("/dev/ttyS3", 9600) // 串口地址地址，波特率
                            .parity(0) // 校验位；0:无校验位(NONE，默认)；1:奇校验位(ODD);2:偶校验位(EVEN)
                            .dataBits(8) // 数据位,默认8；可选值为5~8
                            .stopBits(1) // 停止位，默认1；1:1位停止位；2:2位停止位
                            .build();

                    mSerialPort = serialPort;
                    mInputStream = mSerialPort.getInputStream();
                    mOutputStream = mSerialPort.getOutputStream();
                } catch (Exception e){
                    Log.d("hongbin", e.toString());
                    e.printStackTrace();
                }
                Log.d("hongbin", "SerialPort OK");

                mReadThread = new ReadThread();
                mReadThread.start();

                SleepThread sleepThread = new SleepThread();
                sleepThread.start();
            }
        }).start();

    }

    private void onError(){
        // 配置异常时刷新显示
        layout.post(new Runnable() {
            @Override
            public void run() {
                updateLayout();
            }
        });
    }

    Runnable viewJob = new Runnable() {
        @Override
        public void run() {
            updateLayout();
        }
    };

    private void updateLayout() {
        titleView.setText(ConfigManager.getInstance().getTitle());
        if(layoutIndex>=tvList.size()){
            layoutIndex = 0;
        }
        layout.removeAllViews();
        if(tvList.size() == 0){
            titleView.setTextColor(Color.GREEN);
            return;
        } else {
            titleView.setTextColor(Color.RED);
        }
        View child = tvList.get(layoutIndex);
        layout.addView(child);
        layoutIndex++;
    }

    private class SleepThread extends Thread {
        @Override
        public void run() {
            super.run();
            while (true) {
                layout.post(viewJob);
                try {
                    Thread.sleep(ConfigManager.getInstance().getUpdateTime() * 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class ReadThread extends Thread {

        @Override
        public void run() {
            super.run();

            while (!isInterrupted()) {
                int size;
                try {
                    byte[] buffer = new byte[512];
                    if (mInputStream == null) return;
                    size = mInputStream.read(buffer);
                    if (size > 0) {
                        printByteArray("ReadThread", buffer);
//                        Log.d("hongbin", "toString:" + new String(buffer));
                        onDataReceived(buffer);
                    }
                } catch (IOException e) {
                    Log.d("hongbin", "e:"+ "e1");
                    e.printStackTrace();
                    return;
                }
            }
            Log.d("hongbin", "ReadThread Over");
        }
    }

    private void onDataReceived(byte[] data){
        if(data != null && data.length>5){
            if(data[0] == 0x57 && data[1] == 0x4b && data[2] == 0x4c && data[3] == 0x59){
                int length = data[4];
                byte[] realByte = Arrays.copyOf(data, length);
                printByteArray("receive", realByte);

                //todo 调试时临时注释，打包记得注释打开
                if(realByte[length-1] == calculateXORChecksum(Arrays.copyOf(realByte, length-1))){
                    Log.d("hongbin", "XOR success");
                } else {
                    Log.d("hongbin", "XOR fail");
                    return;
                }

                int id = realByte[5];
                int screenId = ConfigManager.getInstance().getScreenId();
                Log.d("hongbin", "当前屏号：" + screenId + "  显示屏号：" + id);
                if (id != screenId){
                    return;
                }

                int index = (int)realByte[7]-1;
                if(index < 0 || index >= ConfigManager.getInstance().getMapSize()){
                    return;
                }

                String className = ConfigManager.getInstance().getClassName(index);
                if (className == null || className.isEmpty()){
                    return;
                }
                Log.d("hongbin", "班组信息：" + (int)realByte[7] + "【" + className + "】");

                boolean t = realByte[6] == (byte)0x88;
                if(t){
                    Log.d("hongbin", "显示");
                } else {
                    Log.d("hongbin", "关闭");
                }

                if(t){
                    if (!showClass.contains(className)){
                        showClass.add(className);
                    }
                } else {
                    showClass.remove(className);
                }

                layout.post(new Runnable() {
                    @Override
                    public void run() {
                        updateTextView();
                    }
                });


                onDataSend(realByte);
            }
        }
    }

    private void onDataSend(byte[] realByte) {
        byte[] sendByte = new byte[10];
        sendByte[0] = realByte[0];
        sendByte[1] = realByte[1];
        sendByte[2] = realByte[2];
        sendByte[3] = realByte[3];
        sendByte[4] = (byte)(0x0a);
        sendByte[5] = realByte[5];
        sendByte[6] = realByte[6];
        sendByte[7] = (byte)0x00;
        sendByte[8] = realByte[7];
        sendByte[9] = calculateXORChecksum(Arrays.copyOf(sendByte, sendByte.length-1));
        printByteArray("send", sendByte);

        try {
            mOutputStream.write(sendByte);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateTextView() {
        tvList.clear();
        int index = 0;
        StringBuilder builder = new StringBuilder();
        for(String str:showClass){
            index++;
            if(builder.length() == 0){
                builder.append(str);
            } else {
                builder.append("\n").append(str);
            }
            if(index % 3 == 0 || index == showClass.size()){
                TextView tv = new TextView(this);
                tv.setText(builder.toString());
                tv.setTextColor(Color.RED);
                tv.setBackgroundColor(Color.BLACK);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, 16);
                tv.setIncludeFontPadding(false);
                tv.setLineSpacing(-1, 1.2f);
                ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(64, 64);
                tv.setLayoutParams(layoutParams);
                tvList.add(tv);
                builder.setLength(0);
            }
        }
    }

    public static byte calculateXORChecksum(byte[] data) {
        byte checksum = 0;
        for (byte b : data) {
            checksum ^= b; // 对每个字节进行异或运算
        }
        return checksum;
    }

    public static void printByteArray(String by, byte[] array) {
        StringBuilder sb = new StringBuilder();
        for (byte b : array) {
            // 将每个字节转换为两位的16进制字符串，并添加到StringBuilder中
            sb.append(String.format("%02x", b)).append(" ");
        }
        Log.d("hongbin", by + " buffer:" + sb.toString());
    }

    @Override
    public void onConfigChanged() {
        // 配置变更时刷新翻页显示（在主线程执行）
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateLayout();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ConfigManager.getInstance().removeListener(this);
        if (mReadThread != null) {
            mReadThread.interrupt();
        }
        if (mSerialPort != null) {
            try {
                mSerialPort.close();
            } catch (Exception ignored) {
            }
        }
    }

}