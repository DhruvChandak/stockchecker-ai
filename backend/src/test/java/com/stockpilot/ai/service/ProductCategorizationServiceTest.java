package com.stockpilot.ai.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCategorizationServiceTest {
    private final ProductCategorizationService service = new ProductCategorizationService();

    @Test
    void normalizesMessyTallyItemNamesIntoUsefulProductSuggestions() {
        var suggestion = service.suggest("MAGGI MASLA 70");

        assertThat(suggestion.get("brand")).isEqualTo("Maggi");
        assertThat(suggestion.get("category")).isEqualTo("Instant Noodles");
        assertThat(suggestion.get("unitSize")).isEqualTo("70g");
        assertThat(suggestion.get("normalizedName")).isEqualTo("Maggi Masala Noodles 70g");
    }
}
