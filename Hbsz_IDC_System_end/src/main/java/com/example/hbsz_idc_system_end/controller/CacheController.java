package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.utils.RedisUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 缓存管理控制器
 * 用于运维场景下验证 / 管理 Redis 缓存
 * <p>
 * 安全说明：所有接口均需 system:cache:manage 权限，避免任意登录用户读写敏感缓存
 * （如 JWT 黑名单、用户权限缓存等），防止权限提升与信息泄露。
 *
 * @author hbsz
 */
@Tag(name = "缓存管理", description = "Redis 缓存管理与测试接口（需要 system:cache:manage 权限）")
@RestController
@RequestMapping("/cache")
public class CacheController {

    private final RedisUtils redisUtils;

    public CacheController(RedisUtils redisUtils) {
        this.redisUtils = redisUtils;
    }

    /**
     * 允许操作的 Redis Key 前缀白名单（仅运维测试场景使用）
     */
    private static final List<String> ALLOWED_KEY_PREFIXES = Arrays.asList("cache:test:", "cache:debug:");

    /**
     * 受保护前缀：这些前缀的 key 不允许通过缓存管理接口直接操作。
     * <p>
     * 重构说明：原先使用宽泛的 {@code "hbsz:"} 通配会阻止所有缓存管理，
     * 且包含 {@code jwt_blacklist:} / {@code login_fail:} / {@code login_lock:} 等旧命名（现已统一为 hbsz: 命名空间）。
     * 改为精细化的 hbsz: 子前缀，仅保护安全敏感的内部缓存键，
     * 从而允许管理员查看业务缓存（如 dashboardSummary、systemConfig 等）。
     */
    private static final Set<String> PROTECTED_PREFIXES = Set.of(
            "hbsz:jwt:blacklist:",    // JWT 黑名单
            "hbsz:login:fail:",       // 登录失败计数
            "hbsz:login:lock:",       // 登录锁定
            "hbsz:user:perms:",       // 用户权限缓存
            "hbsz:user:roles:",       // 用户角色缓存
            "hbsz:captcha:",           // 验证码
            "hbsz:idempotent:"         // 幂等令牌
    );

