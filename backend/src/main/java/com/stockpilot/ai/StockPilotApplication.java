package com.stockpilot.ai;

import com.stockpilot.ai.repo.Repositories;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableCaching
@EnableJpaRepositories(basePackageClasses = Repositories.class, considerNestedRepositories = true)
public class StockPilotApplication {
    public static void main(String[] args) {
        SpringApplication.run(StockPilotApplication.class, args);
    }
}
