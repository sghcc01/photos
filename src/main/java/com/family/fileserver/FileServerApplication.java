package com.family.fileserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FileServerApplication {

    // 移除冗余的 FileUtils 注入（FileUtils 不是Spring Bean，无需构造函数注入）
    public FileServerApplication() {
    }

    public static void main(String[] args) {
        SpringApplication.run(FileServerApplication.class, args);
    }
}