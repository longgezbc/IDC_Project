package com.example.hbsz_idc_system_end.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / OpenAPI 3 (Swagger) 接口文档配置类
 * <p>
 * 基于 springdoc-openapi 定制 API 文档的元信息（标题、描述、版本、联系人、许可证），
 * 供 Knife4j 增强 UI 渲染。文档访问地址: http://localhost:8080/api/doc.html
 *
 * @author hbsz
 */
@Configuration
public class Knife4jConfig {

    /**
     * 构建 OpenAPI 文档描述对象，定义文档的基础信息。
     *
     * @return 包含标题、描述、版本、联系人与许可证的 {@link OpenAPI} 实例
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("数据中心综合布线项目管理系统 - API文档")
                        .description("数据中心综合布线项目管理系统后端接口文档")
                        .version("V1.0.0")
                        .contact(new Contact()
                                .name("hbsz")
                                .email("support@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }

}
