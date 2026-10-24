package com.example.fintech.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fintechOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Fintech Customer Insights API")
                        .version("v1")
                        .description("Read-only synthetic customer insights for the governed AI-SDLC baseline. "
                                + "All data is synthetic and does NOT represent real people, real creditworthiness, "
                                + "or real banking records.")
                        .license(new License().name("Proprietary — demonstration use only")));
    }
}
