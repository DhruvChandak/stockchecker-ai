package com.stockpilot.ai.service;

import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ImportAdapterTest {
    @Test
    void parsesCsvRowsWithHeaders() {
        var csv = "Item Name,Qty,Rate\nMaggi,10,8.50\n";
        var adapter = new GenericCsvImportAdapter();
        var preview = adapter.parse(new ErpImportAdapter.ImportRequest(UUID.randomUUID(), UUID.randomUUID(), "sample.csv", new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));

        assertThat(preview.rows()).hasSize(1);
        assertThat(preview.rows().getFirst()).containsEntry("Item Name", "Maggi");
    }

    @Test
    void csvImportHandlesDuplicateHeadersAndSkipsBlankRows() {
        var csv = "Item Name,Qty,Qty\nMaggi,10,12\n   ,   ,   \nParle,4,5\n";
        var adapter = new GenericCsvImportAdapter();
        var preview = adapter.parse(new ErpImportAdapter.ImportRequest(UUID.randomUUID(), UUID.randomUUID(), "sample.csv", new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));

        assertThat(preview.rows()).hasSize(2);
        assertThat(preview.rows().getFirst()).containsEntry("Qty", "10").containsEntry("Qty_2", "12");
    }

    @Test
    void excelImportTrimsHeadersAndReadsEvaluatedFormulaValues() throws Exception {
        byte[] workbookBytes;
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Import");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue(" Item Name ");
            header.createCell(1).setCellValue("Qty");
            header.createCell(2).setCellValue("Rate");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("Surf Excel 1kg");
            row.createCell(1).setCellFormula("6*2");
            row.createCell(2).setCellValue(118);
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            workbook.write(output);
            workbookBytes = output.toByteArray();
        }

        var adapter = new GenericExcelImportAdapter();
        var preview = adapter.parse(new ErpImportAdapter.ImportRequest(UUID.randomUUID(), UUID.randomUUID(), "sample.xlsx", new ByteArrayInputStream(workbookBytes)));

        assertThat(preview.rows()).hasSize(1);
        assertThat(preview.rows().getFirst()).containsEntry("Item Name", "Surf Excel 1kg").containsEntry("Qty", "12");
    }

    @Test
    void tallyXmlParsesDisplayStockReportRows() {
        var xml = """
            <ENVELOPE>
              <DSPACCNAME><DSPDISPNAME>Sample Item 70g</DSPDISPNAME></DSPACCNAME>
              <DSPSTKINFO><DSPSTKCL>
                <DSPCLQTY>30 Nos</DSPCLQTY>
                <DSPCLRATE>50.92</DSPCLRATE>
                <DSPCLAMTA>-1527.72</DSPCLAMTA>
              </DSPSTKCL></DSPSTKINFO>
              <DSPACCNAME><DSPDISPNAME>Sample Item 100g</DSPDISPNAME></DSPACCNAME>
              <DSPSTKINFO><DSPSTKCL>
                <DSPCLQTY>12 Pcs</DSPCLQTY>
                <DSPCLRATE>18.50</DSPCLRATE>
                <DSPCLAMTA>-222.00</DSPCLAMTA>
              </DSPSTKCL></DSPSTKINFO>
            </ENVELOPE>
            """;
        var adapter = new TallyXmlImportAdapter();
        var preview = adapter.parse(new ErpImportAdapter.ImportRequest(UUID.randomUUID(), UUID.randomUUID(), "shape-inventory.xml", new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));

        assertThat(preview.rows()).hasSize(2);
        assertThat(preview.rows().getFirst())
            .containsEntry("Source Entity", "DSPSTKCL")
            .containsEntry("Item Name", "Sample Item 70g")
            .containsEntry("Opening Stock", "30")
            .containsEntry("Unit", "Nos")
            .containsEntry("Purchase Price", "50.92");
    }

    @Test
    void xmlImportSanitizesInvalidNumericControlCharacterReferences() {
        var xml = "<ENVELOPE><ROW><NAME>Bad&#4;Name</NAME></ROW></ENVELOPE>";
        var adapter = new GenericXmlImportAdapter();
        var preview = adapter.parse(new ErpImportAdapter.ImportRequest(UUID.randomUUID(), UUID.randomUUID(), "control.xml", new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));

        assertThat(preview.rows()).hasSize(1);
        assertThat(preview.rows().getFirst()).containsEntry("NAME", "Bad Name");
    }

    @Test
    void xmlImportSanitizesMalformedAndRawControlCharacters() {
        var xml = "<ENVELOPE><ROW><NAME>Bad&#4Name</NAME><NOTE>Raw\u0004Byte</NOTE></ROW></ENVELOPE>";
        var adapter = new GenericXmlImportAdapter();
        var preview = adapter.parse(new ErpImportAdapter.ImportRequest(UUID.randomUUID(), UUID.randomUUID(), "control.xml", new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));

        assertThat(preview.rows()).hasSize(1);
        assertThat(preview.rows().getFirst())
            .containsEntry("NAME", "Bad Name")
            .containsEntry("NOTE", "Raw Byte");
    }

    @Test
    void tallySalesXmlSanitizesInvalidControlReferencesBeforeParsingVouchers() {
        var xml = """
            <ENVELOPE>
              <VOUCHER VCHTYPE="Sales" ACTION="Create">
                <DATE>20260617</DATE>
                <VOUCHERNUMBER>S-CTRL-1</VOUCHERNUMBER>
                <PARTYLEDGERNAME>Books&#4; World</PARTYLEDGERNAME>
                <ALLINVENTORYENTRIES.LIST>
                  <STOCKITEMNAME>Notebook&#4; A4</STOCKITEMNAME>
                  <BILLEDQTY>5 PCS</BILLEDQTY>
                  <RATE>20</RATE>
                  <AMOUNT>100</AMOUNT>
                </ALLINVENTORYENTRIES.LIST>
              </VOUCHER>
            </ENVELOPE>
            """;
        var adapter = new TallyXmlImportAdapter();
        var preview = adapter.parse(new ErpImportAdapter.ImportRequest(UUID.randomUUID(), UUID.randomUUID(), "sales.xml", new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));

        assertThat(preview.rows()).hasSize(1);
        assertThat(preview.rows().getFirst())
            .containsEntry("Source Entity", "VOUCHER")
            .containsEntry("Party Name", "Books  World")
            .containsEntry("Item Name", "Notebook  A4")
            .containsEntry("Qty", "5");
    }

    @Test
    void tallySalesXmlParsesUtf16LittleEndianTallyExportWithBom() {
        var xml = """
            <ENVELOPE>
              <HEADER>
                <TALLYREQUEST>Import Data</TALLYREQUEST>
              </HEADER>
              <BODY>
                <IMPORTDATA>
                  <REQUESTDATA>
                    <TALLYMESSAGE>
                      <VOUCHER VCHTYPE="Sales" ACTION="Create">
                        <DATE>20260618</DATE>
                        <VOUCHERNUMBER>S-UTF16-1</VOUCHERNUMBER>
                        <PARTYLEDGERNAME>UTF16 Customer</PARTYLEDGERNAME>
                        <ALLINVENTORYENTRIES.LIST>
                          <STOCKITEMNAME>UTF16&#4; Item</STOCKITEMNAME>
                          <BILLEDQTY>3 PCS</BILLEDQTY>
                          <RATE>11</RATE>
                          <AMOUNT>33</AMOUNT>
                        </ALLINVENTORYENTRIES.LIST>
                      </VOUCHER>
                    </TALLYMESSAGE>
                  </REQUESTDATA>
                </IMPORTDATA>
              </BODY>
            </ENVELOPE>
            """;
        var output = new ByteArrayOutputStream();
        output.write(0xFF);
        output.write(0xFE);
        output.writeBytes(xml.getBytes(StandardCharsets.UTF_16LE));
        var adapter = new TallyXmlImportAdapter();
        var preview = adapter.parse(new ErpImportAdapter.ImportRequest(UUID.randomUUID(), UUID.randomUUID(), "sales.xml", new ByteArrayInputStream(output.toByteArray())));

        assertThat(preview.rows()).hasSize(1);
        assertThat(preview.rows().getFirst())
            .containsEntry("Source Entity", "VOUCHER")
            .containsEntry("Invoice Number", "S-UTF16-1")
            .containsEntry("Party Name", "UTF16 Customer")
            .containsEntry("Item Name", "UTF16  Item")
            .containsEntry("Qty", "3");
    }

    @Test
    void tallyPurchaseRatesAreParsedDerivedNormalizedAndClassifiedAsZeroCost() throws Exception {
        try (var input = getClass().getResourceAsStream("/sample-imports/tally-purchase-rate-cases.xml")) {
            assertThat(input).isNotNull();
            var adapter = new TallyXmlImportAdapter();
            var preview = adapter.parse(new ErpImportAdapter.ImportRequest(UUID.randomUUID(), UUID.randomUUID(), "purchase.xml", input));

            assertThat(preview.rows()).hasSize(4);
            assertThat(preview.rows().get(0))
                .containsEntry("Rate", "30")
                .containsEntry("Raw Rate", "30.00 / Nos")
                .containsEntry("Rate Source", "RATE_FIELD")
                .containsEntry("Ledger Lines Skipped", "2");
            assertThat(preview.rows().get(1))
                .containsEntry("Rate", "50")
                .containsEntry("Raw Amount", "-500.00")
                .containsEntry("Rate Source", "DERIVED_FROM_AMOUNT");
            assertThat(preview.rows().get(2))
                .containsEntry("Rate", "20")
                .containsEntry("Raw Rate", "-20.00/Nos")
                .containsEntry("Rate Source", "SIGN_NORMALIZED");
            assertThat(preview.rows().get(3))
                .containsEntry("Rate", "0")
                .containsEntry("Rate Source", "ZERO_COST_ITEM");
        }
    }

    @Test
    void tallyPurchaseMalformedRateAndAmountRemainInvalid() {
        var xml = """
            <ENVELOPE><VOUCHER VCHTYPE="Purchase">
              <DATE>20260601</DATE><VOUCHERNUMBER>P-BAD-RATE</VOUCHERNUMBER><PARTYLEDGERNAME>Sample Supplier</PARTYLEDGERNAME>
              <ALLINVENTORYENTRIES.LIST>
                <STOCKITEMNAME>Invalid Rate Item</STOCKITEMNAME>
                <BILLEDQTY>10 Nos</BILLEDQTY><RATE>not-a-rate</RATE><AMOUNT>not-an-amount</AMOUNT>
              </ALLINVENTORYENTRIES.LIST>
            </VOUCHER></ENVELOPE>
            """;
        var adapter = new TallyXmlImportAdapter();
        var preview = adapter.parse(new ErpImportAdapter.ImportRequest(UUID.randomUUID(), UUID.randomUUID(), "purchase.xml", new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));

        assertThat(preview.rows()).singleElement().satisfies(row -> assertThat(row)
            .containsEntry("Rate", "")
            .containsEntry("Rate Source", "INVALID")
            .containsEntry("Raw Rate", "not-a-rate"));
    }

    @Test
    void tallyCreditNoteCapturesFinancialLedgerComponentsWithoutProductRows() throws Exception {
        try (var input = getClass().getResourceAsStream("/import-fixtures/smart-session/credit_note.xml")) {
            assertThat(input).isNotNull();
            var preview = new TallyXmlImportAdapter().parse(new ErpImportAdapter.ImportRequest(
                UUID.randomUUID(), UUID.randomUUID(), "credit-note.xml", input));

            assertThat(preview.rows()).hasSize(1);
            assertThat(preview.rows().getFirst())
                .containsEntry("Voucher Type", "Credit Note")
                .containsEntry("Tally GUID", "CREDIT-GUID-001")
                .containsEntry("Tax Amount", "5.4")
                .containsEntry("Discount Amount", "1")
                .containsEntry("Freight Amount", "4.6")
                .containsEntry("Round Off Amount", "0.3")
                .containsEntry("Tax Line Count", "2");
            assertThat(preview.rows()).noneMatch(row -> "CGST 9%".equals(row.get("Item Name")));
        }
    }
}
