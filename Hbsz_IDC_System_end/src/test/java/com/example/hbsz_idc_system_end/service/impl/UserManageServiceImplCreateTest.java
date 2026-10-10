package com.example.hbsz_idc_system_end.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.dto.UserImportDTO;
import com.example.hbsz_idc_system_end.dto.UserSaveDTO;
import com.example.hbsz_idc_system_end.dto.ImportResultVO;
import com.example.hbsz_idc_system_end.entity.SysPosition;
import com.example.hbsz_idc_system_end.entity.SysRole;
import com.example.hbsz_idc_system_end.entity.SysUser;
import com.example.hbsz_idc_system_end.mapper.SysPositionMapper;
import com.example.hbsz_idc_system_end.mapper.SysRoleMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserRoleMapper;
import com.example.hbsz_idc_system_end.service.IPermissionService;
import com.example.hbsz_idc_system_end.utils.PasswordUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserManageServiceImpl} 用户创建与批量导入特征测试。
 * <p>
 * 此前该服务（权限敏感、570 行）无任何测试对位——本类锁定最高风险契约：
 * <ul>
 *   <li>create：三段非空校验（用户名/密码/姓名）→ BAD_REQUEST；
 *       用户名唯一性（含已删除账号维度 selectByUsernameAll）→ DATA_ALREADY_EXISTS；
 *       密码必须经 {@link PasswordUtils#encode} 加密落库（明文密码入库是安全事故）；
 *       status 默认启用；角色列表写入 user_role 关联；</li>
 *   <li>getById：不存在 → DATA_NOT_FOUND；</li>
 *   <li>importUsers：Excel 行级容错（单行失败不阻断整批）；
 *       岗位/角色编码由编码转 ID（不存在时该行报错并带行号）；
 *       importUsers 经 self 代理调用 create（事务注解生效前提），测试以反射注入自身。</li>
 * </ul>
 * 技术说明：LambdaQueryWrapper 依赖 TableInfo 缓存，纯 Mockito 环境需 @BeforeAll 手动注册。
 *
 * @author hbsz
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("用户创建与批量导入测试")
class UserManageServiceImplCreateTest {

    private static final Long NEW_USER_ID = 600L;

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private SysPositionMapper sysPositionMapper;
    @Mock
    private PasswordUtils passwordUtils;
    @Mock
    private IPermissionService permissionService;

    private UserManageServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 解析实体列名依赖 TableInfo 缓存（无 Spring 上下文需手动注册）
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysUser.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), com.example.hbsz_idc_system_end.entity.SysUserRole.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysPosition.class);
    }

    @BeforeEach
    void setUp() {
        service = new UserManageServiceImpl(sysUserMapper, sysRoleMapper, sysUserRoleMapper,
                sysPositionMapper, passwordUtils, permissionService);
        // importUsers 通过 self 代理调用 create（保证 @Transactional 生效），单测注入自身
        ReflectionTestUtils.setField(service, "self", service);
    }

    // ==================================================================
    // create：参数校验
    // ==================================================================

    @Test
    @DisplayName("create：用户名为空 → BAD_REQUEST")
    void create_blankUsername_throws() {
        UserSaveDTO dto = validDto();
        dto.setUsername(" ");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));

        assertEquals("用户名不能为空", ex.getMessage());
        verify(sysUserMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("create：密码为空 → BAD_REQUEST")
    void create_blankPassword_throws() {
        UserSaveDTO dto = validDto();
        dto.setPassword("");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));

        assertEquals("密码不能为空", ex.getMessage());
    }

    @Test
    @DisplayName("create：姓名为空 → BAD_REQUEST")
    void create_blankName_throws() {
        UserSaveDTO dto = validDto();
        dto.setName(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));

        assertEquals("姓名不能为空", ex.getMessage());
    }

    @Test
    @DisplayName("create：用户名已存在 → DATA_ALREADY_EXISTS（唯一性按含已删账号校验）")
    void create_duplicateUsername_throws() {
        UserSaveDTO dto = validDto();
        when(sysUserMapper.selectByUsernameAll("zhangsan")).thenReturn(new SysUser());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));

        assertEquals("用户名已存在", ex.getMessage());
        verify(sysUserMapper, never()).insert(any(SysUser.class));
    }

    // ==================================================================
    // create：成功路径（密码加密 / 默认状态 / 角色关联）
    // ==================================================================

    @Test
    @DisplayName("create：成功 → 密码加密落库、默认启用、角色写入关联表")
    void create_success_encodesPasswordAndSavesRoles() {
        UserSaveDTO dto = validDto();
        dto.setRoleIds(List.of(2L, 3L));
        when(sysUserMapper.selectByUsernameAll("zhangsan")).thenReturn(null);
        when(passwordUtils.encode("Plain@123")).thenReturn("{bcrypt}encoded");
        // 模拟 MyBatis-Plus insert 回填自增主键
        doAnswer(inv -> {
            SysUser u = inv.getArgument(0);
            u.setId(NEW_USER_ID);
            return 1;
        }).when(sysUserMapper).insert(any(SysUser.class));
        stubGetByIdPath();

        service.create(dto);

        // 密码必须加密后落库（明文入库即安全事故）
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(captor.capture());
        assertEquals("{bcrypt}encoded", captor.getValue().getPassword());
        assertNotEquals("Plain@123", captor.getValue().getPassword());
        // 未传 status → 默认启用
        assertEquals(1, captor.getValue().getStatus());
        // 角色关联批量写入
        verify(sysUserRoleMapper).batchInsert(anyList());
    }

    @Test
    @DisplayName("create：未指定角色 → 不写 user_role 关联")
    void create_noRoles_skipsUserRoleInsert() {
        UserSaveDTO dto = validDto();
        when(sysUserMapper.selectByUsernameAll("zhangsan")).thenReturn(null);
        when(passwordUtils.encode(anyString())).thenReturn("{bcrypt}encoded");
        doAnswer(inv -> {
            SysUser u = inv.getArgument(0);
            u.setId(NEW_USER_ID);
            return 1;
        }).when(sysUserMapper).insert(any(SysUser.class));
        stubGetByIdPath();

        service.create(dto);

        verify(sysUserRoleMapper, never()).batchInsert(anyList());
    }

    @Test
    @DisplayName("getById：用户不存在 → DATA_NOT_FOUND")
    void getById_missing_throws() {
        when(sysUserMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(999L));

        assertEquals("用户不存在", ex.getMessage());
    }

    // ==================================================================
    // importUsers：Excel 行级容错与编码转换
    // ==================================================================

    @Test
    @DisplayName("importUsers：岗位编码不存在 → 该行失败并提示行号，整批不中断")
    void importUsers_unknownPosition_reportsRowError() throws Exception {
        stubCreateSuccess();
        // 岗位编码查询返回空 → 该行失败
        when(sysPositionMapper.selectOne(any())).thenReturn(null);

        ImportResultVO result = service.importUsers(excelOf(row("newuser1", "新用户一", "Plain@123",
                null, null, "bad_position", null)));

        assertEquals(1, result.getTotal());
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("第2行"), "错误应带 Excel 行号（表头为第1行）");
        assertTrue(result.getErrors().get(0).contains("岗位编码不存在"));
    }

    @Test
    @DisplayName("importUsers：角色编码不存在 → 该行失败（区别于岗位错误）")
    void importUsers_unknownRole_reportsRowError() throws Exception {
        stubCreateSuccess();
        when(sysPositionMapper.selectOne(any())).thenReturn(position(10L, "engineer"));
        when(sysRoleMapper.selectByCode("bad_role")).thenReturn(null);

        ImportResultVO result = service.importUsers(excelOf(row("newuser1", "新用户一", "Plain@123",
                null, null, "engineer", "bad_role")));

        assertEquals(0, result.getSuccessCount());
        assertTrue(result.getErrors().get(0).contains("角色编码不存在"));
    }

    @Test
    @DisplayName("importUsers：合法行（编码转ID）→ 成功计数并分配角色")
    void importUsers_validRow_convertsCodesToIds() throws Exception {
        stubCreateSuccess();
        when(sysPositionMapper.selectOne(any())).thenReturn(position(10L, "engineer"));
        when(sysRoleMapper.selectByCode("engineer")).thenReturn(role(2L, "engineer"));

        ImportResultVO result = service.importUsers(excelOf(row("newuser1", "新用户一", "Plain@123",
                null, null, "engineer", "engineer")));

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getErrors().size());
        // 角色编码已转换为角色 ID 并写入关联
        verify(sysUserRoleMapper).batchInsert(anyList());
    }

    @Test
    @DisplayName("importUsers：空数据 → 总数 0 且无错误")
    void importUsers_empty_reportsZero() throws Exception {
        stubCreateSuccess();

        ImportResultVO result = service.importUsers(excelOf());

        assertEquals(0, result.getTotal());
        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getErrors().size());
    }

    // ==================================================================
    // 构造辅助
    // ==================================================================

    /** 最小合法入参（三必填齐全） */
    private UserSaveDTO validDto() {
        UserSaveDTO dto = new UserSaveDTO();
        dto.setUsername("zhangsan");
        dto.setPassword("Plain@123");
        dto.setName("张三");
        return dto;
    }

    /** getById 返回路径桩：selectById 回放已插入用户、无角色关联 */
    private void stubGetByIdPath() {
        when(sysUserMapper.selectById(any(Long.class))).thenAnswer(inv -> {
            SysUser u = new SysUser();
            u.setId(inv.getArgument(0));
            u.setUsername("zhangsan");
            u.setName("张三");
            u.setStatus(1);
            return u;
        });
        when(sysUserRoleMapper.selectList(any())).thenReturn(Collections.emptyList());
    }

    /** create 成功路径公共桩（用户名不重复 + 密码加密 + 回填ID + getById 可读回） */
    private void stubCreateSuccess() {
        when(sysUserMapper.selectByUsernameAll(anyString())).thenReturn(null);
        when(passwordUtils.encode(anyString())).thenReturn("{bcrypt}encoded");
        doAnswer(inv -> {
            SysUser u = inv.getArgument(0);
            u.setId(NEW_USER_ID);
            return 1;
        }).when(sysUserMapper).insert(any(SysUser.class));
        stubGetByIdPath();
    }

    private SysPosition position(Long id, String code) {
        SysPosition p = new SysPosition();
        p.setId(id);
        p.setCode(code);
        return p;
    }

    private SysRole role(Long id, String code) {
        SysRole r = new SysRole();
        r.setId(id);
        r.setRoleCode(code);
        return r;
    }

    private UserImportDTO row(String username, String name, String password,
                              String phone, String email, String positionCode, String roleCode) {
        UserImportDTO dto = new UserImportDTO();
        dto.setUsername(username);
        dto.setName(name);
        dto.setPassword(password);
        dto.setPhone(phone);
        dto.setEmail(email);
        dto.setPositionCode(positionCode);
        dto.setRoleCode(roleCode);
        return dto;
    }

    /** 将 DTO 列表写成真实 xlsx 再包装为 MultipartFile（与上传链路一致） */
    private MockMultipartFile excelOf(UserImportDTO... rows) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (com.alibaba.excel.ExcelWriter writer = EasyExcel.write(out).build()) {
            com.alibaba.excel.write.metadata.WriteSheet sheet =
                    EasyExcel.writerSheet(0, "用户导入模板").head(UserImportDTO.class).build();
            writer.write(List.of(rows), sheet);
        }
        return new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(out.toByteArray()).readAllBytes());
    }
}
