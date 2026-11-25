package com.grad.food;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 食品安全留样与检测管理系统启动类。
 * 开启定时任务：每小时计算留样到期、逾期未销毁与缺失记录，并推送站内消息。
 */
@SpringBootApplication
@MapperScan("com.grad.food.mapper")
@EnableScheduling
public class FoodApplication {
    public static void main(String[] args) {
        SpringApplication.run(FoodApplication.class, args);
    }
}
