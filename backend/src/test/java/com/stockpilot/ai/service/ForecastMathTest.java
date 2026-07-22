package com.stockpilot.ai.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ForecastMathTest {
    @Test
    void calculatesMovingAverageAndReorderQuantity() {
        var daily = List.of(BigDecimal.ONE, BigDecimal.valueOf(2), BigDecimal.valueOf(3));

        assertThat(ForecastMath.averageDailyDemand(daily)).isEqualByComparingTo("2.000");
        assertThat(ForecastMath.reorderPoint(new BigDecimal("2.5"), 4, BigDecimal.TEN)).isEqualByComparingTo("20.000");
        assertThat(ForecastMath.suggestedQuantity(new BigDecimal("20"), new BigDecimal("30"), new BigDecimal("12"), new BigDecimal("24"))).isEqualByComparingTo("38.000");
    }
}
