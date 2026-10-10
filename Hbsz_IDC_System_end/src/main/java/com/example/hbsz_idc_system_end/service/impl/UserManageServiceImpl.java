package com.example.hbsz_idc_system_end.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hbsz_idc_system_end.common.constant.CommonConstant;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.ImportResultVO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.ResetPasswordDTO;
import com.example.hbsz_idc_system_end.dto.UserImportDTO;
import com.example.hbsz_idc_system_end.dto.UserManageVO;
import com.example.hbsz_idc_system_end.dto.UserQueryDTO;
import com.example.hbsz_idc_system_end.dto.UserSaveDTO;
import com.example.hbsz_idc_system_end.entity.SysPosition;
import com.example.hbsz_idc_system_end.entity.SysRole;
import com.example.hbsz_idc_system_end.entity.SysUser;
import com.example.hbsz_idc_system_end.entity.SysUserRole;
import com.example.hbsz_idc_system_end.mapper.SysPositionMapper;
import com.example.hbsz_idc_system_end.mapper.SysRoleMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserRoleMapper;
import com.example.hbsz_idc_system_end.service.IPermissionService;
import com.example.hbsz_idc_system_end.service.IUserManageService;
import com.example.hbsz_idc_system_end.utils.DesensitizeUtils;
import com.example.hbsz_idc_system_end.utils.PasswordUtils;
import com.example.hbsz_idc_system_end.utils.SqlUtils;
import com.example.hbsz_idc_system_end.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户管理服务实现（管理员视角）
 * <p>
 * 提供用户的创建、查询、更新、删除、密码重置、状态管理、角色分配等能力。
 * <p>
 * 关键设计要点：
 * <ul>
 *   <li>写操作（创建/更新/删除/重置密码/状态变更/分配角色）均标注 {@code @Transactional}，
 *       保证用户与角色关联的写入在同一事务内完成；</li>
 *   <li>安全防护：禁止删除超级管理员账号（id=1）及当前登录用户本身；</li>
 *   <li>缓存一致性：用户信息、状态或角色变更后调用
 *       {@link IPermissionService#clearUserCache(Long)} 清除权限缓存；</li>
 *   <li>角色分配采用"全量覆盖"（先删后插），并用 batchInsert 批量写入降低 DB 往返；</li>
 *   <li>敏感信息（邮箱/手机号）在转换 VO 时通过 {@link DesensitizeUtils} 脱敏。</li>
 * </ul>
 *
 * @author hbsz
 */
@Slf4j
@Service
public class UserManageServiceImpl implements IUserManageService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysPositionMapper sysPositionMapper;
    private final PasswordUtils passwordUtils;
    private final IPermissionService permissionService;

    public UserManageServiceImpl(SysUserMapper sysUserMapper, SysRoleMapper sysRoleMapper,
                                 SysUserRoleMapper sysUserRoleMapper, SysPositionMapper sysPositionMapper,
                                 PasswordUtils passwordUtils, IPermissionService permissionService) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysPositionMapper = sysPositionMapper;
        this.passwordUtils = passwordUtils;
        this.permissionService = permissionService;
    }

    /**
     * 自注入代理，用于 importUsers 中通过代理调用 @Transactional 的 create() 方法，
     * 避免 this.create() 的 self-invocation 导致事务失效。
     */
    @Lazy
    @Autowired
    private IUserManageService self;

    /**
     * 分页查询用户列表
     */
    @Override
    public PageResult<UserManageVO> queryPage(UserQueryDTO query) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getUsername())) {
            wrapper.like(SysUser::getUsername, SqlUtils.escapeLike(query.getUsername()));
        }
        if (StringUtils.hasText(query.getName())) {
            wrapper.like(SysUser::getName, SqlUtils.escapeLike(query.getName()));
        }
        if (StringUtils.hasText(query.getPhone())) {
            wrapper.like(SysUser::getPhone, SqlUtils.escapeLike(query.getPhone()));
        }
        if (query.getStatus() != null) {
            wrapper.eq(SysUser::getStatus, query.getStatus());
        }
        // 重构：departmentId 已废弃，统一使用 deptId 进行部门筛选
        if (query.getDeptId() != null) {
            wrapper.eq(SysUser::getDeptId, query.getDeptId());
        }
        wrapper.orderByDesc(SysUser::getCreatedAt);

        Page<SysUser> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<SysUser> result = sysUserMapper.selectPage(page, wrapper);

        List<UserManageVO> voList = convertToVOList(result.getRecords());
        return new PageResult<>(voList, result.getTotal(), result.getSize(), result.getCurrent());
    }

    /**
     * 查询所有启用用户（下拉选择用）
     */
    @Override
    public List<UserManageVO> listAll() {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getStatus, CommonConstant.STATUS_ENABLED)
               .orderByAsc(SysUser::getName);
        List<SysUser> users = sysUserMapper.selectList(wrapper);
        return convertToVOList(users);
    }

    /**
     * 根据ID查询用户详情
     */
    @Override
    public UserManageVO getById(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "用户不存在");
        }
        List<UserManageVO> list = convertToVOList(Collections.singletonList(user));
        return list.get(0);
    }

    /**
     * 新增用户
     * <p>
     * 业务规则：校验用户名/密码/姓名非空并保证用户名唯一；密码经
     * {@link PasswordUtils#encode(String)} 加密后落库；如指定角色则一并分配。
     * 全流程在事务内执行。
     *
     * @param dto 用户保存参数（账号、密码、基本信息、角色ID列表等）
     * @return 新建用户的管理视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserManageVO create(UserSaveDTO dto) {
        log.info("[用户服务] 新增用户开始: username={}, name={}", dto.getUsername(), dto.getName());
        // 参数校验
        if (!StringUtils.hasText(dto.getUsername())) {
            log.warn("[用户服务] 新增用户失败: 用户名为空");
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名不能为空");
        }
        if (!StringUtils.hasText(dto.getPassword())) {
            log.warn("[用户服务] 新增用户失败: 密码为空, username={}", dto.getUsername());
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码不能为空");
        }
        if (!StringUtils.hasText(dto.getName())) {
            log.warn("[用户服务] 新增用户失败: 姓名为空, username={}", dto.getUsername());
            throw new BusinessException(ResultCode.BAD_REQUEST, "姓名不能为空");
        }
        // 用户名唯一性校验
        SysUser exists = sysUserMapper.selectByUsernameAll(dto.getUsername());
        if (exists != null) {
            log.warn("[用户服务] 新增用户失败: 用户名已存在, username={}", dto.getUsername());
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordUtils.encode(dto.getPassword()));
        user.setName(dto.getName());
        user.setNickname(dto.getNickname());
        user.setGender(dto.getGender());
        user.setAvatar(dto.getAvatar());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        // 重构：departmentId/position 已废弃，统一使用 deptId/positionId
        user.setDeptId(dto.getDeptId());
        user.setPositionId(dto.getPositionId());
        user.setStatus(dto.getStatus() != null ? dto.getStatus() : CommonConstant.STATUS_ENABLED);
        sysUserMapper.insert(user);
        log.info("[用户服务] 新增用户成功: id={}, username={}, name={}", user.getId(), user.getUsername(), user.getName());

        // 分配角色
        if (!CollectionUtils.isEmpty(dto.getRoleIds())) {
            log.info("[用户服务] 新增用户并分配角色: userId={}, roleIds={}", user.getId(), dto.getRoleIds());
            saveUserRoles(user.getId(), dto.getRoleIds());
        } else {
            log.debug("[用户服务] 新增用户未分配角色: userId={}", user.getId());
        }

        return getById(user.getId());
    }

    /**
     * 修改用户
     * <p>
     * 采用选择性更新：仅对 DTO 中非空字段进行更新；密码仅在传入新密码时才重新加密；
     * roleIds 非 null 时执行角色重新分配（全量覆盖）。更新完成后清除该用户权限缓存。
     *
     * @param id  用户ID
     * @param dto 用户保存参数（仅非空字段生效）
     * @return 更新后的用户管理视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserManageVO update(Long id, UserSaveDTO dto) {
        // 【修复M1】原日志打印整个 dto 对象，UserSaveDTO 含 password 明文字段，存在敏感信息泄露风险。
        // 改为仅记录用户 ID，避免密码泄露到日志。
        log.info("[用户服务] 修改用户开始: id={}", id);
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            log.warn("[用户服务] 修改用户失败: 用户不存在, id={}", id);
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "用户不存在");
        }

        if (StringUtils.hasText(dto.getName())) user.setName(dto.getName());
        if (dto.getNickname() != null) user.setNickname(dto.getNickname());
        if (dto.getGender() != null) user.setGender(dto.getGender());
        if (dto.getAvatar() != null) user.setAvatar(dto.getAvatar());
        if (dto.getEmail() != null) user.setEmail(dto.getEmail());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        // 重构：departmentId/position 已废弃，统一使用 deptId/positionId
        if (dto.getDeptId() != null) user.setDeptId(dto.getDeptId());
        if (dto.getPositionId() != null) user.setPositionId(dto.getPositionId());
        if (dto.getStatus() != null) user.setStatus(dto.getStatus());

        // 修改密码（仅在传入新密码时）
        if (StringUtils.hasText(dto.getPassword())) {
            user.setPassword(passwordUtils.encode(dto.getPassword()));
            log.info("[用户服务] 修改用户密码: id={}", id);
        }

        sysUserMapper.updateById(user);
        log.info("[用户服务] 修改用户成功: id={}, username={}, name={}, status={}",
                id, user.getUsername(), user.getName(), user.getStatus());

        // 重新分配角色
        if (dto.getRoleIds() != null) {
            log.info("[用户服务] 修改用户并重新分配角色: id={}, roleIds={}", id, dto.getRoleIds());
            reassignRoles(id, dto.getRoleIds());
        }

        // 清除用户权限缓存
        permissionService.clearUserCache(id);

        return getById(id);
    }

    /**
     * 删除用户
     * <p>
     * 安全防护：禁止删除超级管理员账号（id=1）及当前登录用户本身。
     * 删除时先移除用户-角色关联，再删除用户主记录，最后清除权限缓存。
     * 全流程在事务内执行。
     *
     * @param id 待删除用户ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        log.info("[用户服务] 删除用户开始: id={}", id);
        // 保护超级管理员账号（id=1）禁止删除
        if (id != null && id == 1L) {
            log.warn("[用户服务] 删除用户失败: 禁止删除超级管理员账号, id={}", id);
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "超级管理员账号不可删除");
        }
        // 禁止删除当前登录用户自己
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null && currentUserId.equals(id)) {
            log.warn("[用户服务] 删除用户失败: 禁止删除当前登录用户, currentUserId={}, targetId={}", currentUserId, id);
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "不能删除当前登录的用户");
        }
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            log.warn("[用户服务] 删除用户失败: 用户不存在, id={}", id);
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "用户不存在");
        }
        // 删除用户角色关联
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getUserId, id);
        int urDeleted = sysUserRoleMapper.delete(wrapper);
        // 删除用户
        sysUserMapper.deleteById(id);
        // 清除缓存
        permissionService.clearUserCache(id);
        log.info("[用户服务] 删除用户成功: id={}, username={}, 关联角色数={}", id, user.getUsername(), urDeleted);
    }

    /**
     * 重置密码
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, ResetPasswordDTO dto) {
        log.info("[用户服务] 重置密码开始: id={}", id);
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            log.warn("[用户服务] 重置密码失败: 用户不存在, id={}", id);
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "用户不存在");
        }
        if (!StringUtils.hasText(dto.getNewPassword())) {
            log.warn("[用户服务] 重置密码失败: 新密码为空, id={}", id);
            throw new BusinessException(ResultCode.BAD_REQUEST, "新密码不能为空");
        }
        user.setPassword(passwordUtils.encode(dto.getNewPassword()));
        sysUserMapper.updateById(user);
        log.info("[用户服务] 重置密码成功: id={}, username={}", id, user.getUsername());
    }

    /**
     * 修改用户状态（启用/禁用）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        log.info("[用户服务] 修改用户状态开始: id={}, targetStatus={}", id, status);
        // 校验状态值合法性（仅允许 0=禁用, 1=启用）
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "状态值无效，仅允许 0（禁用）或 1（启用）");
        }
        // 禁止对超级管理员（id=1）进行状态变更
        if (id == null || id == 1L) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能修改超级管理员状态");
        }
        // 禁止禁用当前登录用户自身
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null && currentUserId.equals(id)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能修改自己的状态");
        }
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            log.warn("[用户服务] 修改状态失败: 用户不存在, id={}", id);
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "用户不存在");
        }
        Integer oldStatus = user.getStatus();
        user.setStatus(status);
        sysUserMapper.updateById(user);
        // 状态变更时清除缓存
        permissionService.clearUserCache(id);
        log.info("[用户服务] 修改用户状态成功: id={}, username={}, {} -> {}",
                id, user.getUsername(), oldStatus, status);
    }

    /**
     * 分配角色
     * <p>
     * 校验用户存在后，以全量覆盖方式重新分配角色（先删后插），
     * 并清除该用户权限缓存以保证后续鉴权即时生效。事务内执行。
     *
     * @param id      用户ID
     * @param roleIds 目标角色ID列表（为空表示清空该用户全部角色）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long id, List<Long> roleIds) {
        log.info("[用户服务] 分配用户角色开始: id={}, roleIds={}", id, roleIds);
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            log.warn("[用户服务] 分配角色失败: 用户不存在, id={}", id);
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "用户不存在");
        }
        reassignRoles(id, roleIds);
        permissionService.clearUserCache(id);
        log.info("[用户服务] 分配用户角色成功: id={}, username={}, roleIds={}",
                id, user.getUsername(), roleIds);
    }

    /**
     * 重新分配角色（全量覆盖）
     */
    private void reassignRoles(Long userId, List<Long> roleIds) {
        // 先删除原有关联
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getUserId, userId);
        int deleted = sysUserRoleMapper.delete(wrapper);
        log.debug("[用户服务] 清除用户原有角色关联: userId={}, 删除记录数={}", userId, deleted);
        // 再插入新的关联
        if (!CollectionUtils.isEmpty(roleIds)) {
            saveUserRoles(userId, roleIds);
        }
    }

    /**
     * 批量保存用户角色关联
     * <p>
     * 优化说明：原实现循环单条 insert，N 个角色产生 N 次 DB 网络往返。
     * 改为构造 List 后调用 batchInsert 单条 SQL 完成插入，
     * N 个角色只需 1 次 DB 往返，显著降低延迟。
     */
    private void saveUserRoles(Long userId, List<Long> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return;
        }
        List<SysUserRole> list = new ArrayList<>(roleIds.size());
        for (Long roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            list.add(ur);
        }
        sysUserRoleMapper.batchInsert(list);
    }

    /**
     * 将实体列表转换为VO列表（批量查询角色，避免N+1）
     * <p>
     * 性能优化：对整批用户仅执行两次查询——一次批量查询用户-角色关联，
     * 一次批量查询相关角色，随后在内存中按用户ID分组组装，规避逐用户查询的 N+1 问题。
     * 同时对邮箱、手机号等敏感字段调用 {@link DesensitizeUtils} 脱敏后再返回。
     *
     * @param users 用户实体列表
     * @return 脱敏并附带角色信息的用户管理视图对象列表
     */
    private List<UserManageVO> convertToVOList(List<SysUser> users) {
        if (CollectionUtils.isEmpty(users)) {
            return Collections.emptyList();
        }
        List<Long> userIds = users.stream().map(SysUser::getId).collect(Collectors.toList());

        // 批量查询所有用户的角色关联（一次查询）
        LambdaQueryWrapper<SysUserRole> urWrapper = new LambdaQueryWrapper<>();
        urWrapper.in(SysUserRole::getUserId, userIds);
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(urWrapper);

        // 查询所有相关角色（一次查询）
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).distinct().collect(Collectors.toList());
        Map<Long, SysRole> roleMap;
        if (roleIds.isEmpty()) {
            roleMap = Collections.emptyMap();
        } else {
            List<SysRole> roles = sysRoleMapper.selectBatchIds(roleIds);
            roleMap = roles.stream().collect(Collectors.toMap(SysRole::getId, r -> r));
        }

        // 按用户ID分组角色
        Map<Long, List<SysUserRole>> rolesByUser = userRoles.stream()
                .collect(Collectors.groupingBy(SysUserRole::getUserId));

        // 批量查询所有用户的岗位信息（一次查询，避免 N+1）
        List<Long> positionIds = users.stream()
                .map(SysUser::getPositionId)
                .filter(pid -> pid != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SysPosition> positionMap;
        if (positionIds.isEmpty()) {
            positionMap = Collections.emptyMap();
        } else {
            List<SysPosition> positions = sysPositionMapper.selectBatchIds(positionIds);
            positionMap = positions.stream().collect(Collectors.toMap(SysPosition::getId, p -> p));
        }

        List<UserManageVO> result = new ArrayList<>(users.size());
        for (SysUser user : users) {
            UserManageVO vo = new UserManageVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setName(user.getName());
            vo.setNickname(user.getNickname());
            vo.setGender(user.getGender());
            vo.setAvatar(user.getAvatar());
            // 敏感信息脱敏
            vo.setEmail(DesensitizeUtils.maskEmail(user.getEmail()));
            vo.setPhone(DesensitizeUtils.maskPhone(user.getPhone()));
            // 重构：departmentId/position 已废弃，统一使用 deptId/positionId
            // （原 setDepartmentId 与 setDeptId 合并，移除冗余的 setDeptId 重复赋值）
            vo.setDeptId(user.getDeptId());
            vo.setPositionId(user.getPositionId());
            // 填充岗位编码与名称（便于前端展示）
            if (user.getPositionId() != null) {
                SysPosition position = positionMap.get(user.getPositionId());
                if (position != null) {
                    vo.setPositionCode(position.getCode());
                    vo.setPositionName(position.getName());
                }
            }
            vo.setStatus(user.getStatus());
            vo.setLastLoginTime(user.getLastLoginTime());
            vo.setLastLoginIp(user.getLastLoginIp());
            vo.setCreatedAt(user.getCreatedAt());

            List<SysUserRole> urs = rolesByUser.get(user.getId());
            if (urs != null && !urs.isEmpty()) {
                List<SysRole> userRoleList = urs.stream()
                        .map(ur -> roleMap.get(ur.getRoleId()))
                        .filter(r -> r != null)
                        .collect(Collectors.toList());
                vo.setRoleIds(userRoleList.stream().map(SysRole::getId).collect(Collectors.toList()));
                vo.setRoleCodes(userRoleList.stream().map(SysRole::getRoleCode).collect(Collectors.toList()));
                vo.setRoleNames(userRoleList.stream().map(SysRole::getRoleName).collect(Collectors.toList()));
            } else {
                vo.setRoleIds(Collections.emptyList());
                vo.setRoleCodes(Collections.emptyList());
                vo.setRoleNames(Collections.emptyList());
            }
            result.add(vo);
        }
        return result;
    }

    /**
     * 批量导入用户（Excel）。
     * <p>
     * 使用 EasyExcel 同步读取整张 Sheet，逐行转换为 {@link UserSaveDTO} 并调用
     * {@link #create(UserSaveDTO)} 落库（内部已完成 BCrypt 加密与角色分配）。
     * positionCode/roleCode 通过查询数据库转换为 positionId/roleId；
     * 单行异常被捕获并记录到 errors 列表，不影响其他行的导入。
     *
     * @param file Excel 文件
     * @return 导入结果汇总
     * @throws IOException 读取文件失败时抛出
     */
    @Override
    public ImportResultVO importUsers(MultipartFile file) throws IOException {
        List<UserImportDTO> dataList = new ArrayList<>();
        try (InputStream in = file.getInputStream()) {
            EasyExcel.read(in, UserImportDTO.class, new ReadListener<UserImportDTO>() {
                @Override
                public void invoke(UserImportDTO data, AnalysisContext context) {
                    dataList.add(data);
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // no-op
                }
            }).sheet().doRead();
        }

        List<String> errors = new ArrayList<>();
        int success = 0;
        int rowIndex = 1; // Excel 行号从 1 开始（不含表头）
        for (UserImportDTO data : dataList) {
            rowIndex++;
            try {
                UserSaveDTO dto = new UserSaveDTO();
                dto.setUsername(data.getUsername());
                dto.setName(data.getName());
                dto.setPassword(data.getPassword());
                dto.setPhone(data.getPhone());
                dto.setEmail(data.getEmail());
                // 岗位编码转换为岗位ID
                if (StringUtils.hasText(data.getPositionCode())) {
                    SysPosition position = sysPositionMapper.selectOne(new LambdaQueryWrapper<SysPosition>()
                            .eq(SysPosition::getCode, data.getPositionCode().trim()));
                    if (position == null) {
                        throw new BusinessException(ResultCode.DATA_NOT_FOUND,
                                "岗位编码不存在: " + data.getPositionCode());
                    }
                    dto.setPositionId(position.getId());
                }
                // 角色编码转换为角色ID
                if (StringUtils.hasText(data.getRoleCode())) {
                    SysRole role = sysRoleMapper.selectByCode(data.getRoleCode().trim());
                    if (role == null) {
                        throw new BusinessException(ResultCode.DATA_NOT_FOUND,
                                "角色编码不存在: " + data.getRoleCode());
                    }
                    dto.setRoleIds(Collections.singletonList(role.getId()));
                }
                self.create(dto);
                success++;
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                errors.add("第" + rowIndex + "行: " + reason);
                log.warn("[用户导入] 第{}行导入失败: {}", rowIndex, reason);
            }
        }
        log.info("[用户导入] 完成: 总数={}, 成功={}, 失败={}", dataList.size(), success, errors.size());
        return ImportResultVO.of(dataList.size(), success, errors);
    }

    /**
     * 生成用户导入模板的字节内容。
     * <p>
     * 模板结构（避免用户盲猜字段规则导致导入失败）：
     * <ul>
     *   <li>Sheet1「用户导入模板」：表头 + 2 行真实合法示例数据（示例可直接改为
     *       自己的数据；说明页首条提示删除/修改示例行）；</li>
     *   <li>Sheet2「填写说明」：每个字段的必填性、格式要求、可选值清单，
     *       与 {@link UserSaveDTO} 的校验注解和 importUsers 的编码校验一一对应。</li>
     * </ul>
     *
     * @return Excel 文件字节
     * @throws IOException 生成文件失败时抛出
     */
    @Override
    public byte[] downloadImportTemplate() throws IOException {
        List<UserImportDTO> samples = new ArrayList<>();
        // 示例行使用真实合法数据（用户名/密码均通过后端校验规则），用户可参照修改
        UserImportDTO sample1 = new UserImportDTO();
        sample1.setUsername("zhangsan");
        sample1.setName("张三");
        sample1.setPassword("Abc@2026");
        sample1.setPhone("13812345678");
        sample1.setEmail("zhangsan@example.com");
        sample1.setPositionCode("engineer");
        sample1.setRoleCode("engineer");
        samples.add(sample1);

        UserImportDTO sample2 = new UserImportDTO();
        sample2.setUsername("lisi");
        sample2.setName("李四");
        sample2.setPassword("Xy#5678abcd");
        sample2.setPhone("13987654321");
        sample2.setPositionCode("pm");
        sample2.setRoleCode("project_manager");
        samples.add(sample2);

        // 填写说明：三列结构（列名 / 是否必填 / 填写说明）
        List<List<String>> guide = new ArrayList<>();
        guide.add(java.util.Arrays.asList(
                "【使用须知】① 请先删除或修改第2-3行示例数据，再填写真实数据；"
                        + "② 每行一个用户，勿修改表头行；③ 岗位/角色编码以系统「系统管理-岗位管理/角色管理」页为准；"
                        + "④ 导入结果按行提示错误原因（如：第4行: 密码长度8-32位，需包含大小写字母、数字和特殊字符）。"));
        guide.add(java.util.Arrays.asList("列名", "是否必填", "填写说明"));
        guide.add(java.util.Arrays.asList("用户名", "必填",
                "登录账号，3-20位字母、数字或下划线，系统内唯一（如：zhangsan）"));
        guide.add(java.util.Arrays.asList("姓名", "必填",
                "真实姓名，不超过50个字符"));
        guide.add(java.util.Arrays.asList("密码", "必填",
                "8-32位，必须同时包含大写字母、小写字母、数字和特殊字符（如：Abc@2026）"));
        guide.add(java.util.Arrays.asList("手机号", "选填",
                "11位手机号，1开头（第2位为3-9），如：13812345678"));
        guide.add(java.util.Arrays.asList("邮箱", "选填",
                "标准邮箱格式，如：zhangsan@example.com"));
        guide.add(java.util.Arrays.asList("岗位编码", "选填",
                "可选值：gm(总经理) / pm(项目经理) / senior_engineer(高级工程师) / engineer(工程师) / "
                        + "junior_engineer(初级工程师) / outsourcing(外包人员) / viewer(只读用户)"));
        guide.add(java.util.Arrays.asList("角色编码", "选填",
                "可选值：admin(系统管理员) / project_manager(项目经理) / engineer(工程师) / user(普通用户)"));

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (com.alibaba.excel.ExcelWriter writer = EasyExcel.write(out).build()) {
            com.alibaba.excel.write.metadata.WriteSheet mainSheet =
                    EasyExcel.writerSheet(0, "用户导入模板").head(UserImportDTO.class).build();
            writer.write(samples, mainSheet);
            com.alibaba.excel.write.metadata.WriteSheet guideSheet =
                    EasyExcel.writerSheet(1, "填写说明").build();
            writer.write(guide, guideSheet);
        }
        return out.toByteArray();
    }

}
