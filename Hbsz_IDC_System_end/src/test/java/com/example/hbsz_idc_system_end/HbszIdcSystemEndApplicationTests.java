package com.example.hbsz_idc_system_end;

import com.example.hbsz_idc_system_end.service.ITaskService;
import com.example.hbsz_idc_system_end.service.IWorkloadService;
import com.example.hbsz_idc_system_end.utils.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring 上下文装配冒烟测试。
 * <p>
 * 修复目标（T6/T1）：原 contextLoads 依赖真实 MySQL/Redis/Flyway 无法独立运行，
 * 且方法体为空断言（Assertion Roulette 的极端形式——零断言）。
 * <p>
 * 运行条件（全部由 application-test.yml 显式声明，无 Mystery Guest）：
 * <ul>
 *   <li>数据源：H2 内存库（MODE=MySQL），Druid 连接池照常装配；</li>
 *   <li>Flyway 关闭：生产迁移脚本为 MySQL 方言（PREPARE + information_schema），H2 无法执行，
 *       schema 验证不属于本测试职责；</li>
 *   <li>Redis：Lettuce 惰性连接，上下文加载阶段不建连，无需真实实例。</li>
 * </ul>
 * 覆盖的装配风险：@Configuration/@Bean、@MapperScan、Druid 属性绑定、MyBatis-Plus
 * mapper XML 解析、JWT 属性绑定（jwt.secret 无默认值）、Knife4j、WebSocket、
 * 拦截器/切面注册、WorkloadServiceImpl @PostConstruct 模板加载。
 *
 * @author hbsz
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Spring 上下文装配冒烟测试（H2 独立运行）")
class HbszIdcSystemEndApplicationTests {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ITaskService taskService;

    @Autowired
    private IWorkloadService workloadService;

    @Autowired
    private JwtUtils jwtUtils;

    @Test
    @DisplayName("上下文加载后：Druid 池可从 H2 获取连接并执行验证查询")
    void contextLoads_dataSourceConnectsToH2() throws Exception {
        assertNotNull(dataSource, "DataSource 应完成装配");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            assertTrue(rs.next(), "验证查询应返回一行");
            assertEquals(1, rs.getInt(1), "H2 内存库应可执行 SELECT 1");
        }
    }

    @Test
    @DisplayName("上下文加载后：核心业务 Service 与 JWT 工具完成装配")
    void contextLoads_coreBeansAreWired() {
        assertNotNull(taskService, "ITaskService 应完成装配（代表 Service/Mapper 链路）");
        assertNotNull(workloadService, "IWorkloadService 应完成装配（含 @PostConstruct 模板加载成功）");
        assertNotNull(jwtUtils, "JwtUtils 应完成装配（代表 jwt.secret 属性绑定成功）");
    }
}
