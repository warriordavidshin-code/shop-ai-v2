package com.petitcamel.shop.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AiAnalysisProperties.class, AiCostProperties.class})
public class AiAnalysisConfig {
}
