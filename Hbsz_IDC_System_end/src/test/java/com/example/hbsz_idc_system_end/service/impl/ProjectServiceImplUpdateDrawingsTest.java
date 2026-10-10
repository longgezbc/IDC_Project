package com.example.hbsz_idc_system_end.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.hbsz_idc_system_end.dto.ProjectSaveDTO;
import com.example.hbsz_idc_system_end.dto.ProjectVO;
import com.example.hbsz_idc_system_end.entity.BizDesignDrawing;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.mapper.BizArchiveRecordMapper;
import com.example.hbsz_idc_system_end.mapper.BizCustomerMapper;
import com.example.hbsz_idc_system_end.mapper.BizDesignDrawingMapper;
import com.example.hbsz_idc_system_end.mapper.BizDocumentMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectAssignmentMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.mapper.BizTaskMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import com.example.hbsz_idc_system_end.service.IFileStorageService;
import com.example.hbsz_idc_system_end.service.INotificationService;
import com.example.hbsz_idc_system_end.service.IPermissionService;
import com.example.hbsz_idc_system_end.service.IProjectAuthorizationService;
import com.example.hbsz_idc_system_end.service.IProjectTeamService;
import com.example.hbsz_idc_system_end.service.ProjectAccessGuard;
import com.example.hbsz_idc_system_end.utils.UserContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectServiceImpl#update(Long, ProjectSaveDTO)} 设计图纸 diff 同步语义测试。
 * <p>
 * 语义修复背景：原"先删后增"会重置全部自增 id 与 uploaded_by；现改为基于 id 的 diff 同步：
 * <ul>
 *   <li>DTO 无 id → 新增（batchInsert，uploaded_by=当前用户）；</li>
 *   <li>DTO 有 id 且属本项目 → updateById，保留原 uploaded_by（不重置上传人）；</li>
 *   <li>库中存在但未提交 → deleteBatchIds；</li>
 *   <li>DTO id 不属于本项目（IDOR）→ 降级为新增，绝不 updateById 他人记录；</li>
 *   <li>designDrawings 为 null → 不触碰图纸；为空列表 → 清空全部。</li>
 * </ul>
 * 技术说明：LambdaQueryWrapper 构建依赖 MyBatis-Plus TableInfo 缓存，
 * 纯 Mockito 环境需在 @BeforeAll 手动初始化 BizProject/BizDesignDrawing 表元数据。
 *
 * @author hbsz
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("项目更新-设计图纸 diff 同步语义测试")
class ProjectServiceImplUpdateDrawingsTest {

    private static final Long PROJECT_ID = 100L;
    private static final Long CURRENT_USER_ID = 500L;
    private static final Long ORIGINAL_UPLOADER_ID = 77L;

    @Mock
    private BizProjectMapper projectMapper;
    @Mock
    private BizCustomerMapper customerMapper;
    @Mock
    private BizDesignDrawingMapper drawingMapper;
    @Mock
    private BizArchiveRecordMapper archiveRecordMapper;
    @Mock
    private IPermissionService permissionService;
    @Mock
    private BizTaskMapper taskMapper;
    @Mock
    private BizDocumentMapper documentMapper;
    @Mock
    private INotificationService notificationService;
    @Mock
    private IProjectAuthorizationService projectAuthorizationService;
    @Mock
    private IProjectTeamService projectTeamService;
    @Mock
    private IFileStorageService fileStorageService;
    @Mock
    private BizProjectAssignmentMapper assignmentMapper;
    @Mock
    private SysUserMapper userMapper;

