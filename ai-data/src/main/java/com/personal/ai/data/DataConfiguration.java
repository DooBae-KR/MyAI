package com.personal.ai.data;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** 앱 진입점 패키지(com.personal.ai.api)와 다르므로 엔티티/Repository 스캔 위치를 명시한다. */
@Configuration
@EntityScan("com.personal.ai.data")
@EnableJpaRepositories("com.personal.ai.data")
public class DataConfiguration {
}
