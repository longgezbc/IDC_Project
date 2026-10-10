package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.PositionQueryDTO;
import com.example.hbsz_idc_system_end.dto.PositionSaveDTO;
import com.example.hbsz_idc_system_end.dto.PositionVO;
import com.example.hbsz_idc_system_end.entity.SysPosition;

import java.util.List;

/**
 * 岗位管理服务接口
 * <p>
 * 提供岗位的增删改查及用户岗位关联统计能力，是岗位职级体系的核心服务。
 * 岗位与用户为一对多关系（一个岗位可被多个用户关联），删除前需校验是否有关联用户。
 *
 * @author hbsz
 */
public interface IPositionService {

    /**
     * 分页查询岗位列表
     *
     * @param query 查询条件（编码/名称/状态 + 分页参数）
     * @return 岗位分页结果（含关联用户数）
     */
    PageResult<PositionVO> queryPage(PositionQueryDTO query);

    /**
     * 查询所有启用岗位（下拉选择用）
     *
     * @return 启用状态的岗位列表
     */
    List<PositionVO> listAll();

    /**
     * 查询岗位详情
     *
     * @param id 岗位ID
     * @return 岗位详情（含关联用户数）
     */
    PositionVO getDetail(Long id);

    /**
     * 新增岗位
     *
     * @param dto 岗位保存参数
     * @return 新增后的岗位详情
     */
    PositionVO create(PositionSaveDTO dto);

    /**
     * 修改岗位
     *
     * @param id  岗位ID
     * @param dto 岗位保存参数
     * @return 修改后的岗位详情
     */
    PositionVO update(Long id, PositionSaveDTO dto);

    /**
     * 删除岗位
     * <p>
     * 删除前校验该岗位下是否有关联用户，有则拒绝删除。
     *
     * @param id 岗位ID
     */
    void delete(Long id);

    /**
     * 根据岗位编码查询岗位（内部使用，给 PermissionAspect 切面调用）
     * <p>
     * 用于权限切面根据用户的 position_id 查询其岗位编码，以进行岗位职级校验。
     *
     * @param code 岗位编码
     * @return 岗位实体，未命中时返回 {@code null}
     */
    SysPosition getPositionByCode(String code);

    /**
     * 根据岗位ID查询岗位实体（内部使用，给 PermissionAspect 切面调用）
     * <p>
     * 用于权限切面根据用户的 position_id 查询其岗位编码，以进行岗位职级校验。
     *
     * @param positionId 岗位ID
     * @return 岗位实体，未命中时返回 {@code null}
     */
    SysPosition getPositionById(Long positionId);

}
