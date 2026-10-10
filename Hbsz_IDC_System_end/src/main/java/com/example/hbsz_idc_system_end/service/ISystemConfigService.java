package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.SystemConfigDTO;

/**
 * 系统配置服务接口
 * <p>
 * 采用 key-value 表设计，Service 层负责与 DTO 互转。
 * <p>
 * 缓存策略：
 * 系统配置变更频率极低（通常仅在管理员首次配置时变更），但读取频率极高
 * （前端登录页每次刷新都会调用 getConfig）。引入 Spring Cache 后，
 * 读取走 Redis 缓存，更新时自动清除缓存，避免每次请求都打 DB。
 *
 * @author hbsz
 */
public interface ISystemConfigService {

    /**
     * 获取系统配置
     * <p>
     * 加 @Cacheable 后，首次访问会查 DB 并写入 Redis，后续请求直接走缓存。
     * 配合 Redis TTL（默认 30 分钟无访问自动过期）+ updateConfig 时的 @CacheEvict，
     * 可保证数据最终一致性的同时大幅降低 DB 压力。
     */
    SystemConfigDTO getConfig();

    /**
     * 更新系统配置
     * <p>
     * 加 @CacheEvict 后，更新成功后自动清除缓存，下次 getConfig 会重新从 DB 加载。
     * beforeInvocation=false 保证 DB 更新成功才清缓存，避免更新失败导致缓存与 DB 不一致。
     */
    SystemConfigDTO updateConfig(SystemConfigDTO dto);

}
