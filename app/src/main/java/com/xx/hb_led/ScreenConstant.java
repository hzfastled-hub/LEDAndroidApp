package com.xx.hb_led;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ScreenConstant {

    static boolean debug = true;

    static int _MODE = 0;//1滚动；2翻页
    static int _UpdateTime = 5;
    static String _ID = "";
    static String _TITLE = "88";
    static String _TITLE2 = "AB";
    // 使用 CopyOnWriteArrayList 保证串口读取线程与配置加载线程并发安全
    static final List<String> _MAP = new CopyOnWriteArrayList<>();
//    static {
//        classArray.put(1, "检修");
//        classArray.put(2, "临修");
//        classArray.put(3, "质检");
//        classArray.put(4, "技改");
//        classArray.put(5, "长客");
//
//        classArray.put(6, "电务");
//        classArray.put(7, "客运");
//        classArray.put(8, "华铁");
//        classArray.put(9, "华利");
//        classArray.put(10, "瑞联");
//
//        classArray.put(11, "迅安保洁");
//        classArray.put(12, "迅安座椅");
//        classArray.put(13, "宝和迪奥");
//        classArray.put(14, "文广");
//        classArray.put(15, "乘务");
//
//        classArray.put(16, "协创");
//        classArray.put(17, "通用");
//        classArray.put(18, "");
//        classArray.put(19, "");
//        classArray.put(20, "");
//
//        classArray.put(21, "");
//        classArray.put(22, "");
//        classArray.put(23, "");
//        classArray.put(24, "");
//        classArray.put(25, "");
//
//    }

}
