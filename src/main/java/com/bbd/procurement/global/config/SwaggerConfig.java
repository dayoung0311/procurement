package com.bbd.procurement.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_AUTH = "bearerAuth";
    private static final String AUTH_USER_ID = "X-User-Id";
    private static final String AUTH_USER_ROLE = "X-User-Role";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("procurement API")
                        .description("procurement Application API Documentation - "
                                + "Gateway에서 Keycloak JWT 인증 후 X-User-Id, X-User-Role 헤더 전달")
                        .version("v1.0"))

                // ===== 서버 목록 (두 설정 합침, 중복 localhost:8084 제거) =====
                .addServersItem(new Server()
                        .url("http://localhost:8084/procurement")
                        .description("local 도커 컴포즈 / 직접 띄울 때"))
                .addServersItem(new Server()
                        .url("http://localhost:8080/procurement")
                        .description("Local Local"))
                .addServersItem(new Server()
                        .url("http://192.168.201.110/procurement")
                        .description("Nginx"))
                .addServersItem(new Server()
                        .url("http://192.168.200.220/procurement")
                        .description("강의실 노트북"))
                .addServersItem(new Server()
                        .url("http://100.73.142.41/procurement")
                        .description("TailScale 강의실 노트북"))
                .addServersItem(new Server()
                        .url("http://112.218.95.58/procurement")
                        .description("External Nginx"))
                .addServersItem(new Server()
                        .url("https://bbd.inwoohub.com/procurement")
                        .description("ECS"))

                // ===== 인증 스킴: JWT Bearer + 헤더(X-User-Id, X-User-Role) =====
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Keycloak Access Token 입력 (Gateway 경유 시)"))
                        .addSecuritySchemes(AUTH_USER_ID, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(AUTH_USER_ID)
                                .description("사용자 사번 (서비스 직접 호출 시)"))
                        .addSecuritySchemes(AUTH_USER_ROLE, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(AUTH_USER_ROLE)
                                .description("사용자 역할 (HQ_MANAGER, HQ_STAFF, BRANCH)")))

                // ===== 보안 요구사항 =====
                // 방식 A: JWT Bearer 하나로 인증 (Gateway 경유)
                // 방식 B: X-User-Id + X-User-Role 둘 다 필요 (서비스 직접 호출)
                .security(List.of(
                        new SecurityRequirement().addList(BEARER_AUTH),
                        new SecurityRequirement()
                                .addList(AUTH_USER_ID)
                                .addList(AUTH_USER_ROLE)
                ));
    }
}