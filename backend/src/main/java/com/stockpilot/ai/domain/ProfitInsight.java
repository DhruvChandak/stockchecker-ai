package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "profit_insights")
public class ProfitInsight extends TenantOwnedEntity {
    public LocalDate periodStart;
    public LocalDate periodEnd;
    public BigDecimal revenue = BigDecimal.ZERO;
    public BigDecimal grossProfit = BigDecimal.ZERO;
    public String explanation;
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> evidenceJson = new HashMap<>();
    public Instant generatedAt = Instant.now();
}
