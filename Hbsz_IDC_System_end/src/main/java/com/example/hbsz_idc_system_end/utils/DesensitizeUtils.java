package com.example.hbsz_idc_system_end.utils;

/**
 * 数据脱敏工具类
 * <p>
 * 对手机号、邮箱、身份证、姓名、银行卡等敏感信息进行掩码处理，
 * 常用于日志打印、列表展示等场景，避免敏感数据明文泄露。
 * 所有方法均对 null 或长度不足的输入做空安全处理，原样返回不抛异常。
 * 该类为纯静态工具类，禁止实例化。
 *
 * @author hbsz
 */
public final class DesensitizeUtils {

    /**
     * 私有构造器，禁止实例化
     */
    private DesensitizeUtils() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 手机号脱敏：保留前 3 位和后 4 位，中间用 **** 替换。
     * 例：13812345678 -&gt; 138****5678
     *
     * @param phone 手机号
     * @return 脱敏后的手机号；为 null 或长度小于 7 时原样返回
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 邮箱脱敏：保留首字符和 @ 后域名，中间用 *** 替换。
     * 例：zhangming@ggkj.com -&gt; z***@ggkj.com
     *
     * @param email 邮箱地址
     * @return 脱敏后的邮箱；为 null、不含 @ 或 @ 前长度不足时原样返回
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int atIdx = email.indexOf('@');
        if (atIdx <= 1) {
            return email;
        }
        return email.charAt(0) + "***" + email.substring(atIdx);
    }

    /**
     * 身份证脱敏：保留前 6 位和后 4 位，中间用 ******** 替换。
     * 例：420101199001011234 -&gt; 420101********1234
     *
     * @param idCard 身份证号
     * @return 脱敏后的身份证号；为 null 或长度小于 10 时原样返回
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 10) {
            return idCard;
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }

    /**
     * 真实姓名脱敏：保留姓氏，其余字符用 * 替换。
     * 例：李建国 -&gt; 李**
     *
     * @param name 姓名
     * @return 脱敏后的姓名；为 null 或长度不超过 1 时原样返回
     */
    public static String maskName(String name) {
        if (name == null || name.length() <= 1) {
            return name;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(name.charAt(0));
        for (int i = 1; i < name.length(); i++) {
            sb.append('*');
        }
        return sb.toString();
    }

    /**
     * 银行卡脱敏：保留前 4 位和后 4 位，中间用 **** 替换。
     * 例：6222021234567890 -&gt; 6222****7890
     *
     * @param bankCard 银行卡号
     * @return 脱敏后的银行卡号；为 null 或长度小于 8 时原样返回
     */
    public static String maskBankCard(String bankCard) {
        if (bankCard == null || bankCard.length() < 8) {
            return bankCard;
        }
        return bankCard.substring(0, 4) + "****" + bankCard.substring(bankCard.length() - 4);
    }
}
