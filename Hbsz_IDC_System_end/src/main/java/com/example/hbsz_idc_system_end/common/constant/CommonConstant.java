package com.example.hbsz_idc_system_end.common.constant;

/**
 * 系统通用常量
 * <p>
 * 集中定义全局共享的常量，包括通用字符串、HTTP 请求头、系统标识、分页默认值、
 * 数据/是否状态、Redis 缓存键前缀、登录安全、JWT 黑名单、权限类型与角色标识等，
 * 避免魔法值散落各处、便于统一维护。该类为纯常量类，禁止实例化。
 *
 * @author hbsz
 */
public class CommonConstant {

    /**
     * 私有构造器，禁止实例化工具/常量类
     */
    private CommonConstant() {
    }

    /* ====== 字符串常量 ====== */
    public static final String UTF_8 = "UTF-8";
    public static final String COMMA = ",";
    public static final String COLON = ":";
    public static final String SLASH = "/";
    public static final String EMPTY = "";
    public static final String SPACE = " ";

    /* ====== HTTP 请求头 ====== */
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";

    /* ====== 系统标识 ====== */
    public static final String SYSTEM_NAME = "数据中心综合布线项目管理系统";
    public static final String SYSTEM_VERSION = "V1.0.0";

    /* ====== 分页默认值 ====== */
    public static final Integer DEFAULT_PAGE_NUM = 1;
    public static final Integer DEFAULT_PAGE_SIZE = 20;
    public static final Integer MAX_PAGE_SIZE = 100;

    /* ====== 数据状态 ====== */
    public static final Integer STATUS_DISABLED = 0;
    public static final Integer STATUS_ENABLED = 1;

    /* ====== 是否标识 ====== */
    public static final Integer NO = 0;
    public static final Integer YES = 1;

    /* ====== Redis 缓存键 ====== */
    /** 用户权限缓存键前缀，完整格式：hbsz:user:perms:{userId} */
    public static final String REDIS_USER_PERMS_PREFIX = "hbsz:user:perms:";
    /** 用户权限缓存默认过期时间（秒），2小时 */
    public static final long REDIS_USER_PERMS_EXPIRE = 7200L;
    /** 用户角色缓存键前缀，完整格式：hbsz:user:roles:{userId} */
    public static final String REDIS_USER_ROLES_PREFIX = "hbsz:user:roles:";

    /* ====== Redis 缓存空间名称 ====== */
    /**
     * 系统配置缓存空间。
     * <p>读取频率高、变更频率极低，使用默认 TTL 30 分钟。
     */
    public static final String CACHE_SYSTEM_CONFIG = "systemConfig";
    /** 工作台概览缓存空间 */
    public static final String CACHE_DASHBOARD_SUMMARY = "dashboardSummary";
    /** 字典数据缓存空间 */
    public static final String CACHE_DICT_DATA = "dictData";
    /** 权限树缓存空间 */
    public static final String CACHE_PERMISSION_TREE = "permissionTree";
    /** 用户信息缓存空间 */
    public static final String CACHE_USER_PROFILE = "userProfile";

    /*
     * 缓存雪崩防护说明：
     * RedisCacheManager 默认 TTL 为 30 分钟（见 RedisConfig#cacheManager）。
     * 为防止大量缓存同批集中过期引发雪崩，建议各缓存空间 TTL 分层配置
     * （已在 RedisConfig 中按 dictData 1h / permissionTree 10min / userProfile 15min /
     * dashboardSummary 60s 分层），天然形成过期时间错峰；
     * 后续如需更精细的随机偏移，可通过自定义 RedisCacheWriter 为单条 entry 叠加随机 TTL 实现。
     */

    /* ====== Redis 缓存键前缀（统一 hbsz: 命名空间） ====== */
    /** 验证码缓存键前缀，完整格式：hbsz:captcha:{captchaId} */
    public static final String REDIS_CAPTCHA_PREFIX = "hbsz:captcha:";
    /** 限流计数缓存键前缀，完整格式：hbsz:rate_limit:{key} */
    public static final String REDIS_RATE_LIMIT_PREFIX = "hbsz:rate_limit:";
    /** 幂等令牌缓存键前缀，完整格式：hbsz:idempotent:{key} */
    public static final String REDIS_IDEMPOTENT_PREFIX = "hbsz:idempotent:";

    /* ====== 登录安全 ====== */
    /** 登录失败次数缓存键前缀，完整格式：hbsz:login:fail:{username} */
    public static final String REDIS_LOGIN_FAIL_PREFIX = "hbsz:login:fail:";
    /** 账号锁定缓存键前缀，完整格式：hbsz:login:lock:{username} */
    public static final String REDIS_LOGIN_LOCK_PREFIX = "hbsz:login:lock:";
    /** 最大登录失败次数，超过则锁定账号 */
    public static final int LOGIN_MAX_FAIL_COUNT = 5;
    /** 账号锁定时长（秒），30分钟 */
    public static final long LOGIN_LOCK_EXPIRE = 1800L;
    /** 登录失败计数过期时间（秒），1小时 */
    public static final long LOGIN_FAIL_EXPIRE = 3600L;

    /* ====== JWT 黑名单 ====== */
    /** JWT 黑名单缓存键前缀，完整格式：hbsz:jwt:blacklist:{token} */
    public static final String REDIS_JWT_BLACKLIST_PREFIX = "hbsz:jwt:blacklist:";

    /* ====== Refresh Token 一次性使用标记 ====== */
    /** refreshToken 已使用标记缓存键前缀，完整格式：hbsz:jwt:refresh_used:{token} */
    public static final String REDIS_REFRESH_TOKEN_USED_PREFIX = "hbsz:jwt:refresh_used:";

    /* ====== 用户登出时间戳 ====== */
    /**
     * 用户最近一次登出时间戳缓存键前缀，完整格式：hbsz:user:logout:{userId}。
     * 值为登出时刻的毫秒时间戳，TTL 为 refreshToken 最大有效期。
     * 用于登出后拒绝该时间点之前签发的 refreshToken 换取新 accessToken。
     */
    public static final String REDIS_USER_LOGOUT_PREFIX = "hbsz:user:logout:";

    /* ====== 权限类型 ====== */
    public static final Integer PERMISSION_TYPE_MENU = 1;
    public static final Integer PERMISSION_TYPE_BUTTON = 2;
    public static final Integer PERMISSION_TYPE_API = 3;

    /* ====== 超级管理员角色编码 ====== */
    public static final String ROLE_CODE_ADMIN = "admin";
    /** 项目经理角色编码 */
    public static final String ROLE_CODE_PROJECT_MANAGER = "project_manager";
    /** 工程师角色编码 */
    public static final String ROLE_CODE_ENGINEER = "engineer";
    /** 全部权限标识 */
    public static final String ALL_PERMISSION = "*:*:*";

}
