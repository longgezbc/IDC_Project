package com.example.hbsz_idc_system_end.service;

import com.example.hbsz_idc_system_end.dto.DocumentQueryDTO;
import com.example.hbsz_idc_system_end.dto.DocumentReviewDTO;
import com.example.hbsz_idc_system_end.dto.DocumentSaveDTO;
import com.example.hbsz_idc_system_end.dto.DocumentVersionDTO;
import com.example.hbsz_idc_system_end.dto.DocumentVO;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.entity.BizDocument;

import java.util.List;
import java.util.Map;

/**
 * 文档服务接口
 * <p>
 * 负责项目文档的全生命周期管理：分页/详情查询、按项目归集、按分类统计，
 * 以及文档的新增、更新、删除、版本迭代（addVersion），并提供下载/预览所需的原始实体获取。
 *
 * @author hbsz
 */
public interface IDocumentService {

    /**
     * 分页查询文档列表
     *
     * @param query 查询条件（含分页参数、关键字、项目/分类等过滤项）
     * @return 文档分页结果
     */
    PageResult<DocumentVO> queryPage(DocumentQueryDTO query);

    /**
     * 查询文档详情
     *
     * @param id 文档ID
     * @return 文档详情 VO
     */
    DocumentVO getDetail(Long id);

    /**
     * 根据项目ID查询文档列表
     *
     * @param projectId 项目ID
     * @return 该项目下的文档列表
     */
    List<DocumentVO> listByProject(Long projectId);

    /**
     * 按分类统计文档数量
     *
     * @return 每项包含 category 和 count 的统计列表
     */
    List<Map<String, Object>> countByCategory();

    /**
     * 新建文档
     *
     * @param dto 文档保存参数（含文件、分类、所属项目等）
     * @return 新建后的文档 VO
     */
    DocumentVO create(DocumentSaveDTO dto);

    /**
     * 更新文档
     *
     * @param id  文档ID
     * @param dto 文档保存参数
     * @return 更新后的文档 VO
     */
    DocumentVO update(Long id, DocumentSaveDTO dto);

    /**
     * 删除文档
     *
     * @param id 文档ID
     */
    void delete(Long id);

    /**
     * 新增文档版本
     *
     * @param id  文档ID
     * @param dto 版本信息（含新版本文件、版本号、变更说明等）
     * @return 追加版本后的文档 VO
     */
    DocumentVO addVersion(Long id, DocumentVersionDTO dto);

    /**
     * 审核文档（项目经理/管理员审核上传的文档）
     *
     * @param id  文档ID
     * @param dto 审核参数（是否通过、审核意见）
     * @return 审核后的文档 VO
     */
    DocumentVO review(Long id, DocumentReviewDTO dto);

    /**
     * 根据ID获取文档实体（用于下载/预览）
     *
     * @param id 文档ID
     * @return 文档实体对象
     */
    BizDocument getEntityById(Long id);

}