    private ProjectServiceImpl projectService;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizProject.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizDesignDrawing.class);
    }

    @BeforeEach
    void setUp() {
        projectService = new ProjectServiceImpl(projectMapper, customerMapper, drawingMapper,
                archiveRecordMapper, permissionService, taskMapper, documentMapper,
                notificationService, projectAuthorizationService, projectTeamService, fileStorageService,
                new ProjectAccessGuard(projectMapper, assignmentMapper, permissionService), userMapper);
        UserContext.setUserId(CURRENT_USER_ID);
        UserContext.setUsername("测试用户");

        // update() 前置路径通用 stub：项目存在 + 可访问 + 编号/名称唯一性校验通过 + 乐观锁更新成功
        BizProject project = new BizProject();
        project.setId(PROJECT_ID);
        project.setProjectCode("P-001");
        project.setName("测试项目");
        project.setManagerId(10L);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        when(projectAuthorizationService.canAccessProject(PROJECT_ID)).thenReturn(true);
        when(projectMapper.selectCount(any())).thenReturn(0L);
        when(projectMapper.updateById(any(BizProject.class))).thenReturn(1);
        // update() 末尾 getDetail(id) 的返回路径
        when(projectMapper.selectProjectDetailById(PROJECT_ID)).thenReturn(new ProjectVO());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /** 构造库中已有的图纸记录 */
    private BizDesignDrawing existingDrawing(Long id, String name) {
        BizDesignDrawing drawing = new BizDesignDrawing();
        drawing.setId(id);
        drawing.setProjectId(PROJECT_ID);
        drawing.setName(name);
        drawing.setDrawingType("施工图");
        drawing.setUploadedBy(ORIGINAL_UPLOADER_ID);
        return drawing;
    }

    /** 构造前端提交的图纸 DTO */
    private ProjectSaveDTO.DesignDrawingDTO drawingDTO(Long id, String name, String size) {
        ProjectSaveDTO.DesignDrawingDTO dto = new ProjectSaveDTO.DesignDrawingDTO();
        dto.setId(id);
        dto.setName(name);
        dto.setType("电气图");
        dto.setUploadDate("2026-08-19");
        dto.setSize(size);
        return dto;
    }

    /** 构造仅含图纸字段的最小更新 DTO（编号/名称与库中一致以跳过唯一性冲突分支） */
    private ProjectSaveDTO updateDTO(List<ProjectSaveDTO.DesignDrawingDTO> drawings) {
        ProjectSaveDTO dto = new ProjectSaveDTO();
        dto.setName("测试项目");
        dto.setCode("P-001");
        dto.setManagerId(10L);
        dto.setDesignDrawings(drawings);
        return dto;
    }

    @Test
    @DisplayName("diff 三分类：无 id 新增、有 id 更新且保留 uploaded_by、未提交的删除")
    void updateDrawings_diffSemantics() {
        when(drawingMapper.selectList(any())).thenReturn(Arrays.asList(
                existingDrawing(1L, "旧名称-1"),
                existingDrawing(2L, "即将删除-2"),
                existingDrawing(3L, "保持不变-3")));

        projectService.update(PROJECT_ID, updateDTO(Arrays.asList(
                drawingDTO(1L, "新名称-1", "3MB"),
                drawingDTO(null, "全新图纸", "1.5MB"),
                drawingDTO(3L, "保持不变-3", "2MB"))));

        // 更新：id=1 与 id=3 均走 updateById（提交了 id 即视为保留/更新，即使字段未变）
        ArgumentCaptor<BizDesignDrawing> updateCaptor = ArgumentCaptor.forClass(BizDesignDrawing.class);
        verify(drawingMapper, times(2)).updateById(updateCaptor.capture());
        List<BizDesignDrawing> updated = updateCaptor.getAllValues();
        BizDesignDrawing updated1 = updated.stream().filter(d -> d.getId().equals(1L)).findFirst().orElseThrow();
        assertEquals("新名称-1", updated1.getName());
        assertEquals("电气图", updated1.getDrawingType());
        // 关键语义：保留原记录的上传人，不重置为当前操作人
        assertEquals(ORIGINAL_UPLOADER_ID, updated1.getUploadedBy());
        // size 变化时字节字段随之重算
        assertEquals(Long.valueOf(3L * 1024 * 1024), updated1.getFileSizeBytes());

        // 新增：无 id 的记录批量插入，uploaded_by 取当前用户
        ArgumentCaptor<List<BizDesignDrawing>> insertCaptor = ArgumentCaptor.forClass(List.class);
        verify(drawingMapper, times(1)).batchInsert(insertCaptor.capture());
        assertEquals(1, insertCaptor.getValue().size());
        BizDesignDrawing inserted = insertCaptor.getValue().get(0);
        assertEquals("全新图纸", inserted.getName());
        assertEquals(PROJECT_ID, inserted.getProjectId());
        assertEquals(CURRENT_USER_ID, inserted.getUploadedBy());
        assertEquals(Long.valueOf(1536L * 1024), inserted.getFileSizeBytes());

        // 删除：库中存在但未提交的 id=2
        ArgumentCaptor<List<Long>> deleteCaptor = ArgumentCaptor.forClass(List.class);
        verify(drawingMapper, times(1)).deleteBatchIds(deleteCaptor.capture());
        assertEquals(Collections.singletonList(2L), deleteCaptor.getValue());
    }

    @Test
    @DisplayName("空列表语义：清空该项目全部图纸")
    void updateDrawings_emptyListClearsAll() {
        when(drawingMapper.selectList(any())).thenReturn(Arrays.asList(
                existingDrawing(1L, "图纸-1"),
                existingDrawing(2L, "图纸-2")));

        projectService.update(PROJECT_ID, updateDTO(Collections.emptyList()));

        verify(drawingMapper, never()).updateById(any(BizDesignDrawing.class));
        verify(drawingMapper, never()).batchInsert(anyList());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> deleteCaptor = ArgumentCaptor.forClass(List.class);
        verify(drawingMapper, times(1)).deleteBatchIds(deleteCaptor.capture());
        assertEquals(Arrays.asList(1L, 2L), deleteCaptor.getValue());
    }

    @Test
    @DisplayName("null 语义：前端未提交图纸字段时不触碰任何图纸写操作")
    void updateDrawings_nullSkipsSync() {
        projectService.update(PROJECT_ID, updateDTO(null));

        verify(drawingMapper, never()).updateById(any(BizDesignDrawing.class));
        verify(drawingMapper, never()).batchInsert(anyList());
        verify(drawingMapper, never()).deleteBatchIds(anyList());
    }

    @Test
    @DisplayName("IDOR 防护：不属于本项目的 id 降级为新增，绝不越权更新他人记录")
    void updateDrawings_foreignIdDegradesToInsert() {
        when(drawingMapper.selectList(any())).thenReturn(Collections.singletonList(
                existingDrawing(1L, "本项目图纸-1")));

        // id=999 属于其他项目（本项目现有集合中不存在）
        projectService.update(PROJECT_ID, updateDTO(Collections.singletonList(
                drawingDTO(999L, "越权图纸", "1MB"))));

        // 绝不按传入 id 更新（防止改写其他项目的记录）
        verify(drawingMapper, never()).updateById(any(BizDesignDrawing.class));
        // 降级为新增：剥离 id 重新插入到当前项目
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BizDesignDrawing>> insertCaptor = ArgumentCaptor.forClass(List.class);
        verify(drawingMapper, times(1)).batchInsert(insertCaptor.capture());
        BizDesignDrawing degraded = insertCaptor.getValue().get(0);
        assertEquals(PROJECT_ID, degraded.getProjectId());
        assertEquals(CURRENT_USER_ID, degraded.getUploadedBy());
        assertNotNull(degraded.getName());
        // 本项目原有图纸因未提交而被删除（全量列表语义）
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> deleteCaptor = ArgumentCaptor.forClass(List.class);
        verify(drawingMapper, times(1)).deleteBatchIds(deleteCaptor.capture());
        assertEquals(Collections.singletonList(1L), deleteCaptor.getValue());
    }
}
