package com.stockpilot.ai.service;

import com.stockpilot.ai.domain.DomainEnums;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StockLedgerMathTest {
    @Test
    void signsMovementQuantitiesByType() {
        assertThat(StockLedgerService.signedQuantity(DomainEnums.MovementType.PURCHASE, new BigDecimal("5"))).isEqualByComparingTo("5");
        assertThat(StockLedgerService.signedQuantity(DomainEnums.MovementType.SALE, new BigDecimal("5"))).isEqualByComparingTo("-5");
        assertThat(StockLedgerService.signedQuantity(DomainEnums.MovementType.RETURN_IN, new BigDecimal("5"))).isEqualByComparingTo("5");
        assertThat(StockLedgerService.signedQuantity(DomainEnums.MovementType.RETURN_OUT, new BigDecimal("5"))).isEqualByComparingTo("-5");
        assertThat(StockLedgerService.signedQuantity(DomainEnums.MovementType.TRANSFER_IN, new BigDecimal("5"))).isEqualByComparingTo("5");
        assertThat(StockLedgerService.signedQuantity(DomainEnums.MovementType.TRANSFER_OUT, new BigDecimal("5"))).isEqualByComparingTo("-5");
        assertThat(StockLedgerService.signedQuantity(DomainEnums.MovementType.OPENING_BALANCE, new BigDecimal("5"))).isEqualByComparingTo("5");
        assertThat(StockLedgerService.signedQuantity(DomainEnums.MovementType.ADJUSTMENT, new BigDecimal("-2"))).isEqualByComparingTo("-2");
    }

    @Test
    void movementSignsSumToCurrentStockProjection() {
        var projected = StockLedgerService.signedQuantity(DomainEnums.MovementType.OPENING_BALANCE, new BigDecimal("10"))
            .add(StockLedgerService.signedQuantity(DomainEnums.MovementType.PURCHASE, new BigDecimal("5")))
            .add(StockLedgerService.signedQuantity(DomainEnums.MovementType.SALE, new BigDecimal("3")))
            .add(StockLedgerService.signedQuantity(DomainEnums.MovementType.RETURN_IN, new BigDecimal("2")))
            .add(StockLedgerService.signedQuantity(DomainEnums.MovementType.RETURN_OUT, new BigDecimal("1")))
            .add(StockLedgerService.signedQuantity(DomainEnums.MovementType.TRANSFER_IN, new BigDecimal("4")))
            .add(StockLedgerService.signedQuantity(DomainEnums.MovementType.TRANSFER_OUT, new BigDecimal("6")))
            .add(StockLedgerService.signedQuantity(DomainEnums.MovementType.ADJUSTMENT, new BigDecimal("-2")));

        assertThat(projected).isEqualByComparingTo("9");
    }
}
