package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.dto.*;
import com.example.hbsz_idc_system_end.entity.*;
import com.example.hbsz_idc_system_end.mapper.*;
import com.example.hbsz_idc_system_end.service.IDocumentService;
import com.example.hbsz_idc_system_end.service.IFileStorageService;
import com.example.hbsz_idc_system_end.service.INotificationService;
import com.example.hbsz_idc_system_end.service.IProjectAuthorizationService;
import com.example.hbsz_idc_system_end.utils.SqlUtils;
import com.example.hbsz_idc_system_end.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档服务实现
 * <p>
 * 负责文档主数据、文档版本与标签关系的协同管理。核心关注点：
 * <ul>
 *   <li>事务：create/update/delete/addVersion 均标注 {@code @Transactional(rollbackFor = Exception.class)}，
 *       保证文档主表、版本表、标签关联表多表写入的原子性；</li>
 *   <li>性能：列表查询通过 {@link #fillExtraInfoBatch} 一次性批量加载版本与标签，规避 N+1 查询；</li>
 *   <li>文件：物理文件的存取与删除委托 {@link IFileStorageService}，删除文档时同步清理磁盘文件。</li>
 * </ul>
 *
 * @author hbsz
 */
@Slf4j
@Service
public class DocumentServiceImpl implements IDocumentService {

    private final BizDocumentMapper documentMapper;
    private final BizDocumentVersionMapper versionMapper;
    private final BizDocumentTagMapper tagMapper;
    private final BizDocumentTagRelationMapper tagRelationMapper;
    private final BizProjectMapper projectMapper;
    private final IFileStorageService fileStorageService;
    private final IProjectAuthorizationService projectAuthorizationService;
    private final INotificationService notificationService;

    public DocumentServiceImpl(BizDocumentMapper documentMapper, BizDocumentVersionMapper versionMapper,
                               BizDocumentTagMapper tagMapper, BizDocumentTagRelationMapper tagRelationMapper,
                               BizProjectMapper projectMapper, IFileStorageService fileStorageService,
                               IProjectAuthorizationService projectAuthorizationService,
                               INotificationService notificationService) {
        this.documentMapper = documentMapper;
        this.versionMapper = versionMapper;
        this.tagMapper = tagMapper;
        this.tagRelationMapper = tagRelationMapper;
        this.projectMapper = projectMapper;
        this.fileStorageService = fileStorageService;
        this.projectAuthorizationService = projectAuthorizationService;
        this.notificationService = notificationService;
    }

    /**
     * 分页查询文档列表
     * <p>
     * 先对关键字、上传人做 LIKE 通配符转义防止 SQL 注入与查询语义污染，
     * 分页查询后统一调用 {@link #fillExtraInfoBatch} 批量补全版本与标签，避免逐条查询。
     * <p>
     * 数据隔离修复：原实现未按项目可见性过滤，工程师/普通用户可通过文档列表
     * 看到未授权项目的文档。现注入 {@code visibleProjectIds}（与项目列表一致的数据隔离规则：
     * admin/PM 返回 null 不过滤；工程师/普通用户仅可见被显式授权的项目文档）。
     */
    @Override
    public PageResult<DocumentVO> queryPage(DocumentQueryDTO query) {
        // 转义 LIKE 查询通配符
        if (query.getKeyword() != null) {
            query.setKeyword(SqlUtils.escapeLike(query.getKeyword()));
        }
        if (query.getUploader() != null) {
            query.setUploader(SqlUtils.escapeLike(query.getUploader()));
        }
        // 数据隔离：注入当前用户可见的项目ID列表（null 表示不过滤）
        query.setVisibleProjectIds(projectAuthorizationService.getCurrentUserVisibleProjectIds());
        Page<DocumentVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<DocumentVO> result = documentMapper.selectDocumentPage(page, query);
        // 批量填充版本和标签信息（解决 N+1 查询问题）
        fillExtraInfoBatch(result.getRecords());
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getSize(), result.getCurrent());
    }

    /**
     * 查询文档详情
     */
    @Override
    public DocumentVO getDetail(Long id) {
        DocumentVO vo = documentMapper.selectDocumentDetailById(id);
        if (vo == null) {
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
        // IDOR 防护：校验当前用户对该文档所属项目的访问权限
        Long projectId = vo.getProjectId() != null ? Long.parseLong(vo.getProjectId()) : null;
        if (projectId == null || !projectAuthorizationService.canAccessProject(projectId)) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权访问该文档所属项目");
        }
        fillExtraInfo(vo);
        return vo;
    }

    /**
     * 根据项目ID查询文档列表
     * <p>
     * 优化说明：原实现硬编码 pageSize=1000，超过 1000 时静默截断，
     * 调用方无法感知数据被截断，可能产生业务错误判断。
     * 改为查询后检测 total，若超过上限打印 WARN 日志便于排查。
     */
    @Override
    public List<DocumentVO> listByProject(Long projectId) {
        // IDOR 防护：校验当前用户对该项目的访问权限
        if (!projectAuthorizationService.canAccessProject(projectId)) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权访问该项目");
        }
        DocumentQueryDTO query = new DocumentQueryDTO();
        query.setProjectId(projectId);
        query.setPageNum(1);
        // 使用合理上限替代 Integer.MAX_VALUE，避免大结果集导致内存溢出
        query.setPageSize(1000);
        Page<DocumentVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<DocumentVO> result = documentMapper.selectDocumentPage(page, query);
        // 检测数据是否被截断，便于调用方排查
        if (result.getTotal() > result.getSize()) {
            log.warn("[文档服务] 项目文档数超过单次查询上限，已截断: projectId={}, total={}, returned={}",
                    projectId, result.getTotal(), result.getRecords().size());
        }
        // 批量填充版本和标签信息（解决 N+1 查询问题）
        fillExtraInfoBatch(result.getRecords());
        return result.getRecords();
    }

    /**
     * 按分类统计文档数量
     *
     * @return 每项包含 category 和 count
     */
    @Override
    public List<java.util.Map<String, Object>> countByCategory() {
        return documentMapper.countByCategory();
    }

    /**
     * 新建文档
     * <p>
     * 事务内串联三步写入：插入文档主记录（含自动生成的文档编号与默认版本/状态）、
     * 写入初始版本、保存标签关联；任一步失败整体回滚，避免产生孤立数据。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVO create(DocumentSaveDTO dto) {
        BizProject project = projectMapper.selectById(dto.getProjectId());
        if (project == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "项目不存在");
        }

        // IDOR 防护：仅 admin 或项目相关用户可为项目创建文档
        if (!projectAuthorizationService.canAccessProject(dto.getProjectId())) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权为该项目创建文档");
        }

        BizDocument document = new BizDocument();
        document.setDocumentCode("DOC" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        document.setProjectId(dto.getProjectId());
        document.setName(dto.getName());
        document.setCategory(dto.getCategory());
        document.setCurrentVersion(StringUtils.hasText(dto.getVersion()) ? dto.getVersion() : "V1.0");
        document.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "待审核");
        document.setFileFormat(dto.getFileType());
        document.setFileSize(dto.getSize());
        document.setFileUrl(dto.getFileUrl());
        document.setFileSizeBytes(dto.getFileSizeBytes());
        document.setDescription(dto.getDescription());
        document.setUploadDate(LocalDate.now());
        document.setUploaderId(UserContext.getUserId());

        documentMapper.insert(document);

        // 保存初始版本
        BizDocumentVersion version = new BizDocumentVersion();
        version.setDocumentId(document.getId());
        version.setVersion(document.getCurrentVersion());
        version.setUploadDate(LocalDate.now());
        versionMapper.insert(version);

        // 保存标签
        saveTags(document.getId(), dto.getTags());

        log.info("新建文档成功: {}", document.getName());
        return getDetail(document.getId());
    }

    /**
     * 更新文档
     * <p>
     * 采用非空字段选择性更新（仅覆盖传入的字段）；标签为全量覆盖策略：
     * 先删除原有标签关联再重新保存，整个过程处于同一事务内。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVO update(Long id, DocumentSaveDTO dto) {
        BizDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
        // IDOR 防护：校验当前用户对该文档所属项目的访问权限
        if (!projectAuthorizationService.canAccessProject(document.getProjectId())) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权修改该文档");
        }
        // IDOR 防护：若迁移项目，校验当前用户对新项目的访问权限
        if (dto.getProjectId() != null && !dto.getProjectId().equals(document.getProjectId())
                && !projectAuthorizationService.canAccessProject(dto.getProjectId())) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权迁移文档到该项目");
        }

        if (StringUtils.hasText(dto.getName())) document.setName(dto.getName());
        if (StringUtils.hasText(dto.getCategory())) document.setCategory(dto.getCategory());
        if (StringUtils.hasText(dto.getStatus())) document.setStatus(dto.getStatus());
        if (dto.getFileType() != null) document.setFileFormat(dto.getFileType());
        if (dto.getSize() != null) document.setFileSize(dto.getSize());
        if (dto.getDescription() != null) document.setDescription(dto.getDescription());

        documentMapper.updateById(document);

        // 更新标签
        if (dto.getTags() != null) {
            LambdaQueryWrapper<BizDocumentTagRelation> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BizDocumentTagRelation::getDocumentId, id);
            tagRelationMapper.delete(wrapper);
            saveTags(id, dto.getTags());
        }

        log.info("更新文档成功: {}", id);
        return getDetail(id);
    }

    /**
     * 删除文档
     * <p>
     * 安全修复 #27：调整删除顺序为「先删 DB 记录、事务提交后再删物理文件」。
     * 原实现先删物理文件再删 DB 记录，若 DB 删除失败则物理文件已丢失无法恢复。
     * 修复策略：事务内先 deleteById 删除 DB 记录，再通过
     * {@link TransactionSynchronizationManager#registerSynchronization} 注册 afterCommit 钩子，
     * 在事务提交成功后才删除物理文件；若文件删除失败仅记录日志，不抛异常（DB 已成功删除）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
        // IDOR 防护：校验当前用户对该文档所属项目的访问权限
        if (!projectAuthorizationService.canAccessProject(document.getProjectId())) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权删除该文档");
        }
        String fileUrl = document.getFileUrl();
        // 先删除 DB 记录（事务内）
        documentMapper.deleteById(id);
        // 注册 afterCommit 钩子：事务提交成功后才删除物理文件，避免 DB 回滚后文件丢失
        if (StringUtils.hasText(fileUrl)) {
            final String finalFileUrl = fileUrl;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        fileStorageService.deleteFile(finalFileUrl);
                    } catch (Exception e) {
                        // 文件删除失败仅记录日志，不抛异常（DB 记录已成功删除）
                        log.warn("[文档服务] 物理文件删除失败（DB记录已删除）: fileUrl={}, error={}",
                                finalFileUrl, e.getMessage());
                    }
                }
            });
        }
        log.info("删除文档成功: {}", id);
    }

    /**
     * 新增文档版本
     * <p>
     * 事务内插入一条新版本记录，并同步更新文档主表的当前版本号与上传日期，
     * 使文档详情始终指向最新版本。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVO addVersion(Long id, DocumentVersionDTO dto) {
        BizDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
        // IDOR 防护：校验当前用户对该文档所属项目的访问权限
        if (!projectAuthorizationService.canAccessProject(document.getProjectId())) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权为该文档添加版本");
        }

        // 校验同一文档下版本号唯一性
        LambdaQueryWrapper<BizDocumentVersion> versionCheck = new LambdaQueryWrapper<>();
        versionCheck.eq(BizDocumentVersion::getDocumentId, id)
                .eq(BizDocumentVersion::getVersion, dto.getVersion());
        long versionExists = versionMapper.selectCount(versionCheck);
        if (versionExists > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "版本号 '" + dto.getVersion() + "' 已存在");
        }

        BizDocumentVersion version = new BizDocumentVersion();
        version.setDocumentId(id);
        version.setVersion(dto.getVersion());
        version.setRemark(dto.getRemark());
        version.setFileUrl(dto.getFileUrl());
        version.setUploadDate(LocalDate.now());
        // 修复并发竞态：select-then-insert 模式在高并发下可能两个线程都通过唯一性检查，
        // 然后都执行 insert，导致重复版本号。捕获 DuplicateKeyException（唯一索引冲突）后
        // 抛出业务异常，保证最终一致。需 biz_document_version 表有 (document_id, version) 唯一索引。
        try {
            versionMapper.insert(version);
        } catch (DuplicateKeyException e) {
            log.warn("[文档服务] 版本号并发冲突: documentId={}, version={}", id, dto.getVersion());
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "版本号 '" + dto.getVersion() + "' 已存在");
        }

        // 更新文档当前版本
        document.setCurrentVersion(dto.getVersion());
        document.setUploadDate(LocalDate.now());
        documentMapper.updateById(document);

        log.info("新增文档版本成功: {} -> {}", id, dto.getVersion());
        return getDetail(id);
    }

    /**
     * 审核文档
     * <p>
     * 业务规则：
     * <ul>
     *   <li>仅 "待审核" 状态的文档可被审核，避免重复审核；</li>
     *   <li>审核通过：将状态置为 "已审核"，记录审核人与审核时间；</li>
     *   <li>审核驳回：将状态置为 "已驳回"，记录审核意见供上传人查看。</li>
     * </ul>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVO review(Long id, DocumentReviewDTO dto) {
        BizDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
        // IDOR 防护：审核是敏感操作，校验当前用户对该文档所属项目的访问权限
        if (!projectAuthorizationService.canAccessProject(document.getProjectId())) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权审核该文档");
        }

        String newStatus = Boolean.TRUE.equals(dto.getApprove()) ? "已审核" : "已驳回";
        Long reviewerId = UserContext.getUserId();
        LocalDateTime reviewTime = LocalDateTime.now();

        // 使用 CAS 原子更新，防止并发重复审核：
        // 仅当 status='待审核' 时才执行更新，受影响行数为 0 表示已被其他线程审核
        int affected = documentMapper.updateReviewStatusCAS(
                id, newStatus, reviewerId, reviewTime, dto.getRemark());
        if (affected == 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "文档已被审核，请刷新后重试");
        }

        log.info("[文档审核] 文档ID={}, 审核结果={}, 审核人={}",
                id, newStatus, UserContext.getUsername());

        // 通知上传人审核结果（补全信息流断点，上传人无需轮询刷新状态）
        notifyDocumentReviewed(document, Boolean.TRUE.equals(dto.getApprove()),
                UserContext.getUsername(), dto.getRemark());

        return getDetail(id);
    }

    /**
     * 通知文档上传人审核结果
     * <p>
     * 审核人自己上传的文档不重复通知；通知失败仅记录 WARN，不影响审核主流程。
     */
    private void notifyDocumentReviewed(BizDocument document, boolean approved,
                                        String reviewerName, String remark) {
        try {
            if (document.getUploaderId() == null
                    || document.getUploaderId().equals(UserContext.getUserId())) {
                return;
            }
            NotificationSendDTO notif = new NotificationSendDTO();
            notif.setUserId(document.getUploaderId());
            notif.setTitle(approved ? "文档审核通过" : "文档审核驳回");
            notif.setContent("文档「" + document.getName() + "」已" + (approved ? "通过审核" : "被驳回")
                    + "，审核人：" + reviewerName
                    + (StringUtils.hasText(remark) ? "，意见：" + remark : ""));
            notif.setNotifyType("document");
            notif.setBizType("project");
            notif.setBizId(document.getProjectId());
            notif.setPriority(approved ? 1 : 2);
            notificationService.sendNotification(notif);
        } catch (Exception e) {
            log.warn("[文档审核] 通知上传人审核结果失败: documentId={}, error={}",
                    document.getId(), e.getMessage());
        }
    }

    /**
     * 根据ID获取文档实体（用于下载/预览）
     */
    @Override
    public BizDocument getEntityById(Long id) {
        BizDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
        // IDOR 防护：下载是高危操作，校验当前用户对该文档所属项目的访问权限
        if (!projectAuthorizationService.canAccessProject(document.getProjectId())) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权下载该文档");
        }
        return document;
    }

    /**
     * 批量填充版本和标签信息（解决 N+1 查询问题）
     * 一次查询所有文档的版本和标签，在内存中按 documentId 分组
     */
    private void fillExtraInfoBatch(List<DocumentVO> voList) {
        if (CollectionUtils.isEmpty(voList)) {
            return;
        }
        // 收集所有 documentId
        List<Long> docIds = voList.stream()
                .map(vo -> {
                    try {
                        return Long.parseLong(vo.getId());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        if (docIds.isEmpty()) {
            voList.forEach(vo -> {
                vo.setVersions(new ArrayList<>());
                vo.setTags(new ArrayList<>());
            });
            return;
        }

        // 一次查询所有版本
        List<BizDocumentVersion> allVersions = versionMapper.selectByDocumentIds(docIds);
        java.util.Map<Long, List<BizDocumentVersion>> versionMap = allVersions.stream()
                .collect(Collectors.groupingBy(BizDocumentVersion::getDocumentId));

        // 一次查询所有标签
        List<java.util.Map<String, Object>> allTagRows = tagMapper.selectTagNamesByDocumentIds(docIds);
        java.util.Map<Long, List<String>> tagMap = new java.util.HashMap<>();
        for (java.util.Map<String, Object> row : allTagRows) {
            Long docId = ((Number) row.get("document_id")).longValue();
            String tagName = (String) row.get("tag_name");
            tagMap.computeIfAbsent(docId, k -> new ArrayList<>()).add(tagName);
        }

        // 填充到每个 VO
        for (DocumentVO vo : voList) {
            try {
                Long docId = Long.parseLong(vo.getId());
                // 版本列表
                List<BizDocumentVersion> versions = versionMap.getOrDefault(docId, java.util.Collections.emptyList());
                List<DocumentVO.VersionVO> versionVOs = versions.stream().map(v -> {
                    DocumentVO.VersionVO vvo = new DocumentVO.VersionVO();
                    vvo.setVersion(v.getVersion());
                    vvo.setDate(v.getUploadDate() != null ? v.getUploadDate().toString() : null);
                    vvo.setUploader(v.getUploaderName());
                    vvo.setRemark(v.getRemark());
                    return vvo;
                }).collect(Collectors.toList());
                vo.setVersions(versionVOs);

                // 标签列表
                vo.setTags(tagMap.getOrDefault(docId, new ArrayList<>()));
            } catch (NumberFormatException e) {
                vo.setVersions(new ArrayList<>());
                vo.setTags(new ArrayList<>());
            }
        }
    }

    /**
     * 填充版本和标签信息（单条，用于详情查询）
     */
    private void fillExtraInfo(DocumentVO vo) {
        try {
            Long docId = Long.parseLong(vo.getId());
            // 版本列表
            LambdaQueryWrapper<BizDocumentVersion> versionWrapper = new LambdaQueryWrapper<>();
            versionWrapper.eq(BizDocumentVersion::getDocumentId, docId)
                    .orderByDesc(BizDocumentVersion::getUploadDate);
            List<BizDocumentVersion> versions = versionMapper.selectList(versionWrapper);
            List<DocumentVO.VersionVO> versionVOs = versions.stream().map(v -> {
                DocumentVO.VersionVO vvo = new DocumentVO.VersionVO();
                vvo.setVersion(v.getVersion());
                vvo.setDate(v.getUploadDate() != null ? v.getUploadDate().toString() : null);
                vvo.setUploader(v.getUploaderName());
                vvo.setRemark(v.getRemark());
                return vvo;
            }).collect(Collectors.toList());
            vo.setVersions(versionVOs);

            // 标签列表
            List<String> tagNames = tagMapper.selectTagNamesByDocumentId(docId);
            vo.setTags(tagNames);
        } catch (NumberFormatException e) {
            vo.setVersions(new ArrayList<>());
            vo.setTags(new ArrayList<>());
        }
    }

    /**
     * 保存标签
     * 优化：一次查询所有已存在的标签，批量插入关联关系
     */
    private void saveTags(Long documentId, List<String> tags) {
        if (CollectionUtils.isEmpty(tags)) {
            return;
        }
        // 过滤空标签
        List<String> validTags = tags.stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        if (validTags.isEmpty()) {
            return;
        }

        // 一次查询所有已存在的标签（替代循环内逐个查询）
        LambdaQueryWrapper<BizDocumentTag> tagWrapper = new LambdaQueryWrapper<>();
        tagWrapper.in(BizDocumentTag::getTagName, validTags);
        List<BizDocumentTag> existingTags = tagMapper.selectList(tagWrapper);

        // 已存在标签的 name -> id 映射
        java.util.Map<String, Long> tagNameToId = new java.util.HashMap<>();
        for (BizDocumentTag tag : existingTags) {
            tagNameToId.put(tag.getTagName(), tag.getId());
        }

        // 找出需要新建的标签
        List<BizDocumentTag> newTags = new ArrayList<>();
        for (String tagName : validTags) {
            if (!tagNameToId.containsKey(tagName)) {
                BizDocumentTag newTag = new BizDocumentTag();
                newTag.setTagName(tagName);
                newTags.add(newTag);
            }
        }

        // 修复 #5：批量插入新标签（替代循环逐条 insert）
        // useGeneratedKeys=true 会将自增主键回填到 newTags 各元素的 id 字段
        if (!newTags.isEmpty()) {
            tagMapper.batchInsert(newTags);
            for (BizDocumentTag newTag : newTags) {
                tagNameToId.put(newTag.getTagName(), newTag.getId());
            }
        }

        // 批量插入关联关系（替代循环内逐个插入）
        List<BizDocumentTagRelation> relations = new ArrayList<>();
        for (String tagName : validTags) {
            Long tagId = tagNameToId.get(tagName);
            if (tagId != null) {
                BizDocumentTagRelation relation = new BizDocumentTagRelation();
                relation.setDocumentId(documentId);
                relation.setTagId(tagId);
                relations.add(relation);
            }
        }
        if (!relations.isEmpty()) {
            tagRelationMapper.batchInsert(relations);
        }
    }

}
