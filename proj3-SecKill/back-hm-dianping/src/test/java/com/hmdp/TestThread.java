package com.hmdp;

public class TestThread {
    public static void main(String[] args) {
        // 获取可用的处理器核心数
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("你的电脑逻辑核心数是：" + cores);
    }


}