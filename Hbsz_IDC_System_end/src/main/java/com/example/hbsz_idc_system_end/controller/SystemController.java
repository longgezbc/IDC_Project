package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统健康检查控制器。
 * <p>
 * 业务模块：系统管理（基础运维）。
 * 基础请求路径：{@code /system}。
 * 提供服务健康探测与系统基础信息查询接口，常用于部署验证、负载均衡健康检查及监控。
 *
 * @author hbsz
 */
@Tag(name = "系统管理", description = "系统健康检查、基础信息接口")
@RestController
@RequestMapping("/system")
public class SystemController {

    /**
     * 健康检查。
     * <p>HTTP：GET /system/health。返回服务运行状态与当前时间戳，可用于存活探测。
     *
     * @return 包含状态（status=UP）与时间戳（timestamp）的响应
     */
    @Operation(summary = "健康检查", description = "检查服务是否正常运行")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("timestamp", System.currentTimeMillis());
        return Result.success("服务运行正常", data);
    }

    /**
     * 系统信息。
     * <p>HTTP：GET /system/info。返回系统名称、版本号及运行环境（Java 版本、操作系统名称）。
     *
     * @return 包含系统基础信息的响应
     */
    @Operation(summary = "系统信息", description = "获取系统基本信息")
    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "数据中心综合布线项目管理系统");
        data.put("version", "V1.0.0");
        data.put("javaVersion", System.getProperty("java.version"));
        data.put("osName", System.getProperty("os.name"));
        return Result.success(data);
    }

}
