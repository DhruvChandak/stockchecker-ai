import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const page = readFileSync(new URL("../app/imports/smart/page.tsx", import.meta.url), "utf8");
const legacy = readFileSync(new URL("../app/imports/page.tsx", import.meta.url), "utf8");
const cashbookReview = readFileSync(new URL("../components/CashbookReviewPanel.tsx", import.meta.url), "utf8");

test("smart import page exposes the canonical staging workspace", () => {
  assert.match(page, /New Smart Import Session/);
  assert.match(page, /type="file" multiple/);
  assert.match(page, /Detected Files/);
  assert.match(page, /Staging Summary/);
  assert.match(page, /Matching/);
  assert.match(page, /Review Queue/);
  assert.match(page, /Canonical Staging Summary/);
  assert.match(page, /Stock Snapshot Preview/);
  assert.match(page, /Voucher Stock Impact Preview/);
  assert.match(page, /Transaction-safe Dry Run/);
  assert.match(page, /Run Dry Run/);
  assert.match(page, /Financial and Payment Smart Import Commit/);
  assert.match(page, /Commit Reviewed Import/);
});

test("smart import page renders dry-run and safe commit reconciliation", () => {
  assert.match(page, /\/dry-run/);
  assert.match(page, /Final Impact Preview/);
  assert.match(page, /Dry-run Items/);
  assert.match(page, /snapshotAdjustmentsPreviewed/);
  assert.match(page, /stockMovementsSkippedBeforeSnapshotDate/);
  assert.match(page, /COMMIT IMPORT PLAN/);
  assert.match(page, /commit-result/);
  assert.match(page, /Commit Reconciliation/);
  assert.match(page, /salesInvoicesCreated/);
  assert.match(page, /purchaseInvoicesCreated/);
  assert.match(page, /Still deferred/);
});

test("smart import safe commit requires exact confirmation and a clean dry run", () => {
  assert.match(page, /commitConfirmation !== "COMMIT IMPORT PLAN"/);
  assert.match(page, /blockingErrors/);
  assert.match(page, /reviewRequiredCount/);
  assert.match(page, /Foundation data, snapshot adjustments, invoice-only sales\/purchases, financial-only credit\/debit notes/);
  assert.match(page, /Unmatched cashbook rows, stock-ageing insights, and every voucher or return stock movement remain staged/);
});

test("smart import Phase 4B-1 exposes invoice-only reconciliation", () => {
  assert.match(page, /Voucher posting mode: Invoice-only, no stock movement/);
  assert.match(page, /voucherItemsReady/);
  assert.match(page, /duplicateVouchersSkipped/);
  assert.match(page, /stockMovementsSkippedDueToInvoiceOnly/);
  assert.match(page, /voucherStockMovementsCreated/);
  assert.match(page, /Sales and purchase invoices were posted without changing stock/);
});

test("smart import Phase 4B-2A exposes financial notes and ledger reconciliation", () => {
  assert.match(page, /Credit notes ready/);
  assert.match(page, /Debit notes ready/);
  assert.match(page, /creditNotesCreated/);
  assert.match(page, /debitNotesCreated/);
  assert.match(page, /taxLinesCaptured/);
  assert.match(page, /discountLinesCaptured/);
  assert.match(page, /freightLinesCaptured/);
  assert.match(page, /roundOffLinesCaptured/);
  assert.match(page, /Return stock impact deferred/);
  assert.match(page, /Return stock movements are not posted in this phase/);
});

test("smart import Phase 4B-2B exposes outstanding snapshots and cashbook reconciliation", () => {
  assert.match(page, /Debtor and Creditor Outstanding Preview/);
  assert.match(page, /Cashbook Payment Matching Preview/);
  assert.match(page, /Cashbook matching is confidence-based/);
  assert.match(page, /cashbookRowsMatched/);
  assert.match(page, /cashbookRowsUnmatched/);
  assert.match(page, /customerPaymentsCreated/);
  assert.match(page, /supplierPaymentsCreated/);
  assert.match(page, /outstandingSnapshotsCreated/);
  assert.match(page, /duplicatePaymentsSkipped/);
  assert.match(page, /row\.cashbookMatchStatus/);
});

test("smart import review queue supports confirmed manual cashbook resolution", () => {
  assert.match(page, /Unmatched Cashbook Resolution/);
  assert.match(page, /cashbookRowsManuallyResolved/);
  assert.match(page, /cashbookRowsIgnored/);
  assert.match(cashbookReview, /cashbook-review/);
  assert.match(cashbookReview, /MAP_CUSTOMER/);
  assert.match(cashbookReview, /MAP_SUPPLIER/);
  assert.match(cashbookReview, /MAP_SALES_INVOICE/);
  assert.match(cashbookReview, /MAP_PURCHASE_INVOICE/);
  assert.match(cashbookReview, /POST PAYMENT/);
  assert.match(cashbookReview, /Post payment/);
  assert.match(cashbookReview, /Keep unresolved/);
});

test("smart import page supports file override and simple review resolution", () => {
  assert.match(page, /selectedFileType/);
  assert.match(page, /method: "PATCH"/);
  assert.match(page, /review-items\/\$\{itemId\}\/resolve/);
  assert.match(page, /Rebuild workspace/);
  assert.match(page, /No products, parties, invoices, or stock movements are created in this phase/);
});

test("smart import page removes uploaded files and refreshes cached session state", () => {
  assert.match(page, /method: "DELETE"/);
  assert.match(page, /Remove file/);
  assert.match(page, /setQueryData<SessionDetails>/);
  assert.match(page, /files\.filter\(\(file\) => file\.id !== fileId\)/);
});

test("legacy import flow remains available", () => {
  assert.match(page, /href="\/imports"/);
  assert.match(legacy, /href="\/imports\/smart"/);
  assert.match(legacy, /Upload and preview/);
});
