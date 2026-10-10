package com.example.hbsz_idc_system_end.controller;

import cn.hutool.captcha.AbstractCaptcha;
import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.generator.RandomGenerator;
import com.example.hbsz_idc_system_end.common.annotation.RateLimit;
import com.example.hbsz_idc_system_end.common.constant.CommonConstant;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.utils.RedisUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 验证码控制器。
 * <p>
 * 业务模块：登录安全（图形验证码）。
 * 基础请求路径：{@code /captcha}。
 * 生成图片验证码并将答案缓存至 Redis，供登录环节进行人机校验。
 * <p>
 * 安全修复：原实现返回明文算术题（如 "3 + 5 = ?"），脚本可直接解析题目并自动求解，
 * 人机校验形同虚设。改为服务端渲染图片验证码（干扰线 + 字符扭曲），
 * 仅下发 base64 图片，答案不下发，仅在服务端 Redis 中留存。
 *
 * @author hbsz
 */
@Tag(name = "验证码", description = "图形验证码生成")
@RestController
@RequestMapping("/captcha")
public class CaptchaController {

    /** 验证码字符集：剔除易混淆字符（0/O、1/I/l）的字母数字组合 */
    private static final String CAPTCHA_CHAR_SET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** 验证码字符数 */
    private static final int CAPTCHA_LENGTH = 4;

    /** 验证码有效期（秒） */
    private static final int CAPTCHA_EXPIRE_SECONDS = 300;

    private final RedisUtils redisUtils;

    public CaptchaController(RedisUtils redisUtils) {
        this.redisUtils = redisUtils;
    }

    /**
     * 获取图片验证码。
     * <p>HTTP：GET /captcha。生成 4 位字母数字图片验证码（带干扰线），
     * 以 {@code hbsz:captcha:{captchaId}} 为键将答案写入 Redis（5 分钟过期），
     * 向前端返回验证码标识与 base64 图片（data URL），答案不下发。
     * 字符集剔除 0/O、1/I/l 等易混淆字符，降低用户输错率。
     *
     * @return 包含验证码标识（captchaId）与图片数据（image，base64 data URL）的响应
     */
    @Operation(summary = "获取验证码", description = "生成图片验证码，返回 captchaId 和 base64 图片")
    // M3修复：验证码生成接口为公开接口，缺少限流会被恶意刷取产生大量 Redis 垃圾键，添加限流（60秒内最多30次）
    @RateLimit(key = "captcha", period = 60, count = 30)
    @GetMapping
    public Result<Map<String, String>> generate() {
        // 生成图片验证码：120x40，4 位字符，30 条干扰线
        // 注意声明为 AbstractCaptcha：setGenerator/getImageBase64Data 定义于抽象基类，ICaptcha 接口未提供
        AbstractCaptcha captcha = CaptchaUtil.createLineCaptcha(120, 40, CAPTCHA_LENGTH, 30);
        captcha.setGenerator(new RandomGenerator(CAPTCHA_CHAR_SET, CAPTCHA_LENGTH));
        // createCode 触发使用自定义生成器重新生成验证码文本与图片
        captcha.createCode();

        String captchaId = UUID.randomUUID().toString().replace("-", "");
        // 答案统一大写存入 Redis，比对时忽略大小写（见 AuthServiceImpl.validateCaptcha）
        redisUtils.set(CommonConstant.REDIS_CAPTCHA_PREFIX + captchaId,
                captcha.getCode().toUpperCase(), CAPTCHA_EXPIRE_SECONDS);

        Map<String, String> result = new HashMap<>();
        result.put("captchaId", captchaId);
        // getImageBase64Data 返回带 "data:image/png;base64," 前缀，前端可直接用于 <img src>
        result.put("image", captcha.getImageBase64Data());
        return Result.success(result);
    }
}
