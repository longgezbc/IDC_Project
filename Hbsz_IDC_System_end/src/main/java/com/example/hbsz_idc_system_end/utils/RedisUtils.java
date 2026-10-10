package com.example.hbsz_idc_system_end.utils;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 操作工具类
 * <p>
 * 对 {@link RedisTemplate} 进行二次封装，屏蔽底层 opsForXxx 细节并统一 null 处理，
 * 提供 Key、String、Hash、List、Set 等常用数据结构操作，以及分布式锁占位、
 * 计数、模式匹配等能力，简化业务层缓存代码。
 *
 * @author hbsz
 */
@Component
public class RedisUtils {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Lua 脚本：原子执行 INCR + 首次 EXPIRE。
     * <p>
     * 修复并发问题：原实现为 increment + expire 两步操作，进程在两步之间崩溃
     * 会导致 key 永不过期（限流永久生效、登录失败计数永久累加）。
     * 改为 Lua 脚本后，Redis 单线程保证脚本内多条命令原子执行。
     * <p>
     * 语义：INCR key；若返回值为 1（首次访问），则 EXPIRE key seconds。
     * 返回值：自增后的计数值。
     */
    private static final DefaultRedisScript<Long> INCR_AND_EXPIRE_SCRIPT;
    static {
        INCR_AND_EXPIRE_SCRIPT = new DefaultRedisScript<>();
        INCR_AND_EXPIRE_SCRIPT.setScriptText(
                "local count = redis.call('INCR', KEYS[1]) " +
                "if count == 1 then " +
                "  redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
                "end " +
                "return count"
        );
        INCR_AND_EXPIRE_SCRIPT.setResultType(Long.class);
    }

