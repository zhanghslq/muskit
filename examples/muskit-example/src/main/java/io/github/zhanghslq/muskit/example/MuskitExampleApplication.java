package io.github.zhanghslq.muskit.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Muskit 功能示例应用入口。
 *
 * @author zhs
 * @since 2026-08-20
 */
@SpringBootApplication
public class MuskitExampleApplication {

    /**
     * 创建示例应用入口对象。
     */
    public MuskitExampleApplication() {
    }

    /**
     * 启动示例应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MuskitExampleApplication.class, args);
    }
}
