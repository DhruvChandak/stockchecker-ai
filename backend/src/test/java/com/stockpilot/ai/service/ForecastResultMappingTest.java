package com.stockpilot.ai.service;

import com.stockpilot.ai.domain.ForecastResult;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ForecastResultMappingTest {
    @Test
    void numericDemandFieldsUseFlywayColumnNames() throws Exception {
        assertThat(ForecastResult.class.getDeclaredField("next7DaysDemand").getAnnotation(Column.class).name())
            .isEqualTo("next_7_days_demand");
        assertThat(ForecastResult.class.getDeclaredField("next30DaysDemand").getAnnotation(Column.class).name())
            .isEqualTo("next_30_days_demand");
    }
}