    public RedisUtils(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /* ======================== Key 操作 ======================== */

    /**
     * 设置 key 的过期时间
     *
     * @param key     缓存键
     * @param timeout 过期时长
     * @param unit    时间单位
     * @return 设置成功返回 true
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        Boolean result = redisTemplate.expire(key, timeout, unit);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 设置 key 的过期时间（秒）
     *
     * @param key     缓存键
     * @param seconds 过期秒数
     * @return 设置成功返回 true
     */
    public boolean expire(String key, long seconds) {
        return expire(key, seconds, TimeUnit.SECONDS);
    }

    /**
     * 获取 key 的剩余过期时间（秒）
     *
     * @param key 缓存键
     * @return 剩余秒数；key 不存在或无过期时间时返回 -1
     */
    public long getExpire(String key) {
        Long expire = redisTemplate.getExpire(key);
        return expire == null ? -1 : expire;
    }

    /**
     * 判断 key 是否存在
     *
     * @param key 缓存键
     * @return 存在返回 true
     */
    public boolean hasKey(String key) {
        Boolean result = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 删除单个 key
     *
     * @param key 缓存键
     * @return 删除成功返回 true
     */
    public boolean delete(String key) {
        Boolean result = redisTemplate.delete(key);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 批量删除 key
     *
     * @param keys 缓存键集合
     * @return 实际删除的数量
     */
    public long delete(Collection<String> keys) {
        Long count = redisTemplate.delete(keys);
        return count == null ? 0 : count;
    }

    /* ======================== String 操作 ======================== */

    /**
     * 存入缓存（永不过期）
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 存入缓存并设置过期时间
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 过期时长
     * @param unit    时间单位
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 存入缓存并设置过期时间（秒）
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param seconds 过期秒数
     */
    public void set(String key, Object value, long seconds) {
        set(key, value, seconds, TimeUnit.SECONDS);
    }

    /**
     * 获取缓存值
     *
     * @param key 缓存键
     * @return 缓存值；不存在时返回 null
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 获取缓存值并转换为指定类型
     *
     * @param key   缓存键
     * @param clazz 目标类型
     * @param <T>   目标类型泛型
     * @return 指定类型的缓存值；不存在时返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * 仅当 key 不存在时设置值（SET NX），常用于分布式锁/幂等占位
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 过期时长
     * @param unit    时间单位
     * @return 占位成功返回 true，key 已存在返回 false
     */
    public boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 按指定步长自增
     *
     * @param key   缓存键
     * @param delta 步长
     * @return 自增后的值
     */
    public long increment(String key, long delta) {
        Long result = redisTemplate.opsForValue().increment(key, delta);
        return result == null ? 0 : result;
    }

    /**
     * 自增 1
     *
     * @param key 缓存键
     * @return 自增后的值
     */
    public long increment(String key) {
        return increment(key, 1);
    }

    /**
     * 原子自增 1 并在首次访问时设置过期时间（修复 INCR + EXPIRE 非原子问题）。
     * <p>
     * 使用 Lua 脚本封装 INCR + EXPIRE 两步操作，Redis 单线程保证脚本内命令原子执行。
     * 解决场景：限流计数、登录失败计数等场景下，原实现若进程在 increment 与 expire 之间崩溃，
     * 会导致 key 永不过期，限流永久生效或失败计数永久累加。
     *
     * @param key     缓存键
     * @param seconds 过期秒数（仅在首次访问 INCR 返回 1 时生效）
     * @return 自增后的计数值
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public long incrementWithExpire(String key, long seconds) {
        // 修复：使用 StringRedisSerializer 序列化 args，
        // 避免 GenericJackson2JsonRedisSerializer 将字符串 "5" 序列化为带引号的 JSON "5"，
        // 导致 Lua 脚本 ARGV[1] 收到带引号的字符串，EXPIRE 命令解析失败报 "Error in execution"
        // 注：resultSerializer 使用 raw type 强转，因为 Lua 返回的整数回复由 Spring Data Redis
        // 直接转为 Long，不经过 resultSerializer，编译器只是泛型类型检查不通过
        Long result = redisTemplate.execute(
                INCR_AND_EXPIRE_SCRIPT,
                StringRedisSerializer.UTF_8,
                (RedisSerializer) StringRedisSerializer.UTF_8,
                Collections.singletonList(key),
                String.valueOf(seconds)
        );
        return result == null ? 0 : result;
    }

    /* ======================== Hash 操作 ======================== */

    /**
     * Hash 存入单个字段
     *
     * @param key     缓存键
     * @param hashKey 字段名
     * @param value   字段值
     */
    public void hSet(String key, String hashKey, Object value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    /**
     * Hash 获取单个字段
     *
     * @param key     缓存键
     * @param hashKey 字段名
     * @return 字段值；不存在时返回 null
     */
    public Object hGet(String key, String hashKey) {
        return redisTemplate.opsForHash().get(key, hashKey);
    }

    /**
     * Hash 批量存入字段
     *
     * @param key 缓存键
     * @param map 字段名到字段值的映射
     */
    public void hSetAll(String key, Map<String, Object> map) {
        redisTemplate.opsForHash().putAll(key, map);
    }

    /**
     * Hash 获取所有字段
     *
     * @param key 缓存键
     * @return 全部字段的映射
     */
    public Map<Object, Object> hGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * Hash 删除一个或多个字段
     *
     * @param key      缓存键
     * @param hashKeys 待删除的字段名
     * @return 实际删除的字段数量
     */
    public long hDelete(String key, Object... hashKeys) {
        Long result = redisTemplate.opsForHash().delete(key, hashKeys);
        return result == null ? 0 : result;
    }

    /**
     * Hash 判断字段是否存在
     *
     * @param key     缓存键
     * @param hashKey 字段名
     * @return 存在返回 true
     */
    public boolean hHasKey(String key, String hashKey) {
        Boolean result = redisTemplate.opsForHash().hasKey(key, hashKey);
        return Boolean.TRUE.equals(result);
    }

    /* ======================== List 操作 ======================== */

    /**
     * List 左侧（头部）推入元素
     *
     * @param key   缓存键
     * @param value 元素值
     * @return 操作后的列表长度
     */
    public long lPush(String key, Object value) {
        Long result = redisTemplate.opsForList().leftPush(key, value);
        return result == null ? 0 : result;
    }

    /**
     * List 右侧（尾部）推入元素
     *
     * @param key   缓存键
     * @param value 元素值
     * @return 操作后的列表长度
     */
    public long rPush(String key, Object value) {
        Long result = redisTemplate.opsForList().rightPush(key, value);
        return result == null ? 0 : result;
    }

    /**
     * List 左侧（头部）弹出元素
     *
     * @param key 缓存键
     * @return 弹出的元素；列表为空时返回 null
     */
    public Object lPop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    /**
     * List 右侧（尾部）弹出元素
     *
     * @param key 缓存键
     * @return 弹出的元素；列表为空时返回 null
     */
    public Object rPop(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }

    /**
     * List 获取指定范围内的元素
     *
     * @param key   缓存键
     * @param start 起始下标（含）
     * @param end   结束下标（含，-1 表示末尾）
     * @return 范围内的元素列表
     */
    public List<Object> lRange(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    /**
     * List 获取长度
     *
     * @param key 缓存键
     * @return 列表长度
     */
    public long lSize(String key) {
        Long size = redisTemplate.opsForList().size(key);
        return size == null ? 0 : size;
    }

    /* ======================== Set 操作 ======================== */

    /**
     * Set 添加一个或多个元素
     *
     * @param key    缓存键
     * @param values 元素值
     * @return 实际新增的元素数量
     */
    public long sAdd(String key, Object... values) {
        Long result = redisTemplate.opsForSet().add(key, values);
        return result == null ? 0 : result;
    }

    /**
     * Set 获取所有元素
     *
     * @param key 缓存键
     * @return 元素集合
     */
    public Set<Object> sMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    /**
     * Set 判断元素是否存在
     *
     * @param key   缓存键
     * @param value 元素值
     * @return 存在返回 true
     */
    public boolean sIsMember(String key, Object value) {
        Boolean result = redisTemplate.opsForSet().isMember(key, value);
        return Boolean.TRUE.equals(result);
    }

    /**
     * Set 获取元素个数
     *
     * @param key 缓存键
     * @return 元素个数
     */
    public long sSize(String key) {
        Long size = redisTemplate.opsForSet().size(key);
        return size == null ? 0 : size;
    }

    /**
     * Set 移除一个或多个元素
     *
     * @param key    缓存键
     * @param values 待移除的元素值
     * @return 实际移除的元素数量
     */
    public long sRemove(String key, Object... values) {
        Long result = redisTemplate.opsForSet().remove(key, values);
        return result == null ? 0 : result;
    }

    /* ======================== 模式匹配 ======================== */

    /**
     * 获取匹配 pattern 的所有 key。
     * <p>
     * 注意：生产环境慎用 KEYS 命令（会阻塞 Redis 主线程），大数据量下建议使用 {@link #scan(String)}。
     *
     * @param pattern key 匹配模式
     * @return 匹配的 key 集合
     */
    public Set<String> keys(String pattern) {
        return redisTemplate.keys(pattern);
    }

    /**
     * 使用 SCAN 迭代获取匹配 pattern 的所有 key
     * <p>
     * 优化说明：Redis 单线程模型下 KEYS 命令会阻塞主线程，
     * 当 key 数量较大（如数万）时会导致请求超时甚至主从切换。
     * SCAN 通过游标分批迭代，单次执行时间可控（O(N) 但分批），
     * 不会阻塞 Redis 主线程，是生产环境批量 key 操作的推荐方式。
     * <p>
     * 注意：SCAN 不保证返回结果不重复，调用方需做去重（本方法用 Set 自动去重）。
     *
     * @param pattern key 匹配模式，如 "hbsz:user:perms:*"
     * @return 匹配的 key 集合（去重后）
     */
    public Set<String> scan(String pattern) {
        Set<String> result = new java.util.HashSet<>();
        org.springframework.data.redis.core.ScanOptions options = org.springframework.data.redis.core.ScanOptions
                .scanOptions()
                .match(pattern)
                .count(100)  // 每批建议 100，平衡 RTT 次数与单次执行时间
                .build();
        try (org.springframework.data.redis.core.Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                result.add(cursor.next());
            }
        }
        return result;
    }

}