    /**
     * 校验 Redis Key 前缀，防止通过缓存管理接口触碰敏感 key。
     * <p>
     * 安全：限制可操作的 Redis Key 前缀，防止触碰 JWT 黑名单、权限缓存等敏感 key。
     * 仅允许以 {@code cache:test:} / {@code cache:debug:} 开头的测试 key 进行读写删操作；
     * 若 key 命中受保护前缀（如 hbsz:jwt:blacklist:、hbsz:user:perms: 等），直接抛出业务异常拒绝。
     *
     * @param key 待校验的 Redis key
     */
    private void validateKeyPrefix(String key) {
        if (key == null || key.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_VALIDATE_FAILED, "缓存键不能为空");
        }
        // 安全：优先校验是否命中受保护前缀，命中即拒绝（给出明确告警便于排查）
        for (String protectedPrefix : PROTECTED_PREFIXES) {
            if (key.startsWith(protectedPrefix)) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR,
                        "禁止操作受保护的缓存键: " + protectedPrefix + "*");
            }
        }
        // 校验是否命中允许的前缀白名单，未命中则拒绝
        boolean allowed = false;
        for (String allowedPrefix : ALLOWED_KEY_PREFIXES) {
            if (key.startsWith(allowedPrefix)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "缓存键前缀非法，仅允许: " + ALLOWED_KEY_PREFIXES);
        }
    }

    /**
     * 设置缓存。
     * <p>HTTP：POST /cache/set。向 Redis 写入键值并设置过期时间（秒）。
     * 权限：{@code system:cache:manage}。
     *
     * @param key    缓存键
     * @param value  缓存值
     * @param expire 过期时间（秒），默认 60
     * @return 无业务数据的操作结果
     */
    @Operation(summary = "设置缓存", description = "向 Redis 写入缓存数据")
    @RequiresPermission("system:cache:manage")
    @PostMapping("/set")
    public Result<Void> set(
            @Parameter(description = "缓存键", required = true) @RequestParam String key,
            @Parameter(description = "缓存值", required = true) @RequestParam String value,
            @Parameter(description = "过期时间（秒），默认60秒") @RequestParam(defaultValue = "60") long expire) {
        // 安全：限制可操作的 Redis Key 前缀，防止触碰 JWT 黑名单、权限缓存等敏感 key
        validateKeyPrefix(key);
        redisUtils.set(key, value, expire, TimeUnit.SECONDS);
        return Result.success("缓存设置成功", null);
    }

    /**
     * 获取缓存。
     * <p>HTTP：GET /cache/get。按键读取 Redis 缓存值。
     * 权限：{@code system:cache:manage}。
     *
     * @param key 缓存键
     * @return 包含 key、value 及是否存在（exists）的响应
     */
    @Operation(summary = "获取缓存", description = "从 Redis 读取缓存数据")
    @RequiresPermission("system:cache:manage")
    @GetMapping("/get")
    public Result<Map<String, Object>> get(
            @Parameter(description = "缓存键", required = true) @RequestParam String key) {
        // 安全：限制可操作的 Redis Key 前缀，防止触碰 JWT 黑名单、权限缓存等敏感 key
        validateKeyPrefix(key);
        Object value = redisUtils.get(key);
        Map<String, Object> data = new HashMap<>();
        data.put("key", key);
        data.put("value", value);
        data.put("exists", value != null);
        return Result.success(data);
    }

    /**
     * 删除缓存。
     * <p>HTTP：DELETE /cache/delete。按键删除 Redis 缓存，键不存在时返回错误提示。
     * 权限：{@code system:cache:manage}。
     *
     * @param key 缓存键
     * @return 删除成功或缓存不存在的操作结果
     */
    @Operation(summary = "删除缓存", description = "从 Redis 删除缓存数据")
    @RequiresPermission("system:cache:manage")
    @DeleteMapping("/delete")
    public Result<Void> delete(
            @Parameter(description = "缓存键", required = true) @RequestParam String key) {
        // 安全：限制可操作的 Redis Key 前缀，防止触碰 JWT 黑名单、权限缓存等敏感 key
        validateKeyPrefix(key);
        boolean deleted = redisUtils.delete(key);
        return deleted ? Result.success("缓存删除成功", null) : Result.error("缓存不存在或已删除");
    }

    /**
     * 判断缓存是否存在。
     * <p>HTTP：GET /cache/exists。检查 Redis 中是否存在指定键。
     * 权限：{@code system:cache:manage}。
     *
     * @param key 缓存键
     * @return 包含 key 及是否存在（exists）的响应
     */
    @Operation(summary = "判断缓存是否存在", description = "检查 Redis 中是否存在指定键")
    @RequiresPermission("system:cache:manage")
    @GetMapping("/exists")
    public Result<Map<String, Object>> exists(
            @Parameter(description = "缓存键", required = true) @RequestParam String key) {
        // M4修复：与 set/get/delete 保持一致，先校验 key 前缀，防止通过 exists 探测敏感缓存键是否存在
        validateKeyPrefix(key);
        boolean exists = redisUtils.hasKey(key);
        Map<String, Object> data = new HashMap<>();
        data.put("key", key);
        data.put("exists", exists);
        return Result.success(data);
    }

    /**
     * 获取剩余过期时间。
     * <p>HTTP：GET /cache/expire。查询 Redis 中指定键的剩余过期时间（秒）。
     * 权限：{@code system:cache:manage}。
     *
     * @param key 缓存键
     * @return 包含 key 及剩余过期秒数（expireSeconds）的响应
     */
    @Operation(summary = "获取剩余过期时间", description = "获取 Redis 中指定键的剩余过期时间（秒）")
    @RequiresPermission("system:cache:manage")
    @GetMapping("/expire")
    public Result<Map<String, Object>> expire(
            @Parameter(description = "缓存键", required = true) @RequestParam String key) {
        // M4修复：与 set/get/delete 保持一致，先校验 key 前缀，防止通过 expire 探测敏感缓存键的过期时间
        validateKeyPrefix(key);
        long expire = redisUtils.getExpire(key);
        Map<String, Object> data = new HashMap<>();
        data.put("key", key);
        data.put("expireSeconds", expire);
        return Result.success(data);
    }

}
