package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.ImportResultVO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.ProjectQueryDTO;
import com.example.hbsz_idc_system_end.dto.ProjectSaveDTO;
import com.example.hbsz_idc_system_end.dto.ProjectStatisticsVO;
import com.example.hbsz_idc_system_end.dto.ProjectVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 项目服务接口
 * <p>
 * 负责工程项目主数据的管理：分页查询（含数据隔离）、详情、增删改，
 * 以及项目归档/取消归档的状态流转与统计概览。
 *
 * @author hbsz
 */
public interface IProjectService {

    /**
     * 分页查询项目列表
     * <p>
     * 数据隔离：普通用户只能查看自己负责的项目，管理员可查看全部。
     * 统计页面（拥有 statistics:query 权限）跳过数据隔离以获取全局统计数据。
     *
     * @param query 查询条件（含分页参数、状态、关键字等）
     * @return 项目分页结果
     */
    PageResult<ProjectVO> queryPage(ProjectQueryDTO query);

    /**
     * 查询项目详情
     *
     * @param id 项目ID
     * @return 项目详情 VO
     */
    ProjectVO getDetail(Long id);

    /**
     * 新建项目
     *
     * @param dto 项目保存参数
     * @return 新建后的项目 VO
     */
    ProjectVO create(ProjectSaveDTO dto);

    /**
     * 更新项目
     *
     * @param id  项目ID
     * @param dto 项目保存参数
     * @return 更新后的项目 VO
     */
    ProjectVO update(Long id, ProjectSaveDTO dto);

    /**
     * 删除项目
     *
     * @param id 项目ID
     */
    void delete(Long id);

    /**
     * 归档项目
     *
     * @param id 项目ID
     * @return 归档后的项目 VO
     */
    ProjectVO archive(Long id);

    /**
     * 取消归档
     *
     * @param id 项目ID
     * @return 取消归档后的项目 VO
     */
    ProjectVO unarchive(Long id);

    /**
     * 项目统计概览
     *
     * @return 项目统计 VO
     */
    ProjectStatisticsVO getStatistics();

    /**
     * 批量导入项目（Excel）。
     * <p>
     * 逐行解析 Excel 并调用 {@link #create(ProjectSaveDTO)} 创建项目，
     * 单行失败不影响其他行，最终返回汇总结果。
     *
     * @param file Excel 文件
     * @return 导入结果汇总
     * @throws IOException 读取文件失败时抛出
     */
    ImportResultVO importProjects(MultipartFile file) throws IOException;

    /**
     * 生成项目导入模板（空 Excel，仅含表头）的字节内容。
     *
     * @return Excel 文件字节
     * @throws IOException 生成文件失败时抛出
     */
    byte[] downloadImportTemplate() throws IOException;

}