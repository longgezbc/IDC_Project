package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hbsz_idc_system_end.common.constant.CommonConstant;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.PositionQueryDTO;
import com.example.hbsz_idc_system_end.dto.PositionSaveDTO;
import com.example.hbsz_idc_system_end.dto.PositionVO;
import com.example.hbsz_idc_system_end.entity.SysPosition;
import com.example.hbsz_idc_system_end.entity.SysUser;
import com.example.hbsz_idc_system_end.mapper.SysPositionMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import com.example.hbsz_idc_system_end.service.IPositionService;
import com.example.hbsz_idc_system_end.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 岗位管理服务实现
 * <p>
 * 提供岗位的增删改查能力，关键约束：
 * <ul>
 *   <li>事务：create/update/delete 均为 {@code @Transactional(rollbackFor = Exception.class)}；</li>
 *   <li>唯一性：岗位编码（code）全局唯一，新增/修改时需校验；</li>
 *   <li>删除保护：删除前校验该岗位下是否有关联用户，有则拒绝删除；</li>
 *   <li>性能：列表转 VO 时批量查询用户数（{@link #convertToVOList}），规避 N+1。</li>
 * </ul>
 *
 * @author hbsz
 */
@Slf4j
@Service
public class PositionServiceImpl implements IPositionService {

    private final SysPositionMapper sysPositionMapper;
    private final SysUserMapper sysUserMapper;

    public PositionServiceImpl(SysPositionMapper sysPositionMapper, SysUserMapper sysUserMapper) {
        this.sysPositionMapper = sysPositionMapper;
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 分页查询岗位列表
     */
    @Override
    public PageResult<PositionVO> queryPage(PositionQueryDTO query) {
        LambdaQueryWrapper<SysPosition> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getCode())) {
            // 【修复L4】对 LIKE 查询的通配符（%、_、\）进行转义，防止用户输入干扰 SQL 语义或导致全表扫描。
            wrapper.like(SysPosition::getCode, SqlUtils.escapeLike(query.getCode()));
        }
        if (StringUtils.hasText(query.getName())) {
            // 【修复L4】同上，转义 LIKE 通配符。
            wrapper.like(SysPosition::getName, SqlUtils.escapeLike(query.getName()));
        }
        if (query.getStatus() != null) {
            wrapper.eq(SysPosition::getStatus, query.getStatus());
        }
        // 按职级升序（职级越高越靠前），再按创建时间降序
        wrapper.orderByAsc(SysPosition::getLevel)
               .orderByDesc(SysPosition::getCreatedAt);

        Page<SysPosition> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<SysPosition> result = sysPositionMapper.selectPage(page, wrapper);
        List<PositionVO> voList = convertToVOList(result.getRecords());
        return new PageResult<>(voList, result.getTotal(), result.getSize(), result.getCurrent());
    }

    /**
     * 查询所有启用岗位（下拉选择用）
     */
    @Override
    public List<PositionVO> listAll() {
        LambdaQueryWrapper<SysPosition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPosition::getStatus, CommonConstant.STATUS_ENABLED)
               .orderByAsc(SysPosition::getLevel);
        List<SysPosition> positions = sysPositionMapper.selectList(wrapper);
        return convertToVOList(positions);
    }

    /**
     * 查询岗位详情
     */
    @Override
    public PositionVO getDetail(Long id) {
        SysPosition position = sysPositionMapper.selectById(id);
        if (position == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "岗位不存在");
        }
        List<PositionVO> list = convertToVOList(Collections.singletonList(position));
        return list.get(0);
    }

    /**
     * 新增岗位
     * <p>
     * 校验岗位编码唯一性后落库，编码重复时抛 DATA_ALREADY_EXISTS。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PositionVO create(PositionSaveDTO dto) {
        log.info("[岗位服务] 新增岗位开始: code={}, name={}, level={}", dto.getCode(), dto.getName(), dto.getLevel());
        // 岗位编码唯一性校验
        LambdaQueryWrapper<SysPosition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPosition::getCode, dto.getCode());
        Long existsCount = sysPositionMapper.selectCount(wrapper);
        if (existsCount != null && existsCount > 0) {
            log.warn("[岗位服务] 新增岗位失败: 岗位编码已存在, code={}", dto.getCode());
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "岗位编码已存在");
        }

        SysPosition position = new SysPosition();
        position.setCode(dto.getCode());
        position.setName(dto.getName());
        position.setLevel(dto.getLevel());
        position.setDescription(dto.getDescription());
        position.setStatus(dto.getStatus() != null ? dto.getStatus() : CommonConstant.STATUS_ENABLED);
        sysPositionMapper.insert(position);
        log.info("[岗位服务] 新增岗位成功: id={}, code={}, name={}", position.getId(), position.getCode(), position.getName());
        return getDetail(position.getId());
    }

    /**
     * 修改岗位
     * <p>
     * 校验岗位存在后选择性更新；编码唯一性校验排除自身 ID，避免误报。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PositionVO update(Long id, PositionSaveDTO dto) {
        log.info("[岗位服务] 修改岗位开始: id={}, dto={}", id, dto);
        SysPosition position = sysPositionMapper.selectById(id);
        if (position == null) {
            log.warn("[岗位服务] 修改岗位失败: 岗位不存在, id={}", id);
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "岗位不存在");
        }
        // 岗位编码唯一性校验（排除自身）
        if (StringUtils.hasText(dto.getCode()) && !dto.getCode().equals(position.getCode())) {
            LambdaQueryWrapper<SysPosition> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysPosition::getCode, dto.getCode())
                   .ne(SysPosition::getId, id);
            Long existsCount = sysPositionMapper.selectCount(wrapper);
            if (existsCount != null && existsCount > 0) {
                log.warn("[岗位服务] 修改岗位失败: 岗位编码已存在, code={}", dto.getCode());
                throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "岗位编码已存在");
            }
        }

        if (StringUtils.hasText(dto.getCode())) position.setCode(dto.getCode());
        if (StringUtils.hasText(dto.getName())) position.setName(dto.getName());
        if (dto.getLevel() != null) position.setLevel(dto.getLevel());
        if (dto.getDescription() != null) position.setDescription(dto.getDescription());
        if (dto.getStatus() != null) position.setStatus(dto.getStatus());
        sysPositionMapper.updateById(position);
        log.info("[岗位服务] 修改岗位成功: id={}, code={}, name={}", id, position.getCode(), position.getName());
        return getDetail(id);
    }

    /**
     * 删除岗位
     * <p>
     * 删除前校验该岗位下是否有关联用户，有则拒绝删除，避免出现孤立的用户岗位引用。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        log.info("[岗位服务] 删除岗位开始: id={}", id);
        SysPosition position = sysPositionMapper.selectById(id);
        if (position == null) {
            log.warn("[岗位服务] 删除岗位失败: 岗位不存在, id={}", id);
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "岗位不存在");
        }
        // 校验是否有用户使用该岗位
        LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(SysUser::getPositionId, id);
        Long userCount = sysUserMapper.selectCount(userWrapper);
        if (userCount != null && userCount > 0) {
            log.warn("[岗位服务] 删除岗位失败: 该岗位下还有用户, id={}, userCount={}", id, userCount);
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该岗位下还有用户，无法删除");
        }
        sysPositionMapper.deleteById(id);
        log.info("[岗位服务] 删除岗位成功: id={}, code={}, name={}", id, position.getCode(), position.getName());
    }

    /**
     * 根据岗位编码查询岗位（内部使用，给 PermissionAspect 调用）
     */
    @Override
    public SysPosition getPositionByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        LambdaQueryWrapper<SysPosition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPosition::getCode, code);
        return sysPositionMapper.selectOne(wrapper);
    }

    /**
     * 根据岗位ID查询岗位实体（内部使用，给 PermissionAspect 调用）
     */
    @Override
    public SysPosition getPositionById(Long positionId) {
        if (positionId == null) {
            return null;
        }
        return sysPositionMapper.selectById(positionId);
    }

    /**
     * 转换为 VO 列表（批量查询关联用户数，避免 N+1）
     * <p>
     * 收集所有 positionId 后一次性按 position_id 分组统计用户数，
     * 在内存中回填每个岗位的 userCount，避免逐岗位查询。
     */
    private List<PositionVO> convertToVOList(List<SysPosition> positions) {
        if (CollectionUtils.isEmpty(positions)) {
            return Collections.emptyList();
        }
        List<Long> positionIds = positions.stream().map(SysPosition::getId).collect(Collectors.toList());

        // 批量查询每个岗位下的用户数
        LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(SysUser::getPositionId, positionIds);
        List<SysUser> users = sysUserMapper.selectList(userWrapper);
        // 按 positionId 分组统计用户数
        java.util.Map<Long, Long> userCountByPosition = users.stream()
                .filter(u -> u.getPositionId() != null)
                .collect(Collectors.groupingBy(SysUser::getPositionId, Collectors.counting()));

        List<PositionVO> result = new ArrayList<>(positions.size());
        for (SysPosition position : positions) {
            PositionVO vo = new PositionVO();
            vo.setId(position.getId());
            vo.setCode(position.getCode());
            vo.setName(position.getName());
            vo.setLevel(position.getLevel());
            vo.setDescription(position.getDescription());
            vo.setStatus(position.getStatus());
            vo.setCreatedAt(position.getCreatedAt());
            vo.setUpdatedAt(position.getUpdatedAt());
            vo.setUserCount(userCountByPosition.getOrDefault(position.getId(), 0L).intValue());
            result.add(vo);
        }
        return result;
    }

}
