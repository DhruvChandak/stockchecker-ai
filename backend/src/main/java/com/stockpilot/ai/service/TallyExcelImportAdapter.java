package com.stockpilot.ai.service;

import com.stockpilot.ai.domain.DomainEnums;
import org.springframework.stereotype.Component;

@Component
public class TallyExcelImportAdapter extends GenericExcelImportAdapter {
    @Override
    public DomainEnums.SourceType sourceType() {
        return DomainEnums.SourceType.TALLY_EXCEL;
    }
}
