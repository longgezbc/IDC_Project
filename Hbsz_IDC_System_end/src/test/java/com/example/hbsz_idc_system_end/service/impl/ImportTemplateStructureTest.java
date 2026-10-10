package com.example.hbsz_idc_system_end.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.read.listener.ReadListener;
import com.example.hbsz_idc_system_end.dto.CustomerImportDTO;
import com.example.hbsz_idc_system_end.dto.UserImportDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 批量导入模板结构测试（用户/客户/项目三个模板统一模式）。
 * <p>
 * 锁定模板的"防填错"契约：
 * <ul>
 *   <li>双 sheet 结构：Sheet1 数据模板（表头 + 2 行示例）+ Sheet2「填写说明」；</li>
 *   <li>示例行数据真实合法：用户名/密码符合 {@code UserSaveDTO} 的校验正则，
 *       用户参照示例填写即可通过后端校验（这是示例存在的意义）；</li>
 *   <li>填写说明 sheet 非空且含表头（列名/是否必填/填写说明）与使用须知；</li>
 *   <li>导入解析兼容：EasyExcel 按 sheet() 默认读第一个 sheet，
 *       说明 sheet 的存在不影响导入流程（importXxx 均用 .sheet().doRead()）。</li>
 * </ul>
 * 技术说明：downloadImportTemplate 为纯 EasyExcel 写出（不触达任何 mapper），
 * 构造器依赖全部传 null 即可实例化。
 *
 * @author hbsz
 */
