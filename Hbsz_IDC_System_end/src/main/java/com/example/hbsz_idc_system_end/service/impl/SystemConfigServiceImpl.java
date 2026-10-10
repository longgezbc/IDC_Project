package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hbsz_idc_system_end.dto.SystemConfigDTO;
import com.example.hbsz_idc_system_end.entity.SysSystemConfig;
import com.example.hbsz_idc_system_end.mapper.SysSystemConfigMapper;
import com.example.hbsz_idc_system_end.service.ISystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置服务实现
 * 采用 key-value 表设计，Service 层负责与 DTO 互转
 * <p>
 * 缓存策略：
 * 系统配置变更频率极低（通常仅在管理员首次配置时变更），但读取频率极高
 * （前端登录页每次刷新都会调用 getConfig）。引入 Spring Cache 后，
 * 读取走 Redis 缓存，更新时自动清除缓存，避免每次请求都打 DB。
 *
 * @author hbsz
 */
@Slf4j
@Service
public class SystemConfigServiceImpl implements ISystemConfigService {

    /** 系统配置缓存名，updateConfig 时清除 */
    public static final String CACHE_NAME = "system_config";

    private final SysSystemConfigMapper systemConfigMapper;

    /** 配置项默认值 */
    private static final Map<String, String> DEFAULTS = new HashMap<>();
    /** 配置项中文名称 */
    private static final Map<String, String> NAMES = new HashMap<>();
    /** 配置项值类型 */
    private static final Map<String, String> TYPES = new HashMap<>();

    static {
        DEFAULTS.put("system.name", "IDC数据中心项目管理系统");
        DEFAULTS.put("system.version", "V1.0.0");
        DEFAULTS.put("system.themeColor", "#1890ff");
        DEFAULTS.put("system.defaultView", "table");
        DEFAULTS.put("system.pageSize", "20");
        DEFAULTS.put("system.autoSave", "true");

        NAMES.put("system.name", "系统名称");
        NAMES.put("system.version", "系统版本");
        NAMES.put("system.themeColor", "主题色");
        NAMES.put("system.defaultView", "默认项目视图");
        NAMES.put("system.pageSize", "每页显示条数");
        NAMES.put("system.autoSave", "表单自动保存");

        TYPES.put("system.name", "string");
        TYPES.put("system.version", "string");
        TYPES.put("system.themeColor", "string");
        TYPES.put("system.defaultView", "string");
        TYPES.put("system.pageSize", "number");
        TYPES.put("system.autoSave", "boolean");
    }

    public SystemConfigServiceImpl(SysSystemConfigMapper systemConfigMapper) {
        this.systemConfigMapper = systemConfigMapper;
    }

    /**
     * 获取系统配置
     * <p>
     * 优化说明：
     * 加 @Cacheable 后，首次访问会查 DB 并写入 Redis，后续请求直接走缓存。
     * 配合 Redis TTL（默认 30 分钟无访问自动过期）+ updateConfig 时的 @CacheEvict，
     * 可保证数据最终一致性的同时大幅降低 DB 压力。
     */
    @Override
    @Cacheable(value = CACHE_NAME, key = "'config'")
    public SystemConfigDTO getConfig() {
        log.debug("[系统配置] 缓存未命中，从 DB 加载");
        Map<String, String> kv = loadAll();
        SystemConfigDTO dto = new SystemConfigDTO();
        dto.setName(kv.getOrDefault("system.name", DEFAULTS.get("system.name")));
        dto.setVersion(kv.getOrDefault("system.version", DEFAULTS.get("system.version")));
        dto.setThemeColor(kv.getOrDefault("system.themeColor", DEFAULTS.get("system.themeColor")));
        dto.setDefaultView(kv.getOrDefault("system.defaultView", DEFAULTS.get("system.defaultView")));
        dto.setPageSize(parseInt(kv.getOrDefault("system.pageSize", DEFAULTS.get("system.pageSize")), 20));
        dto.setAutoSave(parseBool(kv.getOrDefault("system.autoSave", DEFAULTS.get("system.autoSave")), true));
        return dto;
    }

    /**
     * 更新系统配置
     * <p>
     * 加 @CacheEvict 后，更新成功后自动清除缓存，下次 getConfig 会重新从 DB 加载。
     * beforeInvocation=false 保证 DB 更新成功才清缓存，避免更新失败导致缓存与 DB 不一致。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CACHE_NAME, key = "'config'")
    public SystemConfigDTO updateConfig(SystemConfigDTO dto) {
        upsert("system.name", dto.getName(), "string");
        upsert("system.themeColor", dto.getThemeColor(), "string");
        upsert("system.defaultView", dto.getDefaultView(), "string");
        upsert("system.pageSize", dto.getPageSize() == null ? null : String.valueOf(dto.getPageSize()), "number");
        upsert("system.autoSave", dto.getAutoSave() == null ? null : String.valueOf(dto.getAutoSave()), "boolean");
        // version 为只读字段，不更新
        log.info("更新系统配置成功");
        return getConfig();
    }

    /**
     * 加载全部配置项为 key-value Map
     */
    private Map<String, String> loadAll() {
        List<SysSystemConfig> list = systemConfigMapper.selectList(null);
        Map<String, String> kv = new HashMap<>();
        for (SysSystemConfig config : list) {
            kv.put(config.getConfigKey(), config.getConfigValue());
        }
        return kv;
    }

    /**
     * 插入或更新单个配置项
     * <p>
     * 采用 upsert 语义：值为 null 则跳过（不覆盖已有配置）；按 configKey 查存在则更新其值，
     * 不存在则插入并补齐中文名与类型（取自预置的 NAMES/TYPES 元数据）。
     */
    private void upsert(String key, String value, String type) {
        if (value == null) {
            return;
        }
        LambdaQueryWrapper<SysSystemConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysSystemConfig::getConfigKey, key);
        SysSystemConfig existing = systemConfigMapper.selectOne(wrapper);
        if (existing == null) {
            SysSystemConfig config = new SysSystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setConfigName(NAMES.getOrDefault(key, key));
            config.setConfigType(TYPES.getOrDefault(key, type));
            // 【修复L5】select-then-insert 存在并发竞态，高并发下可能重复插入。
            // 捕获 DuplicateKeyException（唯一键冲突）后改为 update 操作，保证最终一致。
            try {
                systemConfigMapper.insert(config);
            } catch (DuplicateKeyException e) {
                SysSystemConfig exist = systemConfigMapper.selectOne(wrapper);
                if (exist != null) {
                    exist.setConfigValue(value);
                    systemConfigMapper.updateById(exist);
                }
            }
        } else {
            existing.setConfigValue(value);
            systemConfigMapper.updateById(existing);
        }
    }

    private Integer parseInt(String value, int defaultValue) {
        try {
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Boolean parseBool(String value, boolean defaultValue) {
        if (value == null) return defaultValue;
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

}
