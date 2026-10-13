package com.friendlystation.platform.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI 元信息：接口文档通过 /v3/api-docs 导出，用于生成 docs/接口文档.md。 */
@Configuration
public class OpenApiConfig {
    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI friendlyStationOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("新就业群体友好驿站服务与诉求响应平台 API")
                        .version("1.0.0")
                        .description("""
                                基于 Spring Boot + Vue 的友好驿站服务与诉求响应平台后端接口。

                                统一返回结构：`{ "code": 200, "message": "success", "data": ... }`；
                                分页结构：`{ "current": 1, "size": 10, "total": 0, "records": [] }`。

                                鉴权：登录后取得的 token 通过 `Authorization: Bearer <token>` 传递。
                                角色为 USER、STATION_ADMIN、PLATFORM_ADMIN、SUPER_ADMIN，
                                受保护接口会校验角色，越权返回 403，未登录或凭证失效返回 401。
                                """)
                        .contact(new Contact().name("毕业设计项目"))
                        .license(new License().name("仅用于教学与毕业设计演示")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录接口返回的 token")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
