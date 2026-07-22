package com.stockpilot.ai.service;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ProductCategorizationService {
    private static final Pattern SIZE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s?(GM|G|KG|ML|L|LTR|PCS)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOOSE_SIZE = Pattern.compile("\\b(\\d{2,4})(?:\\s|$)");

    public Map<String, String> suggest(String rawName) {
        var name = cleanup(rawName == null ? "" : rawName.trim());
        var upper = name.toUpperCase(Locale.ROOT);
        var brand = brand(upper);
        var category = category(upper);
        var size = size(upper, brand);
        var normalized = normalizedName(name, brand, category, size);
        return Map.of("brand", brand, "category", category, "unitSize", size, "normalizedName", normalized);
    }

    private String cleanup(String value) {
        return value
            .replaceAll("(?i)\\bMASLA\\b", "Masala")
            .replaceAll("(?i)\\bNOODLS\\b", "Noodles")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String size(String upper, String brand) {
        var matcher = SIZE.matcher(upper);
        if (matcher.find()) {
            return matcher.group(1) + matcher.group(2).toLowerCase(Locale.ROOT).replace("gm", "g").replace("ltr", "l");
        }
        if ("Maggi".equals(brand)) {
            var loose = LOOSE_SIZE.matcher(upper);
            if (loose.find()) {
                return loose.group(1) + "g";
            }
        }
        return "";
    }

    private String normalizedName(String name, String brand, String category, String size) {
        var upper = name.toUpperCase(Locale.ROOT);
        if ("Maggi".equals(brand) && "Instant Noodles".equals(category) && !size.isBlank()) {
            return "Maggi Masala Noodles " + size;
        }
        var normalized = (brand.isBlank() ? name : brand + " " + name.replaceFirst("(?i)^" + Pattern.quote(brand), "").trim()).replaceAll("\\s+", " ").trim();
        if (!size.isBlank() && !normalized.toLowerCase(Locale.ROOT).contains(size.toLowerCase(Locale.ROOT))) {
            normalized = normalized + " " + size;
        }
        return normalized;
    }

    private String brand(String upper) {
        if (upper.contains("MAGGI")) return "Maggi";
        if (upper.contains("PARLE")) return "Parle";
        if (upper.contains("DAIRY MILK")) return "Dairy Milk";
        if (upper.contains("SURF EXCEL")) return "Surf Excel";
        if (upper.contains("TATA")) return "Tata";
        if (upper.contains("DETTOL")) return "Dettol";
        if (upper.contains("FORTUNE")) return "Fortune";
        if (upper.contains("COLGATE")) return "Colgate";
        if (upper.contains("GOOD DAY")) return "Good Day";
        if (upper.contains("RED LABEL")) return "Red Label";
        return "";
    }

    private String category(String upper) {
        if (upper.contains("NOODLE") || upper.contains("MAGGI")) return "Instant Noodles";
        if (upper.contains("BISCUIT") || upper.contains("PARLE-G") || upper.contains("GOOD DAY")) return "Biscuits";
        if (upper.contains("CHOCOLATE") || upper.contains("DAIRY MILK")) return "Confectionery";
        if (upper.contains("DETERGENT") || upper.contains("SURF")) return "Laundry";
        if (upper.contains("SALT")) return "Staples";
        if (upper.contains("SOAP") || upper.contains("DETTOL")) return "Personal Care";
        if (upper.contains("OIL")) return "Edible Oil";
        if (upper.contains("TOOTHPASTE")) return "Oral Care";
        if (upper.contains("TEA")) return "Beverages";
        return "Uncategorized";
    }
}
