package com.stockpilot.ai.web.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.stockpilot.ai.domain.DomainEnums;

import java.util.UUID;
import java.time.LocalDate;

public final class SmartImportDtos {
    private SmartImportDtos() {
    }

    public record CreateSessionRequest(
        @Size(max = 160, message = "Session name must be 160 characters or fewer") String name
    ) {
    }

    public record FileTypeOverrideRequest(@NotNull DomainEnums.DetectedFileType selectedFileType) {
    }

    public record ReviewResolutionRequest(
        @NotBlank String action,
        UUID targetId,
        DomainEnums.DetectedFileType selectedFileType
    ) {
    }

    public record DryRunRequest(
        DomainEnums.ImportStrategy strategy,
        DomainEnums.VoucherStockImpactMode stockImpactMode,
        DomainEnums.NegativeStockImportPolicy negativeStockPolicy,
        LocalDate snapshotDate
    ) {
    }

    public record CommitRequest(
        @NotNull UUID dryRunId,
        @NotBlank String confirmation
    ) {
    }

    public record CashbookReviewResolutionRequest(
        @NotNull DomainEnums.CashbookResolutionAction action,
        UUID targetId,
        @Size(max = 80) String confirmation,
        @Size(max = 500) String note
    ) {
    }
}
