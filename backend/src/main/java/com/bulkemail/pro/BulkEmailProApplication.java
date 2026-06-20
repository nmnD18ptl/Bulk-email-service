package com.bulkemail.pro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class BulkEmailProApplication {

    public static void main(String[] args) {
        // Create data directory if not exists
        String userHome = System.getProperty("user.home");
        new java.io.File(userHome + "/BulkEmailPro/data").mkdirs();
        new java.io.File(userHome + "/BulkEmailPro/logs").mkdirs();
        new java.io.File(userHome + "/BulkEmailPro/exports").mkdirs();

        SpringApplication.run(BulkEmailProApplication.class, args);
    }
}
