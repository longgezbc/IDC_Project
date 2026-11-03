package com.example.hbsz_idc_system_end.utils;

import cn.hutool.crypto.digest.BCrypt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 密码加密工具类
 * <p>
 * 基于 BCrypt 算法（Hutool 实现）对用户密码进行单向加密与校验。
 * BCrypt 自带随机盐并可调节计算强度（cost factor），能有效抵御彩虹表与暴力破解，
 * 因此同一明文每次加密结果不同，校验时需使用 {@link #matches} 而非直接比较字符串。
 *
 * @author hbsz
 */
@Component
public class PasswordUtils {

    /**
     * BCrypt 计算强度（cost factor）。
     * <p>
     * 安全加固（P2-2）：原使用 Hutool 默认 cost=10，2026 年 GPU 暴力破解成本偏低。
     * 提升至 12，并支持通过配置 hbsz.password.bcrypt-cost 覆盖。
     */
    @Value("${hbsz.password.bcrypt-cost:12}")
    private int bcryptCost;

    /**
     * 对明文密码进行 BCrypt 加密。
     * <p>
     * 每次调用生成随机盐，因此相同明文得到的密文不同，属正常现象。
     *
     * @param rawPassword 明文密码
     * @return 加密后的密文（含盐）
     */
    public String encode(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(bcryptCost));
    }

    /**
     * 校验明文密码与密文是否匹配。
     *
     * @param rawPassword     待校验的明文密码
     * @param encodedPassword 数据库中存储的 BCrypt 密文
     * @return 匹配返回 true；任一参数为 null 时返回 false
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return BCrypt.checkpw(rawPassword, encodedPassword);
    }

}
