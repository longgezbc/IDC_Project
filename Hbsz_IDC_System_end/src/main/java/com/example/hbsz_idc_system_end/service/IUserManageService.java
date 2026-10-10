package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.ImportResultVO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.ResetPasswordDTO;
import com.example.hbsz_idc_system_end.dto.UserManageVO;
import com.example.hbsz_idc_system_end.dto.UserQueryDTO;
import com.example.hbsz_idc_system_end.dto.UserSaveDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 用户管理服务接口（管理员视角）
 * 提供用户的创建、查询、更新、删除、密码重置、状态管理、角色分配等功能
 *
 * @author hbsz
 */
public interface IUserManageService {

    /**
     * 分页查询用户列表
     *
     * @param query 用户查询条件（含分页参数、关键字、状态等）
     * @return 用户分页结果
     */
    PageResult<UserManageVO> queryPage(UserQueryDTO query);

    /**
     * 查询所有启用用户（下拉选择用）
     *
     * @return 启用状态的用户列表
     */
    List<UserManageVO> listAll();

    /**
     * 根据ID查询用户详情
     *
     * @param id 用户ID
     * @return 用户详情 VO（含角色信息）
     */
    UserManageVO getById(Long id);

    /**
     * 新增用户
     *
     * @param dto 用户保存参数
     * @return 新增后的用户 VO
     */
    UserManageVO create(UserSaveDTO dto);

    /**
     * 修改用户
     *
     * @param id  用户ID
     * @param dto 用户保存参数
     * @return 修改后的用户 VO
     */
    UserManageVO update(Long id, UserSaveDTO dto);

    /**
     * 删除用户
     *
     * @param id 用户ID
     */
    void delete(Long id);

    /**
     * 重置密码
     *
     * @param id  用户ID
     * @param dto 重置密码参数（新密码）
     */
    void resetPassword(Long id, ResetPasswordDTO dto);

    /**
     * 修改用户状态（启用/禁用）
     *
     * @param id     用户ID
     * @param status 目标状态（如 1=启用，0=禁用）
     */
    void updateStatus(Long id, Integer status);

    /**
     * 分配角色
     * <p>
     * 全量覆盖用户的角色绑定，变更后需清除该用户的权限/角色缓存以立即生效。
     *
     * @param id      用户ID
     * @param roleIds 角色ID列表
     */
    void assignRoles(Long id, List<Long> roleIds);

    /**
     * 批量导入用户（Excel）。
     * <p>
     * 逐行解析 Excel 并调用 {@link #create(UserSaveDTO)} 创建用户。
     * 密码经 BCrypt 加密；positionCode/roleCode 转换为 positionId/roleId。
     * 单行失败不影响其他行，最终返回汇总结果。
     *
     * @param file Excel 文件
     * @return 导入结果汇总
     * @throws IOException 读取文件失败时抛出
     */
    ImportResultVO importUsers(MultipartFile file) throws IOException;

    /**
     * 生成用户导入模板（空 Excel，仅含表头）的字节内容。
     *
     * @return Excel 文件字节
     * @throws IOException 生成文件失败时抛出
     */
    byte[] downloadImportTemplate() throws IOException;

}
