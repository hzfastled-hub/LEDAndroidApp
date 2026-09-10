package com.xx.hb_led;


import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Toast;
import android.serialport.SerialPort;
import android.serialport.SerialPortFinder;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ts.hb_led.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Mode1Activity extends Activity implements ConfigManager.OnConfigChangedListener {
    public SerialPortFinder mSerialPortFinder = new SerialPortFinder();
    private SerialPort mSerialPort = null;
    protected OutputStream mOutputStream;
    private InputStream mInputStream;
    private ReadThread mReadThread;

    private TextView tv1, tv2;
    private ScrollView scrollView;
    private LinearLayout layout;
    private final List<String> showClass = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mode1);
        tv1 = (TextView) findViewById(R.id.tv1);
        tv2 = (TextView) findViewById(R.id.tv2);
        scrollView = (ScrollView) findViewById(R.id.sv);
        layout = (LinearLayout) findViewById(R.id.main_layout);
        tv1.setText(ConfigManager.getInstance().getTitle());
        tv2.setText(ConfigManager.getInstance().getTitle2());

        // 显示配置加载状态（调试用）
        String debugInfo = ConfigManager.getInstance().getDebugInfo();
        Log.d("hongbin", "配置状态:\n" + debugInfo);
        Toast.makeText(this, debugInfo, Toast.LENGTH_LONG).show();

        // 监听配置变更，热更新标题
        ConfigManager.getInstance().addListener(this);
//        switch (_TITLE.length()){
//            case 2:
//                tv1.setTextSize(TypedValue.COMPLEX_UNIT_PX, 48);
//                break;
//            case 3:
//                tv1.setTextSize(TypedValue.COMPLEX_UNIT_PX, 33);
//                break;
//            default:
//                tv1.setTextSize(TypedValue.COMPLEX_UNIT_PX, 64);
//                break;
//        }

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
                    Log.d("hongbin", "SerialPort OK");
                } catch (Exception e){
                    Log.d("hongbin", e.toString());
                    e.printStackTrace();
                }

                mReadThread = new ReadThread();
                mReadThread.start();

                SleepThread sleepThread = new SleepThread();
                sleepThread.start();
            }
        }).start();


//        changeShowClass(true, "讯安保洁");
//        StringBuilder str = new StringBuilder("技改");
//        if(str.length() == 3){
//            str.insert(1, " ");
//            str.insert(3, " ");
//        } else if(str.length() == 2){
//            str.insert(1, "      ");
//        }
//        changeShowClass(true, str.toString());
//        StringBuilder str2 = new StringBuilder("乘务");
//        if(str2.length() == 3){
//            str2.insert(1, " ");
//            str2.insert(3, " ");
//        } else if(str2.length() == 2){
//            str2.insert(1, "      ");
//        }
//        changeShowClass(true, str2.toString());
//        changeShowClass(true, "似懂非懂");
//        changeShowClass(true, "雷的");
//        changeShowClass(true, "稳定人");

        updateTextView();


        findViewById(R.id.click).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                changeShowClass(false, "似懂非懂");
//                updateTextView();
            }
        });
    }

    private void updateTextView() {
        layout.removeAllViews();
        if(showClass.size() == 0){
            tv1.setTextColor(Color.GREEN);
            tv2.setTextColor(Color.GREEN);
            return;
        } else {
            tv1.setTextColor(Color.RED);
            tv2.setTextColor(Color.RED);
        }
        for(String str:showClass){
            TextView tv = new TextView(this);
            tv.setGravity(Gravity.CENTER);
            tv.setText(str);
            tv.setSingleLine();
            tv.setTextColor(Color.RED);
            tv.setBackgroundColor(Color.BLACK);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, 16);
            ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(64, 21);
            tv.setLayoutParams(layoutParams);
            tv.setIncludeFontPadding(false);
            tv.setLineSpacing(-1, 1.5f);
            layout.addView(tv);
        }
    }

    private class SleepThread extends Thread {
        @Override
        public void run() {
            super.run();
            Log.d("hongbin", "SleepThread OK");
            while (true) {
                try {
                    if(scrollView.canScrollVertically(1)){
                        scrollView.smoothScrollBy(0, 1);
                    } else if(scrollView.canScrollVertically(-1)){
                        scrollView.scrollTo(0, 0);
                    }
                    Thread.sleep(ConfigManager.getInstance().getUpdateTime() * 10);
                } catch (InterruptedException e) {
                    Log.d("hongbin", "SleepThread E:" + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    private class ReadThread extends Thread {

        @Override
        public void run() {
            super.run();
            Log.d("hongbin", "ReadThread OK");
            while (!isInterrupted()) {
                int size;
                try {
                    byte[] buffer = new byte[512];
                    if (mInputStream == null)
                        return;
                    size = mInputStream.read(buffer);
                    if (size > 0) {
                        printByteArray("ReadThread", buffer);
//                        Log.d("hongbin", "toString:" + new String(buffer));
                        onDataReceived(buffer);
                    }
                } catch (Exception e) {
                    Log.d("hongbin", "ReadThread E:"+ e.getMessage());
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
                if(length<9){
                    return;
                }
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

                final boolean t = realByte[6] == (byte)0x88;
                if(t){
                    Log.d("hongbin", "显示");
                } else {
                    Log.d("hongbin", "关闭");
                }

                final String clsName = className;
                layout.post(new Runnable() {
                    @Override
                    public void run() {
                        changeShowClass(t, clsName);
                        updateTextView();
                    }
                });


                onDataSend(realByte);
            }
        }
    }
    //当显示数量超过3个时需要滚动，滚动需要将最后一个显示条目完全划出屏幕，所以最后面加三个空条目
    private void changeShowClass(boolean isShow, String className){
        StringBuilder str = new StringBuilder(className);
        if(str.length() == 3){
            str.insert(1, " ");
            str.insert(3, " ");
        } else if(str.length() == 2){
            str.insert(1, "      ");
        }
        className = str.toString();
        Log.d("hongbin", "变更内容：" + className);
        if(isShow){
            if (!showClass.contains(className)){
                if (showClass.size()>6 && TextUtils.equals(" ", showClass.get(showClass.size()-1))){
                    showClass.remove(showClass.size()-1);
                    showClass.remove(showClass.size()-1);
                    showClass.remove(showClass.size()-1);
                }
                showClass.add(className);
                if(showClass.size()>3){
                    showClass.add(" ");
                    showClass.add(" ");
                    showClass.add(" ");
                }
            }
            Log.d("hongbin", "新增后内容：");
        } else {
            showClass.remove(className);
            if(showClass.size()==6 && TextUtils.equals(" ", showClass.get(showClass.size()-1))){
                showClass.remove(showClass.size()-1);
                showClass.remove(showClass.size()-1);
                showClass.remove(showClass.size()-1);
            }
            Log.d("hongbin", "删减后内容：");
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
        // 配置变更时更新标题显示（在主线程执行）
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tv1.setText(ConfigManager.getInstance().getTitle());
                tv2.setText(ConfigManager.getInstance().getTitle2());
                Toast.makeText(Mode1Activity.this, "配置已更新: " + ConfigManager.getInstance().getMapSize() + "个班组", Toast.LENGTH_SHORT).show();
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