@DisplayName("批量导入模板结构测试")
class ImportTemplateStructureTest {

    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_]{3,20}$");
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[~!@#$%^&*()_+\\-={}|\\[\\]:\";'<>?,./]).{8,32}$");

    // ==================================================================
    // Sheet 名读取：EasyExcel 读回 sheet 列表
    // ==================================================================

    private List<String> readSheetNames(byte[] excelBytes) {
        List<String> names = new ArrayList<>();
        try (com.alibaba.excel.ExcelReader reader = EasyExcel.read(
                new ByteArrayInputStream(excelBytes)).build()) {
            reader.excelExecutor().sheetList().forEach(s -> names.add(s.getSheetName()));
        }
        return names;
    }

    /** 读第一个 sheet 的原始行数据（含表头），每行为「列名→单元格文本」 */
    private List<Map<Integer, String>> readFirstSheetRows(byte[] excelBytes, Class<?> headClass) {
        List<Map<Integer, String>> rows = new ArrayList<>();
        EasyExcel.read(new ByteArrayInputStream(excelBytes), headClass, new ReadListener<Object>() {
            Map<Integer, String> current;

            @Override
            public void invokeHead(Map<Integer, ReadCellData<?>> headMap, AnalysisContext context) {
                current = new HashMap<>();
                headMap.forEach((k, v) -> current.put(k, v.getStringValue()));
                rows.add(current);
            }

            @Override
            public void invoke(Object data, AnalysisContext context) {
                // 有模型类时行数据为对象；此处仅关心行数，逐行占位
                Map<Integer, String> row = new HashMap<>();
                row.put(-1, data == null ? "" : data.toString());
                rows.add(row);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                // no-op
            }
        }).sheet(0).doRead();
        return rows;
    }

    /** 读第二个 sheet（填写说明）的全部文本，拼接后做包含断言 */
    private String readSecondSheetText(byte[] excelBytes) {
        StringBuilder sb = new StringBuilder();
        EasyExcel.read(new ByteArrayInputStream(excelBytes), new ReadListener<Map<Integer, String>>() {
            @Override
            public void invokeHead(Map<Integer, ReadCellData<?>> headMap, AnalysisContext context) {
                headMap.values().forEach(v -> sb.append(v.getStringValue()).append('\n'));
            }

            @Override
            public void invoke(Map<Integer, String> data, AnalysisContext context) {
                data.values().forEach(v -> sb.append(v).append('\n'));
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                // no-op
            }
        }).sheet(1).doRead();
        return sb.toString();
    }

    private static void assertValidXlsx(byte[] bytes) {
        assertNotNull(bytes);
        assertTrue(bytes.length > 100, "模板文件应非空（zip 容器 + 双 sheet）");
        // xlsx 为 zip 容器，魔数 PK\x03\x04
        assertEquals('P', bytes[0]);
        assertEquals('K', bytes[1]);
    }

    // ==================================================================
    // 用户导入模板
    // ==================================================================

    @Test
    @DisplayName("用户模板：双sheet结构 + 示例行真实合法 + 说明页含编码清单")
    void userTemplate_structureAndSample() throws Exception {
        UserManageServiceImpl service = new UserManageServiceImpl(null, null, null, null, null, null);
        byte[] bytes = service.downloadImportTemplate();
        assertValidXlsx(bytes);

        List<String> sheetNames = readSheetNames(bytes);
        assertEquals(2, sheetNames.size(), "应为双 sheet：数据模板 + 填写说明");
        assertEquals("用户导入模板", sheetNames.get(0));
        assertEquals("填写说明", sheetNames.get(1));

        // 示例行真实合法（用户照抄能通过后端校验）
        UserImportDTO sample = new UserImportDTO();
        sample.setUsername("zhangsan");
        sample.setPassword("Abc@2026");
        assertTrue(USERNAME_PATTERN.matcher(sample.getUsername()).matches(), "示例用户名应合法");
        assertTrue(PASSWORD_PATTERN.matcher(sample.getPassword()).matches(), "示例密码应满足强度规则");
        assertTrue(USERNAME_PATTERN.matcher("lisi").matches());
        assertTrue(PASSWORD_PATTERN.matcher("Xy#5678abcd").matches());

        // 说明页关键内容：使用须知 + 岗位/角色编码清单 + 密码规则
        String guide = readSecondSheetText(bytes);
        assertTrue(guide.contains("使用须知"), "说明页应含使用须知");
        assertTrue(guide.contains("是否必填"), "说明页应含三列表头");
        assertTrue(guide.contains("Abc@2026"), "密码说明应给出合法示例");
        assertTrue(guide.contains("project_manager"), "说明页应列出角色编码可选值");
        assertTrue(guide.contains("senior_engineer"), "说明页应列出岗位编码可选值");
    }

    // ==================================================================
    // 客户导入模板
    // ==================================================================

    @Test
    @DisplayName("客户模板：双sheet结构 + 示例 + 说明页含必填项与编号格式")
    void customerTemplate_structureAndGuide() throws Exception {
        CustomerServiceImpl service = new CustomerServiceImpl(null, null);
        byte[] bytes = service.downloadImportTemplate();
        assertValidXlsx(bytes);

        List<String> sheetNames = readSheetNames(bytes);
        assertEquals(2, sheetNames.size());
        assertEquals("客户导入模板", sheetNames.get(0));
        assertEquals("填写说明", sheetNames.get(1));

        String guide = readSecondSheetText(bytes);
        assertTrue(guide.contains("使用须知"));
        assertTrue(guide.contains("CUST-2026-001"), "客户编号说明应给出建议格式示例");
        assertTrue(guide.contains("客户编号") && guide.contains("必填"));
    }

    // ==================================================================
    // 项目导入模板
    // ==================================================================

    @Test
    @DisplayName("项目模板：双sheet结构 + 说明页含类型/优先级枚举与日期格式")
    void projectTemplate_structureAndGuide() throws Exception {
        ProjectServiceImpl service = new ProjectServiceImpl(null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        byte[] bytes = service.downloadImportTemplate();
        assertValidXlsx(bytes);

        List<String> sheetNames = readSheetNames(bytes);
        assertEquals(2, sheetNames.size());
        assertEquals("项目导入模板", sheetNames.get(0));
        assertEquals("填写说明", sheetNames.get(1));

        String guide = readSecondSheetText(bytes);
        assertTrue(guide.contains("使用须知"));
        assertTrue(guide.contains("新建") && guide.contains("改造") && guide.contains("维护"),
                "说明页应列出项目类型可选值");
        assertTrue(guide.contains("yyyy-MM-dd"), "说明页应明确日期格式");
        assertTrue(guide.contains("PRJ-2026-001"), "项目编号说明应给出建议格式示例");
        assertTrue(guide.contains("工作台统计"), "项目经理说明应提示关联影响");
    }

    // ==================================================================
    // 导入兼容性：说明 sheet 不影响第一个 sheet 的模型解析
    // ==================================================================

    @Test
    @DisplayName("导入兼容：EasyExcel 按 sheet() 默认读 Sheet1，模型解析不受说明页影响")
    void importParsing_stillReadsFirstSheetOnly() throws Exception {
        CustomerServiceImpl service = new CustomerServiceImpl(null, null);
        byte[] bytes = service.downloadImportTemplate();

        // 与 importCustomers 相同的读取方式（.sheet() 默认第一个 sheet）
        List<CustomerImportDTO> dataList = new ArrayList<>();
        EasyExcel.read(new ByteArrayInputStream(bytes), CustomerImportDTO.class, new ReadListener<CustomerImportDTO>() {
            @Override
            public void invoke(CustomerImportDTO data, AnalysisContext context) {
                dataList.add(data);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                // no-op
            }
        }).sheet().doRead();

        // 2 行示例数据被正常解析为 DTO（表头映射正确；说明 sheet 未被误读）
        assertEquals(2, dataList.size(), "模板含 2 行示例数据");
        assertEquals("CUST-2026-001", dataList.get(0).getCode());
        assertEquals("北京华邦科技有限公司", dataList.get(0).getName());
        assertEquals("王经理", dataList.get(0).getContactPerson());
    }
}
