package com.example.hbsz_idc_system_end;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 数据中心综合布线项目管理系统 - 后端服务启动类。
 * <p>
 * 作为 Spring Boot 应用的入口，负责初始化并启动整个后端服务。
 * <ul>
 *     <li>{@code @SpringBootApplication}：开启自动配置与组件扫描。</li>
 *     <li>{@code @MapperScan}：扫描并注册 MyBatis Mapper 接口（mapper 包）。</li>
 *     <li>{@code @EnableScheduling}：启用定时任务调度能力。</li>
 * </ul>
 *
 * @author hbsz
 */
@SpringBootApplication
@MapperScan("com.example.hbsz_idc_system_end.mapper")
@EnableScheduling
public class HbszIdcSystemEndApplication {

    /**
     * 应用程序主入口，启动 Spring Boot 容器。
     *
     * @param args 命令行启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(HbszIdcSystemEndApplication.class, args);
    }

}
