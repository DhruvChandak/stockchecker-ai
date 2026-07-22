package com.stockpilot.ai.service;

import com.stockpilot.ai.domain.DomainEnums;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class TallyXmlImportAdapter extends GenericXmlImportAdapter {
    @Override
    public DomainEnums.SourceType sourceType() {
        return DomainEnums.SourceType.TALLY_XML;
    }

    @Override
    public ImportPreview parse(ImportRequest request) {
        try {
            var document = parseDocument(request.inputStream());
            var rows = new ArrayList<Map<String, String>>();
            var stockItems = document.getElementsByTagName("STOCKITEM");
            for (int i = 0; i < stockItems.getLength(); i++) {
                var item = (Element) stockItems.item(i);
                var row = new LinkedHashMap<String, String>();
                row.put("Item Name", item.getAttribute("NAME"));
                row.put("Unit", text(item, "BASEUNITS"));
                row.put("Category", text(item, "PARENT"));
                row.put("Opening Stock", numberPrefix(text(item, "OPENINGBALANCE")));
                row.put("Purchase Price", numberPrefix(text(item, "OPENINGRATE")));
                row.put("Sales Price", numberPrefix(text(item, "RATEOFVAT")));
                row.put("HSN", text(item, "HSNCODE"));
                row.put("HSN Code", text(item, "HSNCODE"));
                row.put("GST %", text(item, "RATEDETAILS.LIST"));
                row.put("Source Entity", "STOCKITEM");
                rows.add(row);
            }
            var ledgers = document.getElementsByTagName("LEDGER");
            for (int i = 0; i < ledgers.getLength(); i++) {
                var ledger = (Element) ledgers.item(i);
                var row = new LinkedHashMap<String, String>();
                row.put("Party Name", ledger.getAttribute("NAME"));
                row.put("GSTIN", text(ledger, "PARTYGSTIN"));
                row.put("GST No", text(ledger, "PARTYGSTIN"));
                row.put("Ledger Group", text(ledger, "PARENT"));
                row.put("Source Entity", "LEDGER");
                rows.add(row);
            }
            addDisplayStockRows(document, rows);
            var entries = document.getElementsByTagName("ALLINVENTORYENTRIES.LIST");
            var financialsEmitted = Collections.newSetFromMap(new IdentityHashMap<Element, Boolean>());
            for (int i = 0; i < entries.getLength(); i++) {
                var entry = (Element) entries.item(i);
                var voucher = parentElement(entry, "VOUCHER");
                var voucherType = voucher == null ? "" : firstNonBlank(voucher.getAttribute("VCHTYPE"), textIgnoreCase(voucher, "VOUCHERTYPENAME"));
                var rawBilledQuantity = textIgnoreCase(entry, "BILLEDQTY");
                var rawActualQuantity = textIgnoreCase(entry, "ACTUALQTY");
                var rawQuantity = firstNonBlank(rawBilledQuantity, rawActualQuantity);
                var rawRate = textIgnoreCase(entry, "RATE");
                var rawAmount = textIgnoreCase(entry, "AMOUNT");
                var purchaseLike = isPurchaseVoucher(voucherType) || isPurchaseReturnVoucher(voucherType);
                var purchaseReturn = isPurchaseReturnVoucher(voucherType);
                var quantity = numberPrefix(rawQuantity);
                if (purchaseReturn) {
                    quantity = absoluteNumber(quantity);
                }
                var rateResolution = purchaseLike
                    ? resolvePurchaseRate(rawRate, rawAmount, rawQuantity)
                    : new PurchaseRateResolution(numberPrefix(rawRate), "RATE_FIELD");
                var row = new LinkedHashMap<String, String>();
                row.put("Voucher Type", voucherType);
                row.put("Voucher Date", voucher == null ? "" : text(voucher, "DATE"));
                row.put("Voucher Number", voucher == null ? "" : text(voucher, "VOUCHERNUMBER"));
                row.put("Invoice Number", voucher == null ? "" : text(voucher, "VOUCHERNUMBER"));
                row.put("Party Name", voucher == null ? "" : text(voucher, "PARTYLEDGERNAME"));
                row.put("Tally GUID", voucher == null ? "" : firstNonBlank(
                    voucher.getAttribute("GUID"), textIgnoreCase(voucher, "GUID"), textIgnoreCase(voucher, "MASTERID")));
                row.put("Item Name", text(entry, "STOCKITEMNAME"));
                row.put("Unit", unitFromQuantity(rawBilledQuantity, rawActualQuantity));
                row.put("Qty", quantity);
                row.put("Billed Quantity", purchaseReturn ? absoluteNumber(numberPrefix(rawBilledQuantity)) : numberPrefix(rawBilledQuantity));
                row.put("Actual Quantity", purchaseReturn ? absoluteNumber(numberPrefix(rawActualQuantity)) : numberPrefix(rawActualQuantity));
                row.put("Rate", rateResolution.rate());
                row.put("Amount", numberPrefix(rawAmount));
                row.put("Raw Rate", rawRate);
                row.put("Raw Amount", rawAmount);
                row.put("Raw Quantity", rawQuantity);
                row.put("Parsed Rate", rateResolution.rate());
                row.put("Rate Source", rateResolution.source());
                row.put("Ledger Lines Skipped", voucher == null ? "0" : String.valueOf(voucher.getElementsByTagName("ALLLEDGERENTRIES.LIST").getLength()));
                row.put("Godown", text(entry, "GODOWNNAME"));
                row.put("Batch", text(entry, "BATCHNAME"));
                row.put("Cancelled", voucher == null ? "" : cancelled(voucher));
                row.put("Source Entity", "VOUCHER");
                if (voucher != null && financialsEmitted.add(voucher)) {
                    applyVoucherFinancials(row, voucher);
                }
                rows.add(row);
            }
            var vouchers = document.getElementsByTagName("VOUCHER");
            for (int i = 0; i < vouchers.getLength(); i++) {
                var voucher = (Element) vouchers.item(i);
                if (voucher.getElementsByTagName("ALLINVENTORYENTRIES.LIST").getLength() > 0) continue;
                var row = voucherHeaderRow(voucher);
                applyVoucherFinancials(row, voucher);
                rows.add(row);
            }
            return new ImportPreview(rows);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not parse Tally XML file: " + ex.getMessage(), ex);
        }
    }

    private void addDisplayStockRows(Document document, List<Map<String, String>> rows) {
        var names = document.getElementsByTagName("DSPACCNAME");
        for (int i = 0; i < names.getLength(); i++) {
            var name = (Element) names.item(i);
            var itemName = text(name, "DSPDISPNAME");
            var stockInfo = nextElementSibling(name, "DSPSTKINFO");
            var stockClose = stockInfo == null ? null : firstElement(stockInfo, "DSPSTKCL");
            if (itemName.isBlank() || stockClose == null) {
                continue;
            }
            var closingQty = text(stockClose, "DSPCLQTY");
            var closingRate = text(stockClose, "DSPCLRATE");
            var closingAmount = text(stockClose, "DSPCLAMTA");
            var row = new LinkedHashMap<String, String>();
            row.put("Item Name", itemName);
            row.put("Unit", unitFromQuantity(closingQty, ""));
            row.put("Opening Stock", numberPrefix(closingQty));
            row.put("Purchase Price", numberPrefix(closingRate));
            row.put("Stock Value", numberPrefix(closingAmount));
            row.put("Source Entity", "DSPSTKCL");
            rows.add(row);
        }
    }

    private String text(Element element, String tag) {
        var nodes = element.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    private String textIgnoreCase(Element element, String tag) {
        var nodes = element.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element child && tag.equalsIgnoreCase(child.getTagName())) {
                return child.getTextContent().trim();
            }
        }
        return "";
    }

    private Map<String, String> voucherHeaderRow(Element voucher) {
        var row = new LinkedHashMap<String, String>();
        row.put("Voucher Type", firstNonBlank(voucher.getAttribute("VCHTYPE"), textIgnoreCase(voucher, "VOUCHERTYPENAME")));
        row.put("Voucher Date", textIgnoreCase(voucher, "DATE"));
        row.put("Voucher Number", textIgnoreCase(voucher, "VOUCHERNUMBER"));
        row.put("Invoice Number", textIgnoreCase(voucher, "VOUCHERNUMBER"));
        row.put("Party Name", textIgnoreCase(voucher, "PARTYLEDGERNAME"));
        row.put("Tally GUID", firstNonBlank(voucher.getAttribute("GUID"), textIgnoreCase(voucher, "GUID"), textIgnoreCase(voucher, "MASTERID")));
        row.put("Cancelled", cancelled(voucher));
        row.put("Source Entity", "VOUCHER_HEADER");
        return row;
    }

    private void applyVoucherFinancials(Map<String, String> row, Element voucher) {
        var financials = voucherFinancials(voucher);
        row.put("Tax Amount", format(financials.taxAmount));
        row.put("Discount Amount", format(financials.discountAmount));
        row.put("Freight Amount", format(financials.freightAmount));
        row.put("Round Off Amount", format(financials.roundOffAmount));
        row.put("Other Charges Amount", format(financials.otherChargesAmount));
        row.put("Tax Line Count", String.valueOf(financials.taxLines));
        row.put("Discount Line Count", String.valueOf(financials.discountLines));
        row.put("Freight Line Count", String.valueOf(financials.freightLines));
        row.put("Round Off Line Count", String.valueOf(financials.roundOffLines));
        row.put("Other Charge Line Count", String.valueOf(financials.otherChargeLines));
        row.put("Ledger Adjustment Details", financials.details.toString());
    }

    private VoucherFinancials voucherFinancials(Element voucher) {
        var result = new VoucherFinancials();
        var partyName = normalizeLedger(textIgnoreCase(voucher, "PARTYLEDGERNAME"));
        var voucherType = normalizeLedger(firstNonBlank(voucher.getAttribute("VCHTYPE"), textIgnoreCase(voucher, "VOUCHERTYPENAME")));
        var ledgers = voucher.getElementsByTagName("ALLLEDGERENTRIES.LIST");
        for (int index = 0; index < ledgers.getLength(); index++) {
            var ledger = (Element) ledgers.item(index);
            var ledgerName = firstNonBlank(textIgnoreCase(ledger, "LEDGERNAME"), textIgnoreCase(ledger, "NAME"));
            var normalized = normalizeLedger(ledgerName);
            var amount = decimal(textIgnoreCase(ledger, "AMOUNT"));
            var component = componentType(normalized, partyName, voucherType);
            if (component == null || amount == null) continue;
            result.add(component, ledgerName, amount);
        }
        return result;
    }

    private String componentType(String ledgerName, String partyName, String voucherType) {
        if (ledgerName.isBlank() || ledgerName.equals(partyName)) return null;
        if (ledgerName.contains("CGST") || ledgerName.contains("SGST") || ledgerName.contains("IGST")
            || ledgerName.contains("GST") || ledgerName.contains("TAX") || ledgerName.contains("DUTY")) return "TAX";
        if (ledgerName.contains("DISCOUNT") || ledgerName.contains("DISC ")) return "DISCOUNT";
        if (ledgerName.contains("FREIGHT") || ledgerName.contains("DELIVERY") || ledgerName.contains("LOADING")
            || ledgerName.contains("CARRIAGE") || ledgerName.contains("TRANSPORT") || ledgerName.contains("PACKING")) return "FREIGHT";
        if (ledgerName.contains("ROUND")) return "ROUND_OFF";
        if (ledgerName.equals(voucherType) || ledgerName.contains("SALES") || ledgerName.contains("PURCHASE")
            || ledgerName.contains("RETURN") || ledgerName.contains("CASH") || ledgerName.contains("BANK")) return null;
        return "OTHER_CHARGE";
    }

    private String normalizeLedger(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private String numberPrefix(String value) {
        if (value == null) {
            return "";
        }
        var normalized = value.replaceAll("[^0-9.\\-]", "");
        return normalized.isBlank() ? "" : normalized;
    }

    private PurchaseRateResolution resolvePurchaseRate(String rawRate, String rawAmount, String rawQuantity) {
        var rate = decimal(rawRate);
        var amount = decimal(rawAmount);
        var quantity = decimal(rawQuantity);
        if (rate != null && rate.compareTo(BigDecimal.ZERO) > 0) {
            return new PurchaseRateResolution(format(rate), "RATE_FIELD");
        }
        if (rate != null && rate.compareTo(BigDecimal.ZERO) < 0) {
            return new PurchaseRateResolution(format(rate.abs()), "SIGN_NORMALIZED");
        }
        if (amount != null && amount.compareTo(BigDecimal.ZERO) != 0 && quantity != null && quantity.compareTo(BigDecimal.ZERO) != 0) {
            var derived = amount.abs().divide(quantity.abs(), 2, RoundingMode.HALF_UP);
            return new PurchaseRateResolution(format(derived), "DERIVED_FROM_AMOUNT");
        }
        var invalidRate = rawRate != null && !rawRate.isBlank() && rate == null;
        var invalidAmount = rawAmount != null && !rawAmount.isBlank() && amount == null;
        if (invalidRate || invalidAmount || quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) {
            return new PurchaseRateResolution("", "INVALID");
        }
        return new PurchaseRateResolution("0", "ZERO_COST_ITEM");
    }

    private final class VoucherFinancials {
        private BigDecimal taxAmount = BigDecimal.ZERO;
        private BigDecimal discountAmount = BigDecimal.ZERO;
        private BigDecimal freightAmount = BigDecimal.ZERO;
        private BigDecimal roundOffAmount = BigDecimal.ZERO;
        private BigDecimal otherChargesAmount = BigDecimal.ZERO;
        private int taxLines;
        private int discountLines;
        private int freightLines;
        private int roundOffLines;
        private int otherChargeLines;
        private final StringBuilder details = new StringBuilder();

        private void add(String type, String ledgerName, BigDecimal amount) {
            if (!details.isEmpty()) details.append(" | ");
            details.append(type).append(':').append(ledgerName).append(':').append(format(amount));
            switch (type) {
                case "TAX" -> { taxAmount = taxAmount.add(amount.abs()); taxLines++; }
                case "DISCOUNT" -> { discountAmount = discountAmount.add(amount.abs()); discountLines++; }
                case "FREIGHT" -> { freightAmount = freightAmount.add(amount.abs()); freightLines++; }
                case "ROUND_OFF" -> { roundOffAmount = roundOffAmount.add(amount); roundOffLines++; }
                default -> { otherChargesAmount = otherChargesAmount.add(amount.abs()); otherChargeLines++; }
            }
        }
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            var cleaned = value.replace(",", "").replaceAll("[^0-9.\\-]", "");
            return cleaned.isBlank() || "-".equals(cleaned) ? null : new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String absoluteNumber(String value) {
        var parsed = decimal(value);
        return parsed == null ? value : format(parsed.abs());
    }

    private String format(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private boolean isPurchaseVoucher(String voucherType) {
        var normalized = voucherType == null ? "" : voucherType.toLowerCase(Locale.ROOT);
        return normalized.contains("purchase") && !isPurchaseReturnVoucher(voucherType);
    }

    private boolean isPurchaseReturnVoucher(String voucherType) {
        var normalized = voucherType == null ? "" : voucherType.toLowerCase(Locale.ROOT);
        return normalized.contains("debit note") || normalized.contains("purchase return") || (normalized.contains("purchase") && normalized.contains("return"));
    }

    private String firstNonBlank(String... values) {
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String unitFromQuantity(String billedQty, String actualQty) {
        var source = firstNonBlank(billedQty, actualQty);
        var unit = source.replaceAll("[-0-9.,\\s]", "").trim();
        return unit.isBlank() ? "PCS" : unit;
    }

    private String cancelled(Element voucher) {
        var action = voucher.getAttribute("ACTION");
        var isCancelled = firstNonBlank(text(voucher, "ISCANCELLED"), text(voucher, "ISDELETED"), action);
        return isCancelled == null ? "" : isCancelled;
    }

    private Element parentElement(Element element, String tagName) {
        Node current = element.getParentNode();
        while (current != null) {
            if (current instanceof Element parent && tagName.equals(parent.getTagName())) {
                return parent;
            }
            current = current.getParentNode();
        }
        return null;
    }

    private Element nextElementSibling(Element element, String tagName) {
        Node current = element.getNextSibling();
        while (current != null) {
            if (current instanceof Element sibling) {
                return tagName.equals(sibling.getTagName()) ? sibling : null;
            }
            current = current.getNextSibling();
        }
        return null;
    }

    private Element firstElement(Element element, String tagName) {
        var nodes = element.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private record PurchaseRateResolution(String rate, String source) {
    }
}
