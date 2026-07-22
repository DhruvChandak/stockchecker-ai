package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "import_mapping_templates")
public class ImportMappingTemplate extends TenantOwnedEntity {
    public String name;
    @Enumerated(EnumType.STRING)
    public DomainEnums.SourceType sourceType;
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> mappingJson = new HashMap<>();
}
