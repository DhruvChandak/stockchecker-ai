"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import clsx from "clsx";
import {
  ArrowLeft,
  Ban,
  Boxes,
  Database,
  FileSearch,
  Files,
  FolderUp,
  Link2,
  ListChecks,
  Plus,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
  Trash2
} from "lucide-react";
import Link from "next/link";
import { DragEvent, useEffect, useMemo, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { EmptyState } from "@/components/EmptyState";
import { ErrorState } from "@/components/ErrorState";
import { Field, PrimaryButton, SecondaryButton, TextInput } from "@/components/FormControls";
import { LoadingState } from "@/components/LoadingState";
import { Panel } from "@/components/Panel";
import { CashbookReviewPanel } from "@/components/CashbookReviewPanel";
import { api, Page } from "@/lib/api";

type SessionStatus = "CREATED" | "FILES_UPLOADED" | "CLASSIFIED" | "PLAN_READY" | "STAGED" | "NEEDS_REVIEW" | "COMMITTING" | "COMMITTED" | "FAILED" | "CANCELLED";
type Strategy = "SNAPSHOT_FIRST" | "TRANSACTION_HISTORY" | "HYBRID_RECONCILIATION" | "UNKNOWN";
type WorkspaceTab = "files" | "staging" | "matching" | "review" | "plan" | "dry-run" | "commit";
type StockImpactMode = "CREATE_INVOICES_ONLY" | "CREATE_INVOICES_AND_STOCK_MOVEMENTS" | "APPLY_STOCK_MOVEMENTS_AFTER_SNAPSHOT_DATE";
type NegativeStockPolicy = "BLOCK" | "SKIP_STOCK_MOVEMENT" | "IMPORT_AS_IS";

type SessionSummary = {
  id: string;
  name: string;
  status: SessionStatus;
  recommendedStrategy: Strategy;
  createdAt: string;
};

type SessionFile = {
  id: string;
  originalFileName: string;
  detectedFileType: string;
  selectedFileType: string;
  confidence: number;
  detectionReason: string;
  status: string;
  rowCount: number;
  errorCount: number;
  warningCount: number;
  dateRangeStart: string;
  dateRangeEnd: string;
  companyName: string;
  metadata: { duplicateOfFileId?: string };
};

type ReviewChoice = {
  action: string;
  label: string;
  targetId?: string;
  selectedFileType?: string;
};

type ReviewItem = {
  id: string;
  severity: "INFO" | "WARNING" | "ERROR" | "REVIEW_REQUIRED";
  code: string;
  message: string;
  affectedFileId: string;
  affectedFile?: string;
  affectedRows?: number[];
  suggestedAction: string;
  availableChoices?: ReviewChoice[];
  resolved?: boolean;
};

type PlanStep = { order: number; name: string; purpose: string; status: string };
type ImportPlan = {
  id?: string;
  strategy?: Strategy;
  status?: string;
  details?: {
    explanation?: string;
    dependencyNote?: string;
    steps?: PlanStep[];
    commitEnabled?: boolean;
    filesConsidered?: number;
    duplicateFilesSkipped?: number;
    phase?: string;
    stagingComplete?: boolean;
  };
};

type SessionDetails = SessionSummary & {
  files: SessionFile[];
  issues: ReviewItem[];
  plan: ImportPlan;
};

type StagingSummary = {
  productsStaged: number;
  partiesStaged: number;
  warehousesStaged: number;
  unitsStaged: number;
  stockSnapshotsStaged: number;
  vouchersStaged: number;
  voucherItemsStaged: number;
  creditNotesStaged: number;
  debitNotesStaged: number;
  ledgerAdjustmentLinesStaged: number;
  debtorRowsStaged: number;
  creditorRowsStaged: number;
  cashbookEntriesStaged: number;
  stockAgeingRowsStaged: number;
};

type MatchingSummary = {
  productsToCreate: number;
  productsMatchedExisting: number;
  possibleDuplicateProducts: number;
  partiesToCreate: number;
  partiesMatchedExisting: number;
  warehousesToCreateOrMap: number;
  warehousesMatchedExisting: number;
  vouchersLikelyDuplicates: number;
  cashbookRowsMatched: number;
  cashbookRowsUnmatched: number;
  cashbookRowsReviewRequired: number;
  paymentsToCreate: number;
};

type StagedProduct = { id: string; row: number; name: string; unitCode: string; sku?: string; matchStatus: string; reviewStatus: string };
type StagedParty = { id: string; row: number; name: string; partyType: string; sourceType: string; gstin?: string; outstandingAmount?: number; snapshotDate?: string; matchStatus: string; reviewStatus: string };
type StagedWarehouse = { id: string; name: string; matchStatus: string; reviewStatus: string };
type StagedSnapshot = {
  id: string;
  row: number;
  productName: string;
  unitCode: string;
  warehouseName?: string;
  currentStock: number;
  importedStock: number;
  deltaPreview: number;
  matchStatus: string;
  reviewStatus: string;
  action: string;
};
type StagedVoucher = {
  id: string;
  row: number;
  voucherType: string;
  voucherNumber?: string;
  voucherDate?: string;
  partyName?: string;
  totalAmount?: number;
  matchStatus: string;
  reviewStatus: string;
  stockImpactModeSuggestion: string;
};
type StagedCashbook = {
  id: string;
  row: number;
  date?: string;
  partyName?: string;
  amount?: number;
  direction: string;
  paymentMode: string;
  referenceNumber?: string;
  matchedPartyType: string;
  cashbookMatchStatus: string;
  matchConfidence: number;
  matchReason?: string;
};

type StagingWorkspace = {
  summary: StagingSummary;
  matching: MatchingSummary;
  products: StagedProduct[];
  parties: StagedParty[];
  warehouses: StagedWarehouse[];
  units: unknown[];
  snapshots: StagedSnapshot[];
  vouchers: StagedVoucher[];
  voucherItems: unknown[];
  cashbookEntries: StagedCashbook[];
  stockAgeing: unknown[];
  reviewItems: ReviewItem[];
  previewLimit: number;
};

type DryRunSummary = Record<string, number | string | boolean> & {
  productsToCreate: number;
  productsMatchedExisting: number;
  purchaseInvoicesPreviewed: number;
  salesInvoicesPreviewed: number;
  creditNotesPreviewed: number;
  debitNotesPreviewed: number;
  snapshotAdjustmentsPreviewed: number;
  stockMovementsPreviewed: number;
  stockMovementsSkippedDueToInvoiceOnly: number;
  stockMovementsSkippedBeforeSnapshotDate: number;
  voucherItemsReady: number;
  vouchersBlocked: number;
  duplicateVouchersSkipped: number;
  taxLinesCaptured: number;
  discountLinesCaptured: number;
  freightLinesCaptured: number;
  roundOffLinesCaptured: number;
  warnings: number;
  blockingErrors: number;
  reviewRequiredCount: number;
  debtorRowsProcessed: number;
  creditorRowsProcessed: number;
  outstandingSnapshotsPreviewed: number;
  cashbookRowsReady: number;
  cashbookRowsReviewRequired: number;
  customerPaymentsPreviewed: number;
  supplierPaymentsPreviewed: number;
  paymentsLinkedToSalesInvoices: number;
  paymentsLinkedToPurchaseInvoices: number;
  duplicatePaymentsSkipped: number;
  stockImpactMode: StockImpactMode;
};

type DryRunResponse = {
  available: boolean;
  id?: string;
  status: "NOT_RUN" | "RUNNING" | "COMPLETED" | "FAILED";
  strategy?: Strategy;
  startedAt?: string;
  finishedAt?: string;
  summary?: DryRunSummary;
};

type DryRunItem = {
  id: string;
  itemType: string;
  action: string;
  sourceFileId?: string;
  sourceRowNumber?: number;
  preview: Record<string, unknown>;
  warningCode?: string;
  errorCode?: string;
};

type CommitSummary = Record<string, number | string | boolean> & {
  productsCreated?: number;
  productsMatchedExisting?: number;
  productsUpdated?: number;
  partiesCreated?: number;
  partiesMatchedExisting?: number;
  warehousesCreated?: number;
  warehousesMatchedExisting?: number;
  unitsCreated?: number;
  snapshotRowsProcessed?: number;
  snapshotMovementsCreated?: number;
  snapshotRowsNoChange?: number;
  negativeStockRowsFound?: number;
  negativeStockRowsSkipped?: number;
  futurePhaseVoucherRowsSkipped?: number;
  futurePhaseCashbookRowsSkipped?: number;
  futurePhaseAgeingRowsSkipped?: number;
  failureReason?: string;
  futurePhaseMessage?: string;
  invoiceOnlyMessage?: string;
  financialAdjustmentMessage?: string;
  salesInvoicesCreated?: number;
  salesInvoiceItemsCreated?: number;
  purchaseInvoicesCreated?: number;
  purchaseInvoiceItemsCreated?: number;
  duplicateSalesVouchersSkipped?: number;
  duplicatePurchaseVouchersSkipped?: number;
  voucherStockMovementsCreated?: number;
  stockMovementsSkippedDueToInvoiceOnly?: number;
  vouchersDeferredForFuturePhase?: number;
  creditNotesCreated?: number;
  creditNoteItemsCreated?: number;
  debitNotesCreated?: number;
  debitNoteItemsCreated?: number;
  duplicateCreditNotesSkipped?: number;
  duplicateDebitNotesSkipped?: number;
  taxLinesCaptured?: number;
  discountLinesCaptured?: number;
  freightLinesCaptured?: number;
  roundOffLinesCaptured?: number;
  otherChargeLinesCaptured?: number;
  stockMovementsCreatedFromReturns?: number;
  returnStockMovementsDeferred?: number;
  debtorRowsProcessed?: number;
  creditorRowsProcessed?: number;
  outstandingSnapshotsCreated?: number;
  customerPaymentsCreated?: number;
  supplierPaymentsCreated?: number;
  cashbookRowsMatched?: number;
  cashbookRowsUnmatched?: number;
  cashbookRowsReviewRequired?: number;
  paymentsLinkedToSalesInvoices?: number;
  paymentsLinkedToPurchaseInvoices?: number;
  duplicatePaymentsSkipped?: number;
  cashbookRowsManuallyResolved?: number;
  cashbookRowsIgnored?: number;
  manualCustomerPaymentsCreated?: number;
  manualSupplierPaymentsCreated?: number;
  manualPaymentsLinkedToSalesInvoices?: number;
  manualPaymentsLinkedToPurchaseInvoices?: number;
};

type CommitResponse = {
  available: boolean;
  id?: string;
  dryRunId?: string;
  status: "NOT_COMMITTED" | "COMMITTING" | "COMMITTED" | "FAILED" | "ROLLED_BACK";
  committedAt?: string;
  committedBy?: string;
  idempotentRetry?: boolean;
  summary?: CommitSummary;
};

const FILE_TYPES = [
  "INVENTORY_MASTER",
  "ACCOUNTING_MASTER",
  "STOCK_SNAPSHOT",
  "SALES_VOUCHERS",
  "PURCHASE_VOUCHERS",
  "CREDIT_NOTES",
  "DEBIT_NOTES",
  "STOCK_JOURNAL",
  "CASH_BOOK",
  "DEBTOR_CREDITOR_ANALYSIS",
  "STOCK_AGEING",
  "UNKNOWN"
];

const TABS: Array<{ id: WorkspaceTab; label: string; icon: typeof Files }> = [
  { id: "files", label: "Files", icon: Files },
  { id: "staging", label: "Staging Summary", icon: Database },
  { id: "matching", label: "Matching", icon: Link2 },
  { id: "review", label: "Review Queue", icon: ShieldAlert },
  { id: "plan", label: "Plan", icon: ListChecks },
  { id: "dry-run", label: "Dry Run", icon: FileSearch },
  { id: "commit", label: "Final Commit", icon: Boxes }
];

export default function SmartImportsPage() {
  const queryClient = useQueryClient();
  const [sessionName, setSessionName] = useState("");
  const [sessionId, setSessionId] = useState("");
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [dragActive, setDragActive] = useState(false);
  const [activeTab, setActiveTab] = useState<WorkspaceTab>("files");
  const [dryRunStrategy, setDryRunStrategy] = useState<Strategy>("UNKNOWN");
  const [stockImpactMode, setStockImpactMode] = useState<StockImpactMode>("CREATE_INVOICES_ONLY");
  const [negativeStockPolicy, setNegativeStockPolicy] = useState<NegativeStockPolicy>("BLOCK");
  const [dryRunItemType, setDryRunItemType] = useState("");
  const [dryRunIssue, setDryRunIssue] = useState("");
  const [commitConfirmation, setCommitConfirmation] = useState("");

  const sessions = useQuery({
    queryKey: ["smart-import-sessions"],
    queryFn: () => api<Page<SessionSummary>>("/api/import-sessions?size=20")
  });
  const current = useQuery({
    queryKey: ["smart-import-session", sessionId],
    enabled: !!sessionId,
    queryFn: () => api<SessionDetails>(`/api/import-sessions/${sessionId}`)
  });
  const staging = useQuery({
    queryKey: ["smart-import-staging", sessionId],
    enabled: !!sessionId,
    queryFn: () => api<StagingWorkspace>(`/api/import-sessions/${sessionId}/staging`),
    retry: false
  });
  const dryRun = useQuery({
    queryKey: ["smart-import-dry-run", sessionId],
    enabled: !!sessionId,
    queryFn: () => api<DryRunResponse>(`/api/import-sessions/${sessionId}/dry-run`),
    retry: false
  });
  const dryRunItems = useQuery({
    queryKey: ["smart-import-dry-run-items", sessionId, dryRunItemType, dryRunIssue],
    enabled: !!sessionId && !!dryRun.data?.available,
    queryFn: () => {
      const params = new URLSearchParams({ size: "100" });
      if (dryRunItemType) params.set("itemType", dryRunItemType);
      if (dryRunIssue) params.set("issue", dryRunIssue);
      return api<Page<DryRunItem>>(`/api/import-sessions/${sessionId}/dry-run/items?${params}`);
    },
    retry: false
  });
  const commitResult = useQuery({
    queryKey: ["smart-import-commit", sessionId],
    enabled: !!sessionId,
    queryFn: () => api<CommitResponse>(`/api/import-sessions/${sessionId}/commit-result`),
    retry: false
  });

  useEffect(() => {
    if (!sessionId && sessions.data?.content[0]) setSessionId(sessions.data.content[0].id);
  }, [sessionId, sessions.data]);

  const refresh = async (id: string) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["smart-import-sessions"] }),
      queryClient.invalidateQueries({ queryKey: ["smart-import-session", id] }),
      queryClient.invalidateQueries({ queryKey: ["smart-import-staging", id] }),
      queryClient.invalidateQueries({ queryKey: ["smart-import-dry-run", id] }),
      queryClient.invalidateQueries({ queryKey: ["smart-import-dry-run-items", id] }),
      queryClient.invalidateQueries({ queryKey: ["smart-import-commit", id] })
    ]);
  };

  const createSession = useMutation({
    mutationFn: () => api<SessionSummary>("/api/import-sessions", {
      method: "POST",
      body: JSON.stringify({ name: sessionName.trim() || undefined })
    }),
    onSuccess: async (created) => {
      setSessionId(created.id);
      setSessionName("");
      setSelectedFiles([]);
      setActiveTab("files");
      setCommitConfirmation("");
      await refresh(created.id);
    }
  });

  const upload = useMutation({
    mutationFn: async () => {
      if (!sessionId || selectedFiles.length === 0) throw new Error("Select a session and at least one file");
      const body = new FormData();
      selectedFiles.forEach((file) => body.append("files", file));
      return api<SessionDetails>(`/api/import-sessions/${sessionId}/files`, { method: "POST", body });
    },
    onSuccess: async () => {
      setSelectedFiles([]);
      await refresh(sessionId);
    }
  });

  const analyze = useMutation({
    mutationFn: async () => {
      if (!sessionId) throw new Error("Select a smart import session");
      await api(`/api/import-sessions/${sessionId}/classify`, { method: "POST" });
      await api(`/api/import-sessions/${sessionId}/plan`, { method: "POST" });
      return api<StagingWorkspace>(`/api/import-sessions/${sessionId}/stage`, { method: "POST" });
    },
    onSuccess: async () => {
      setActiveTab("staging");
      await refresh(sessionId);
    }
  });

  const rebuild = useMutation({
    mutationFn: () => api<StagingWorkspace>(`/api/import-sessions/${sessionId}/rebuild`, { method: "POST" }),
    onSuccess: async () => refresh(sessionId)
  });

  const overrideType = useMutation({
    mutationFn: ({ fileId, selectedFileType }: { fileId: string; selectedFileType: string }) =>
      api<StagingWorkspace>(`/api/import-sessions/${sessionId}/files/${fileId}/type`, {
        method: "PATCH",
        body: JSON.stringify({ selectedFileType })
      }),
    onSuccess: async () => refresh(sessionId)
  });

  const removeFile = useMutation({
    mutationFn: (fileId: string) => {
      if (!sessionId) throw new Error("Select a smart import session");
      return api<StagingWorkspace>(`/api/import-sessions/${sessionId}/files/${fileId}`, { method: "DELETE" });
    },
    onSuccess: async (workspaceAfterDelete, fileId) => {
      queryClient.setQueryData<SessionDetails>(["smart-import-session", sessionId], (existing) => {
        if (!existing) return existing;
        const files = existing.files.filter((file) => file.id !== fileId);
        const issues = workspaceAfterDelete.reviewItems ?? [];
        const needsReview = issues.some((item) => item.severity === "ERROR" || item.severity === "REVIEW_REQUIRED");
        return {
          ...existing,
          files,
          issues,
          plan: {},
          status: files.length ? (needsReview ? "NEEDS_REVIEW" : "CLASSIFIED") : "CREATED",
          recommendedStrategy: "UNKNOWN"
        };
      });
      queryClient.setQueryData<StagingWorkspace>(["smart-import-staging", sessionId], workspaceAfterDelete);
      await refresh(sessionId);
    }
  });

  const resolveReview = useMutation({
    mutationFn: ({ itemId, choice }: { itemId: string; choice: ReviewChoice }) =>
      api<StagingWorkspace>(`/api/import-sessions/${sessionId}/review-items/${itemId}/resolve`, {
        method: "POST",
        body: JSON.stringify({
          action: choice.action,
          targetId: choice.targetId,
          selectedFileType: choice.selectedFileType
        })
      }),
    onSuccess: async () => refresh(sessionId)
  });

  const cancel = useMutation({
    mutationFn: () => api(`/api/import-sessions/${sessionId}/cancel`, { method: "POST" }),
    onSuccess: async () => refresh(sessionId)
  });

  const runDryRun = useMutation({
    mutationFn: () => {
      if (!sessionId) throw new Error("Select and stage a smart import session");
      return api<DryRunResponse>(`/api/import-sessions/${sessionId}/dry-run`, {
        method: "POST",
        body: JSON.stringify({
          strategy: dryRunStrategy === "UNKNOWN" ? undefined : dryRunStrategy,
          stockImpactMode,
          negativeStockPolicy
        })
      });
    },
    onSuccess: async () => {
      setActiveTab("dry-run");
      await refresh(sessionId);
    }
  });

  const commitFoundation = useMutation({
    mutationFn: () => {
      if (!sessionId || !dryRun.data?.id) throw new Error("Run a completed dry run before committing");
      return api<CommitResponse>(`/api/import-sessions/${sessionId}/commit`, {
        method: "POST",
        body: JSON.stringify({ dryRunId: dryRun.data.id, confirmation: commitConfirmation })
      });
    },
    onSuccess: async () => {
      setActiveTab("commit");
      await Promise.all([
        refresh(sessionId),
        queryClient.invalidateQueries({ queryKey: ["products"] }),
        queryClient.invalidateQueries({ queryKey: ["stock"] }),
        queryClient.invalidateQueries({ queryKey: ["dashboard"] }),
        queryClient.invalidateQueries({ queryKey: ["sales"] }),
        queryClient.invalidateQueries({ queryKey: ["purchases"] })
      ]);
    }
  });

  const details = current.data;
  const workspace = staging.data;
  const plan = details?.plan;
  const strategy = plan?.strategy ?? details?.recommendedStrategy ?? "UNKNOWN";
  const planSteps = plan?.details?.steps ?? [];
  const dryRunSummary = dryRun.data?.summary;
  const safeDryRun = dryRun.data?.available && dryRun.data.status === "COMPLETED"
    && Number(dryRunSummary?.blockingErrors ?? 0) === 0
    && Number(dryRunSummary?.reviewRequiredCount ?? 0) === 0
    && (!(workspace?.vouchers.some((voucher) => /SALE|PURCHASE/i.test(voucher.voucherType)))
      || dryRunSummary?.stockImpactMode === "CREATE_INVOICES_ONLY");
  const reviewItems = workspace?.reviewItems ?? details?.issues ?? [];
  const openReviewCount = reviewItems.filter((item) => !item.resolved).length;
  const reviewGroups = useMemo(() => {
    const groups = new Map<string, ReviewItem[]>();
    for (const item of reviewItems) groups.set(item.code, [...(groups.get(item.code) ?? []), item]);
    return groups;
  }, [reviewItems]);

  useEffect(() => {
    setDryRunStrategy(strategy);
    setStockImpactMode(defaultStockImpactMode());
  }, [sessionId, strategy]);

  const acceptFiles = (incoming: FileList | File[]) => {
    const accepted = Array.from(incoming).filter((file) => /\.(xml|csv|xlsx|xls|json)$/i.test(file.name));
    setSelectedFiles((existing) => {
      const byKey = new Map(existing.map((file) => [`${file.name}:${file.size}:${file.lastModified}`, file]));
      accepted.forEach((file) => byKey.set(`${file.name}:${file.size}:${file.lastModified}`, file));
      return Array.from(byKey.values());
    });
  };

  const onDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setDragActive(false);
    acceptFiles(event.dataTransfer.files);
  };

  const mutationError = createSession.error || upload.error || analyze.error || rebuild.error || overrideType.error || removeFile.error || resolveReview.error || cancel.error || runDryRun.error || commitFoundation.error;

  return (
    <AppShell
      title="Smart Import Workspace"
      actions={
        <Link className="focus-ring inline-flex h-10 items-center gap-2 rounded border border-ink/15 bg-white px-3 text-sm font-semibold text-ink/75" href="/imports">
          <ArrowLeft size={16} /> Single-file import
        </Link>
      }
    >
      <div className="grid gap-4 xl:grid-cols-[300px_minmax(0,1fr)]">
        <aside className="space-y-4">
          <Panel title="Import Sessions">
            <div className="space-y-3">
              <Field label="Session name">
                <TextInput value={sessionName} onChange={(event) => setSessionName(event.target.value)} placeholder="June Tally exports" maxLength={160} />
              </Field>
              <PrimaryButton className="w-full gap-2" onClick={() => createSession.mutate()} disabled={createSession.isPending}>
                <Plus size={16} /> {createSession.isPending ? "Creating..." : "New Smart Import Session"}
              </PrimaryButton>
            </div>
            <div className="mt-4 space-y-2 border-t border-ink/10 pt-4">
              {sessions.isLoading ? <p className="text-sm text-ink/55">Loading sessions...</p> : null}
              {sessions.data?.content.map((session) => (
                <button
                  key={session.id}
                  className={clsx("w-full rounded border p-3 text-left", session.id === sessionId ? "border-moss bg-mint/60" : "border-ink/10 hover:bg-ink/[0.03]")}
                  onClick={() => { setSessionId(session.id); setActiveTab("files"); }}
                >
                  <span className="block truncate text-sm font-semibold">{session.name}</span>
                  <span className="mt-1 flex items-center justify-between gap-2 text-xs text-ink/55">
                    <span>{readable(session.status)}</span><span>{readable(session.recommendedStrategy)}</span>
                  </span>
                </button>
              ))}
              {!sessions.isLoading && !sessions.data?.content.length ? <EmptyState label="No smart import sessions yet." /> : null}
            </div>
          </Panel>

          {details && details.status !== "CANCELLED" ? (
            <SecondaryButton className="w-full gap-2 text-coral" onClick={() => cancel.mutate()} disabled={cancel.isPending}>
              <Ban size={16} /> Cancel session
            </SecondaryButton>
          ) : null}
        </aside>

        <main className="min-w-0 space-y-4">
          {!sessionId ? <EmptyState label="Create a smart import session to begin." /> : null}
          {current.isLoading ? <LoadingState label="Loading import session" /> : null}
          {current.error ? <ErrorState error={current.error} /> : null}
          {mutationError ? <ErrorState error={mutationError} /> : null}

          {details ? (
            <>
              <div className="flex flex-col gap-3 border-y border-ink/10 bg-white px-3 py-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <div className="truncate font-semibold">{details.name}</div>
                  <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs text-ink/55">
                    <span>Status: <strong className="text-ink/75">{readable(details.status)}</strong></span>
                    <span>Strategy: <strong className="text-ink/75">{readable(strategy)}</strong></span>
                    <span>Review items: <strong className="text-ink/75">{openReviewCount}</strong></span>
                  </div>
                </div>
                <SecondaryButton className="shrink-0 gap-2" onClick={() => rebuild.mutate()} disabled={rebuild.isPending || details.files.length === 0 || details.status === "CANCELLED"}>
                  <RefreshCw size={16} /> {rebuild.isPending ? "Rebuilding..." : "Rebuild workspace"}
                </SecondaryButton>
              </div>

              <nav className="overflow-x-auto border-b border-ink/15" aria-label="Smart import workspace">
                <div className="flex min-w-max gap-1">
                  {TABS.map((tab) => {
                    const Icon = tab.icon;
                    const count = tab.id === "review" && openReviewCount > 0 ? openReviewCount : null;
                    return (
                      <button
                        key={tab.id}
                        className={clsx(
                          "focus-ring inline-flex h-11 items-center gap-2 border-b-2 px-3 text-sm font-semibold",
                          activeTab === tab.id ? "border-moss text-moss" : "border-transparent text-ink/55 hover:text-ink"
                        )}
                        onClick={() => setActiveTab(tab.id)}
                        aria-current={activeTab === tab.id ? "page" : undefined}
                      >
                        <Icon size={16} /> {tab.label}
                        {count ? <span className="rounded bg-coral px-1.5 py-0.5 text-[11px] text-white">{count}</span> : null}
                      </button>
                    );
                  })}
                </div>
              </nav>

              {activeTab === "files" ? (
                <div className="space-y-4">
                  <Panel title="Upload Files" action={<FolderUp size={18} className="text-moss" />}>
                    <div
                      className={clsx("rounded border border-dashed p-5 text-center transition", dragActive ? "border-moss bg-mint/60" : "border-ink/20 bg-ink/[0.02]")}
                      onDragEnter={(event) => { event.preventDefault(); setDragActive(true); }}
                      onDragOver={(event) => event.preventDefault()}
                      onDragLeave={() => setDragActive(false)}
                      onDrop={onDrop}
                    >
                      <FolderUp className="mx-auto text-moss" size={28} />
                      <p className="mt-2 text-sm font-semibold">Drop Tally, Excel, CSV, XML, or JSON files here</p>
                      <p className="mt-1 text-xs text-ink/55">Files may be uploaded in any order. StockPilot builds the canonical staging sequence.</p>
                      <label className="focus-ring mt-4 inline-flex h-10 cursor-pointer items-center rounded border border-ink/15 bg-white px-4 text-sm font-semibold text-ink/75">
                        Choose files
                        <input className="sr-only" type="file" multiple accept=".xml,.csv,.xlsx,.xls,.json" onChange={(event) => event.target.files && acceptFiles(event.target.files)} />
                      </label>
                    </div>
                    {selectedFiles.length ? (
                      <div className="mt-4">
                        <div className="mb-2 text-sm font-semibold">Ready to upload ({selectedFiles.length})</div>
                        <div className="divide-y divide-ink/10 rounded border border-ink/10">
                          {selectedFiles.map((file) => (
                            <div key={`${file.name}:${file.size}`} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                              <span className="min-w-0 truncate">{file.name}</span><span className="shrink-0 text-xs text-ink/45">{fileSize(file.size)}</span>
                            </div>
                          ))}
                        </div>
                        <div className="mt-3 flex flex-wrap gap-2">
                          <PrimaryButton className="gap-2" onClick={() => upload.mutate()} disabled={upload.isPending}>
                            <FolderUp size={16} /> {upload.isPending ? "Uploading..." : "Upload selected files"}
                          </PrimaryButton>
                          <SecondaryButton onClick={() => setSelectedFiles([])}>Clear selection</SecondaryButton>
                        </div>
                      </div>
                    ) : null}
                  </Panel>

                  <Panel
                    title="Detected Files"
                    action={
                      <PrimaryButton className="gap-2" onClick={() => analyze.mutate()} disabled={analyze.isPending || details.files.length === 0 || details.status === "CANCELLED"}>
                        <FileSearch size={16} /> {analyze.isPending ? "Building workspace..." : "Classify, stage, and match"}
                      </PrimaryButton>
                    }
                  >
                    {details.files.length ? (
                      <div className="overflow-x-auto">
                        <table className="w-full min-w-[1080px] text-left text-sm">
                          <thead className="border-b border-ink/10 text-xs uppercase text-ink/45">
                            <tr><th className="px-2 py-3">File</th><th className="px-2 py-3">Detected</th><th className="px-2 py-3">Selected type</th><th className="px-2 py-3">Confidence</th><th className="px-2 py-3">Company / dates</th><th className="px-2 py-3">Rows</th><th className="px-2 py-3">Reason</th><th className="w-12 px-2 py-3"><span className="sr-only">Actions</span></th></tr>
                          </thead>
                          <tbody className="divide-y divide-ink/10">
                            {details.files.map((file) => (
                              <tr key={file.id}>
                                <td className="px-2 py-3">
                                  <div className="max-w-[180px] truncate font-medium">{file.originalFileName}</div>
                                  {file.status === "DUPLICATE" ? <StatusPill value="DUPLICATE" /> : null}
                                </td>
                                <td className="px-2 py-3"><StatusPill value={file.detectedFileType} /></td>
                                <td className="px-2 py-3">
                                  <select
                                    className="focus-ring h-9 min-w-[190px] rounded border border-ink/15 bg-white px-2 text-sm"
                                    value={file.selectedFileType || file.detectedFileType || "UNKNOWN"}
                                    onChange={(event) => overrideType.mutate({ fileId: file.id, selectedFileType: event.target.value })}
                                    disabled={overrideType.isPending || file.status === "DUPLICATE"}
                                    aria-label={`Selected type for ${file.originalFileName}`}
                                  >
                                    {FILE_TYPES.map((type) => <option key={type} value={type}>{readable(type)}</option>)}
                                  </select>
                                </td>
                                <td className="px-2 py-3">{Math.round(Number(file.confidence || 0) * 100)}%</td>
                                <td className="px-2 py-3 text-xs text-ink/65"><div>{file.companyName || "Not detected"}</div><div>{dateRange(file)}</div></td>
                                <td className="px-2 py-3">{file.rowCount}</td>
                                <td className="max-w-[270px] px-2 py-3 text-xs text-ink/65">{file.detectionReason || "Awaiting classification"}</td>
                                <td className="px-2 py-3">
                                  <button className="focus-ring inline-flex h-9 w-9 items-center justify-center rounded text-coral hover:bg-coral/10" onClick={() => removeFile.mutate(file.id)} disabled={removeFile.isPending} aria-label={`Remove ${file.originalFileName}`} title="Remove file">
                                    <Trash2 size={16} />
                                  </button>
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    ) : <EmptyState label="No files uploaded to this session." />}
                  </Panel>
                </div>
              ) : null}

              {activeTab === "staging" ? (
                <div className="space-y-4">
                  <Panel title="Canonical Staging Summary" action={<Database size={18} className="text-moss" />}>
                    <MetricGrid entries={stagingMetrics(workspace?.summary)} />
                    <p className="mt-4 border-t border-ink/10 pt-3 text-xs text-ink/55">Preview is limited to {workspace?.previewLimit ?? 100} rows per record type. No products, parties, invoices, or stock movements are created in this phase.</p>
                  </Panel>

                  <Panel title="Stock Snapshot Preview">
                    {workspace?.snapshots.length ? (
                      <div className="overflow-x-auto">
                        <table className="w-full min-w-[980px] text-left text-sm">
                          <thead className="border-b border-ink/10 text-xs uppercase text-ink/45"><tr><th className="px-2 py-3">Product</th><th className="px-2 py-3">Unit</th><th className="px-2 py-3">Warehouse</th><th className="px-2 py-3 text-right">Current</th><th className="px-2 py-3 text-right">Imported</th><th className="px-2 py-3 text-right">Delta</th><th className="px-2 py-3">Match</th><th className="px-2 py-3">Action</th></tr></thead>
                          <tbody className="divide-y divide-ink/10">
                            {workspace.snapshots.map((row) => (
                              <tr key={row.id}><td className="px-2 py-3 font-medium">{row.productName || "Missing product"}</td><td className="px-2 py-3">{row.unitCode || "-"}</td><td className="px-2 py-3">{row.warehouseName || "Default"}</td><td className="px-2 py-3 text-right">{number(row.currentStock)}</td><td className="px-2 py-3 text-right">{number(row.importedStock)}</td><td className={clsx("px-2 py-3 text-right font-semibold", Number(row.deltaPreview) < 0 ? "text-coral" : Number(row.deltaPreview) > 0 ? "text-moss" : "text-ink/55")}>{signedNumber(row.deltaPreview)}</td><td className="px-2 py-3"><StatusPill value={row.matchStatus} /></td><td className="px-2 py-3"><StatusPill value={row.action} /></td></tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    ) : <EmptyState label="No stock snapshot rows staged." />}
                  </Panel>

                  <Panel title="Voucher Stock Impact Preview">
                    {workspace?.vouchers.length ? (
                      <div className="overflow-x-auto">
                        <table className="w-full min-w-[900px] text-left text-sm">
                          <thead className="border-b border-ink/10 text-xs uppercase text-ink/45"><tr><th className="px-2 py-3">Voucher</th><th className="px-2 py-3">Date</th><th className="px-2 py-3">Party</th><th className="px-2 py-3 text-right">Amount</th><th className="px-2 py-3">Match</th><th className="px-2 py-3">Suggested stock impact</th></tr></thead>
                          <tbody className="divide-y divide-ink/10">
                            {workspace.vouchers.map((row) => (
                              <tr key={row.id}><td className="px-2 py-3"><div className="font-medium">{row.voucherNumber || "Missing number"}</div><div className="text-xs text-ink/50">{readable(row.voucherType || "UNKNOWN")}</div></td><td className="px-2 py-3">{row.voucherDate || "-"}</td><td className="px-2 py-3">{row.partyName || "Missing party"}</td><td className="px-2 py-3 text-right">{money(row.totalAmount)}</td><td className="px-2 py-3"><StatusPill value={row.matchStatus} /></td><td className="px-2 py-3"><StatusPill value={row.stockImpactModeSuggestion} /></td></tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    ) : <EmptyState label="No voucher rows staged." />}
                  </Panel>

                  <Panel title="Debtor and Creditor Outstanding Preview">
                    {workspace?.parties.some((row) => row.sourceType === "DEBTOR_CREDITOR_ANALYSIS") ? (
                      <div className="overflow-x-auto">
                        <table className="w-full min-w-[760px] text-left text-sm">
                          <thead className="border-b border-ink/10 text-xs uppercase text-ink/45"><tr><th className="px-2 py-3">Party</th><th className="px-2 py-3">Type</th><th className="px-2 py-3">Snapshot date</th><th className="px-2 py-3 text-right">Outstanding</th><th className="px-2 py-3">Match</th></tr></thead>
                          <tbody className="divide-y divide-ink/10">
                            {workspace.parties.filter((row) => row.sourceType === "DEBTOR_CREDITOR_ANALYSIS").map((row) => (
                              <tr key={row.id}><td className="px-2 py-3 font-medium">{row.name}</td><td className="px-2 py-3"><StatusPill value={row.partyType} /></td><td className="px-2 py-3">{row.snapshotDate || "Commit date"}</td><td className="px-2 py-3 text-right font-semibold">{money(row.outstandingAmount)}</td><td className="px-2 py-3"><StatusPill value={row.matchStatus} /></td></tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    ) : <EmptyState label="No debtor or creditor outstanding rows staged." />}
                  </Panel>

                  <Panel title="Cashbook Payment Matching Preview">
                    <p className="mb-4 text-sm leading-6 text-ink/60">Cashbook matching is confidence-based. Low-confidence entries are kept for review instead of being automatically matched.</p>
                    {workspace?.cashbookEntries.length ? (
                      <div className="overflow-x-auto">
                        <table className="w-full min-w-[1020px] text-left text-sm">
                          <thead className="border-b border-ink/10 text-xs uppercase text-ink/45"><tr><th className="px-2 py-3">Date</th><th className="px-2 py-3">Party</th><th className="px-2 py-3">Direction</th><th className="px-2 py-3 text-right">Amount</th><th className="px-2 py-3">Reference</th><th className="px-2 py-3">Match result</th><th className="px-2 py-3">Reason</th></tr></thead>
                          <tbody className="divide-y divide-ink/10">
                            {workspace.cashbookEntries.map((row) => (
                              <tr key={row.id}><td className="px-2 py-3">{row.date || "-"}</td><td className="px-2 py-3 font-medium">{row.partyName || "Unidentified"}</td><td className="px-2 py-3"><StatusPill value={row.direction} /></td><td className="px-2 py-3 text-right">{money(row.amount)}</td><td className="px-2 py-3">{row.referenceNumber || "-"}</td><td className="px-2 py-3"><StatusPill value={row.cashbookMatchStatus} /></td><td className="max-w-[300px] px-2 py-3 text-xs text-ink/60">{row.matchReason || "Awaiting matching"}</td></tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    ) : <EmptyState label="No cashbook entries staged." />}
                  </Panel>
                </div>
              ) : null}

              {activeTab === "matching" ? (
                <div className="space-y-4">
                  <Panel title="Matching Summary" action={<Link2 size={18} className="text-moss" />}>
                    <MetricGrid entries={matchingMetrics(workspace?.matching)} />
                  </Panel>
                  <Panel title="Products">
                    {workspace?.products.length ? <ProductMatchTable rows={workspace.products} /> : <EmptyState label="No staged products." />}
                  </Panel>
                  <div className="grid gap-4 lg:grid-cols-2">
                    <Panel title="Parties">
                      {workspace?.parties.length ? <PartyMatchTable rows={workspace.parties} /> : <EmptyState label="No staged parties." />}
                    </Panel>
                    <Panel title="Warehouses / Godowns">
                      {workspace?.warehouses.length ? <WarehouseMatchTable rows={workspace.warehouses} /> : <EmptyState label="No staged warehouses." />}
                    </Panel>
                  </div>
                </div>
              ) : null}

              {activeTab === "review" ? (
                <div className="space-y-4">
                <Panel title="Review Queue" action={<ShieldAlert size={18} className="text-amber-600" />}>
                  {reviewGroups.size ? (
                    <div className="space-y-5">
                      {Array.from(reviewGroups.entries()).map(([code, items]) => (
                        <section key={code}>
                          <div className="mb-2 flex items-center justify-between gap-3"><h3 className="text-sm font-bold">{readable(code)}</h3><span className="text-xs text-ink/45">{items.length} item{items.length === 1 ? "" : "s"}</span></div>
                          <div className="divide-y divide-ink/10 border-y border-ink/10">
                            {items.map((item) => (
                              <div key={item.id} className="py-4">
                                <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                                  <div>
                                    <div className="flex flex-wrap items-center gap-2"><StatusPill value={item.severity} /><span className="text-xs text-ink/50">{item.affectedFile || "Session"}{item.affectedRows?.length ? `, row ${item.affectedRows.join(", ")}` : ""}</span></div>
                                    <p className="mt-2 text-sm font-medium">{item.message}</p>
                                    {item.suggestedAction ? <p className="mt-1 text-xs leading-5 text-ink/55">{item.suggestedAction}</p> : null}
                                  </div>
                                  {item.resolved ? <StatusPill value="RESOLVED" /> : null}
                                </div>
                                {!item.resolved && item.availableChoices?.length ? (
                                  <div className="mt-3 flex flex-wrap gap-2">
                                    {item.availableChoices.map((choice) => (
                                      <SecondaryButton key={`${choice.action}:${choice.targetId || choice.selectedFileType || choice.label}`} className="min-h-9 h-auto py-2 text-left" onClick={() => resolveReview.mutate({ itemId: item.id, choice })} disabled={resolveReview.isPending}>
                                        {choice.label}
                                      </SecondaryButton>
                                    ))}
                                  </div>
                                ) : null}
                              </div>
                            ))}
                          </div>
                        </section>
                      ))}
                    </div>
                  ) : <EmptyState label="No review items. Exact matches were resolved automatically." />}
                </Panel>
                <Panel title="Unmatched Cashbook Resolution" action={<Link2 size={18} className="text-moss" />}>
                  <CashbookReviewPanel sessionId={sessionId} />
                </Panel>
                </div>
              ) : null}

              {activeTab === "plan" ? (
                <div className="grid gap-4 lg:grid-cols-[minmax(0,340px)_minmax(0,1fr)]">
                  <Panel title="Recommended Strategy" action={<Sparkles size={18} className="text-moss" />}>
                    {plan?.id ? (
                      <div><div className="text-lg font-semibold text-moss">{readable(strategy)}</div><p className="mt-2 text-sm leading-6 text-ink/65">{plan.details?.explanation}</p><div className="mt-4 grid grid-cols-2 gap-3 border-t border-ink/10 pt-4 text-sm"><div><span className="block text-xs text-ink/45">Files considered</span><span className="font-semibold">{plan.details?.filesConsidered ?? 0}</span></div><div><span className="block text-xs text-ink/45">Duplicates skipped</span><span className="font-semibold">{plan.details?.duplicateFilesSkipped ?? 0}</span></div></div></div>
                    ) : <EmptyState label="Classify and stage files to receive a strategy." />}
                  </Panel>
                  <Panel title="Draft Import Plan" action={<ListChecks size={18} className="text-moss" />}>
                    {planSteps.length ? (
                      <div><div className="divide-y divide-ink/10 border-y border-ink/10">{planSteps.map((step) => <div key={step.order} className="grid gap-2 py-3 sm:grid-cols-[38px_190px_1fr_80px] sm:items-center"><div className="flex h-7 w-7 items-center justify-center rounded bg-mint text-xs font-bold text-moss">{step.order}</div><div className="font-semibold">{step.name}</div><div className="text-sm text-ink/60">{step.purpose}</div><div className="text-xs font-medium text-ink/45">{step.status}</div></div>)}</div><p className="mt-3 text-sm text-ink/60">{plan?.details?.dependencyNote}</p></div>
                    ) : <EmptyState label="No draft plan yet." />}
                  </Panel>
                </div>
              ) : null}

              {activeTab === "dry-run" ? (
                <div className="space-y-4">
                  <Panel title="Transaction-safe Dry Run" action={<FileSearch size={18} className="text-moss" />}>
                    <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-end">
                      <div className="grid gap-3 sm:grid-cols-3">
                        <Field label="Import strategy">
                          <select className="focus-ring h-10 w-full rounded border border-ink/15 bg-white px-3 text-sm" value={dryRunStrategy} onChange={(event) => setDryRunStrategy(event.target.value as Strategy)}>
                            <option value="UNKNOWN">Choose strategy</option>
                            <option value="SNAPSHOT_FIRST">Snapshot first</option>
                            <option value="TRANSACTION_HISTORY">Transaction history</option>
                            <option value="HYBRID_RECONCILIATION">Hybrid reconciliation</option>
                          </select>
                        </Field>
                        <Field label="Voucher stock impact">
                          <select className="h-10 w-full rounded border border-ink/15 bg-ink/[0.03] px-3 text-sm text-ink/70" value={stockImpactMode} disabled>
                            <option value="CREATE_INVOICES_ONLY">Invoice-only, no stock movement</option>
                          </select>
                        </Field>
                        <Field label="Negative stock policy">
                          <select className="focus-ring h-10 w-full rounded border border-ink/15 bg-white px-3 text-sm" value={negativeStockPolicy} onChange={(event) => setNegativeStockPolicy(event.target.value as NegativeStockPolicy)}>
                            <option value="BLOCK">Block</option>
                            <option value="SKIP_STOCK_MOVEMENT">Skip stock movement</option>
                            <option value="IMPORT_AS_IS">Import as-is if allowed</option>
                          </select>
                        </Field>
                      </div>
                      <PrimaryButton className="gap-2" onClick={() => runDryRun.mutate()} disabled={runDryRun.isPending || dryRunStrategy === "UNKNOWN" || !plan?.details?.stagingComplete}>
                        <FileSearch size={16} /> {runDryRun.isPending ? "Running..." : "Run Dry Run"}
                      </PrimaryButton>
                    </div>
                    <div className="mt-4 border-l-2 border-moss/40 pl-3 text-sm leading-6 text-ink/65">
                      <p className="font-semibold text-ink">{strategyExplanation(dryRunStrategy)}</p>
                      <p>{stockImpactExplanation(dryRunStrategy, stockImpactMode)}</p>
                      <p className="mt-1 text-xs">Dry run replaces the previous simulation for this session. It never creates final products, parties, invoices, or stock movements.</p>
                    </div>
                  </Panel>

                  {dryRun.data?.available ? (
                    <>
                      <Panel title="Final Impact Preview" action={<StatusPill value={dryRun.data.status} />}>
                        <MetricGrid entries={dryRunMetrics(dryRunSummary)} />
                      </Panel>
                      <Panel title="Dry-run Items">
                        <div className="mb-4 flex flex-wrap gap-3">
                          <select className="focus-ring h-9 rounded border border-ink/15 bg-white px-3 text-sm" value={dryRunItemType} onChange={(event) => setDryRunItemType(event.target.value)} aria-label="Filter dry-run items by type">
                            <option value="">All record types</option>
                            <option value="PRODUCT">Products</option>
                            <option value="CUSTOMER">Customers</option>
                            <option value="SUPPLIER">Suppliers</option>
                            <option value="WAREHOUSE">Warehouses</option>
                            <option value="STOCK_SNAPSHOT">Stock snapshots</option>
                            <option value="STOCK_MOVEMENT">Stock movements</option>
                            <option value="SALES_INVOICE">Sales invoices</option>
                            <option value="PURCHASE_INVOICE">Purchase invoices</option>
                            <option value="CASHBOOK_ENTRY">Cashbook</option>
                            <option value="OUTSTANDING_SNAPSHOT">Outstanding snapshots</option>
                            <option value="STOCK_AGEING">Stock ageing</option>
                          </select>
                          <select className="focus-ring h-9 rounded border border-ink/15 bg-white px-3 text-sm" value={dryRunIssue} onChange={(event) => setDryRunIssue(event.target.value)} aria-label="Filter dry-run items by issue">
                            <option value="">All severities</option>
                            <option value="ERROR">Blocking errors</option>
                            <option value="WARNING">Warnings</option>
                            <option value="REVIEW_REQUIRED">Review required</option>
                          </select>
                        </div>
                        {dryRunItems.isLoading ? <LoadingState label="Loading dry-run items..." /> : null}
                        {dryRunItems.data?.content.length ? <DryRunItemsTable rows={dryRunItems.data.content} /> : !dryRunItems.isLoading ? <EmptyState label="No dry-run items match these filters." /> : null}
                      </Panel>
                    </>
                  ) : (
                    <Panel title="Final Impact Preview"><EmptyState label="Run a dry run after staging to see final impact and reconciliation details." /></Panel>
                  )}
                </div>
              ) : null}

              {activeTab === "commit" ? (
                <div className="space-y-4">
                  <Panel title="Financial and Payment Smart Import Commit" action={<ShieldCheck size={18} className="text-moss" />}>
                    <div className="mb-6 border-l-2 border-moss/50 bg-mint/30 px-4 py-3 text-sm leading-6 text-ink/70">
                      <div className="font-semibold text-moss">Voucher posting mode: Invoice-only, no stock movement</div>
                      <p>Since closing stock may already define the inventory baseline, sales and purchase invoices are imported without changing stock. This prevents historical vouchers from double-counting inventory.</p>
                    </div>
                    <div className="mb-6 border-l-2 border-amber-400 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-900">
                      <div className="font-semibold">Return stock impact deferred</div>
                      <p>Credit and debit notes are posted as financial adjustments only. Return stock movements are not posted in this phase; stock remains controlled by the closing stock snapshot.</p>
                    </div>
                    <div className="grid gap-6 lg:grid-cols-2">
                      <section className="border-l-2 border-moss/40 pl-4">
                        <h2 className="text-sm font-semibold text-moss">Phase 4A through 4B-2B writes</h2>
                        <p className="mt-2 text-sm leading-6 text-ink/65">Foundation data, snapshot adjustments, invoice-only sales/purchases, financial-only credit/debit notes, outstanding snapshots, and high-confidence customer or supplier payments.</p>
                      </section>
                      <section className="border-l-2 border-amber-400 pl-4">
                        <h2 className="text-sm font-semibold text-amber-800">Still deferred</h2>
                        <p className="mt-2 text-sm leading-6 text-ink/65">Unmatched cashbook rows, stock-ageing insights, and every voucher or return stock movement remain staged.</p>
                      </section>
                    </div>
                    <div className="mt-6 border-y border-ink/10 py-5">
                      <MetricGrid entries={voucherReadyMetrics(dryRunSummary)} />
                    </div>
                    <div className="mt-6 border-t border-ink/10 pt-5">
                      {safeDryRun ? (
                        <div className="max-w-xl space-y-3">
                          <p className="text-sm leading-6 text-ink/65">This operation is transactional and idempotent. Type the confirmation exactly; a retry returns the existing result without duplicating products, payments, invoices, or stock.</p>
                          <Field label="Type COMMIT IMPORT PLAN">
                            <TextInput value={commitConfirmation} onChange={(event) => setCommitConfirmation(event.target.value)} autoComplete="off" />
                          </Field>
                          <PrimaryButton className="gap-2" onClick={() => commitFoundation.mutate()} disabled={commitFoundation.isPending || commitConfirmation !== "COMMIT IMPORT PLAN" || commitResult.data?.status === "COMMITTED"}>
                            <Boxes size={16} /> {commitFoundation.isPending ? "Committing safely..." : commitResult.data?.status === "COMMITTED" ? "Import committed" : "Commit Reviewed Import"}
                          </PrimaryButton>
                        </div>
                      ) : (
                        <div className="flex gap-3 text-sm text-coral">
                          <ShieldAlert className="mt-0.5 shrink-0" size={18} />
                          <p>Commit is disabled. Run a completed dry run with zero blocking errors and zero unresolved review items.</p>
                        </div>
                      )}
                    </div>
                  </Panel>

                  {commitResult.data?.available ? (
                    <Panel title="Commit Reconciliation" action={<StatusPill value={commitResult.data.status} />}>
                      {commitResult.data.status === "FAILED" ? (
                        <div className="border-l-2 border-coral pl-4 text-sm leading-6 text-coral">
                          {String(commitResult.data.summary?.failureReason || "The commit failed safely. No partial business data was retained.")}
                        </div>
                      ) : (
                        <>
                          <MetricGrid entries={commitMetrics(commitResult.data.summary)} />
                          <p className="mt-5 border-t border-ink/10 pt-4 text-sm text-ink/60">
                            {String(commitResult.data.summary?.invoiceOnlyMessage || "Sales and purchase invoices were posted without changing stock.")}
                          </p>
                          <p className="mt-2 text-sm text-ink/60">
                            {String(commitResult.data.summary?.financialAdjustmentMessage || "Credit and debit notes were posted as financial adjustments without changing stock.")}
                          </p>
                          <p className="mt-2 text-sm text-ink/60">
                            {String(commitResult.data.summary?.cashbookMessage || "High-confidence cashbook rows were posted; unmatched rows remain in review.")}
                          </p>
                          <p className="mt-2 text-sm text-ink/60">
                            {String(commitResult.data.summary?.outstandingMessage || "Debtor and creditor balances were stored as dated outstanding snapshots.")}
                          </p>
                          <p className="mt-2 text-sm text-ink/60">
                            {String(commitResult.data.summary?.futurePhaseMessage || "Stock ageing and stock-affecting voucher posting remain deferred.")}
                          </p>
                        </>
                      )}
                    </Panel>
                  ) : null}
                </div>
              ) : null}
            </>
          ) : null}
        </main>
      </div>
    </AppShell>
  );
}

function MetricGrid({ entries }: { entries: Array<[string, number]> }) {
  return <div className="grid gap-x-6 gap-y-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">{entries.map(([label, value]) => <div key={label} className="border-l-2 border-moss/35 pl-3"><div className="text-2xl font-semibold tabular-nums">{value}</div><div className="mt-1 text-xs text-ink/55">{label}</div></div>)}</div>;
}

function ProductMatchTable({ rows }: { rows: StagedProduct[] }) {
  return <div className="overflow-x-auto"><table className="w-full min-w-[720px] text-left text-sm"><thead className="border-b border-ink/10 text-xs uppercase text-ink/45"><tr><th className="px-2 py-3">Product</th><th className="px-2 py-3">Unit</th><th className="px-2 py-3">SKU</th><th className="px-2 py-3">Match</th><th className="px-2 py-3">Review</th></tr></thead><tbody className="divide-y divide-ink/10">{rows.map((row) => <tr key={row.id}><td className="px-2 py-3 font-medium">{row.name || "Missing name"}</td><td className="px-2 py-3">{row.unitCode || "-"}</td><td className="px-2 py-3">{row.sku || "-"}</td><td className="px-2 py-3"><StatusPill value={row.matchStatus} /></td><td className="px-2 py-3"><StatusPill value={row.reviewStatus} /></td></tr>)}</tbody></table></div>;
}

function PartyMatchTable({ rows }: { rows: StagedParty[] }) {
  return <div className="overflow-x-auto"><table className="w-full min-w-[520px] text-left text-sm"><thead className="border-b border-ink/10 text-xs uppercase text-ink/45"><tr><th className="px-2 py-3">Party</th><th className="px-2 py-3">Type</th><th className="px-2 py-3">GSTIN</th><th className="px-2 py-3">Match</th></tr></thead><tbody className="divide-y divide-ink/10">{rows.map((row) => <tr key={row.id}><td className="px-2 py-3 font-medium">{row.name || "Missing name"}</td><td className="px-2 py-3">{readable(row.partyType)}</td><td className="px-2 py-3">{row.gstin || "-"}</td><td className="px-2 py-3"><StatusPill value={row.matchStatus} /></td></tr>)}</tbody></table></div>;
}

function WarehouseMatchTable({ rows }: { rows: StagedWarehouse[] }) {
  return <div className="overflow-x-auto"><table className="w-full min-w-[420px] text-left text-sm"><thead className="border-b border-ink/10 text-xs uppercase text-ink/45"><tr><th className="px-2 py-3">Warehouse</th><th className="px-2 py-3">Match</th><th className="px-2 py-3">Review</th></tr></thead><tbody className="divide-y divide-ink/10">{rows.map((row) => <tr key={row.id}><td className="px-2 py-3 font-medium">{row.name || "Missing name"}</td><td className="px-2 py-3"><StatusPill value={row.matchStatus} /></td><td className="px-2 py-3"><StatusPill value={row.reviewStatus} /></td></tr>)}</tbody></table></div>;
}

function DryRunItemsTable({ rows }: { rows: DryRunItem[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[920px] text-left text-sm">
        <thead className="border-b border-ink/10 text-xs uppercase text-ink/45">
          <tr><th className="px-2 py-3">Record</th><th className="px-2 py-3">Action</th><th className="px-2 py-3">Source row</th><th className="px-2 py-3">Impact preview</th><th className="px-2 py-3">Issue</th></tr>
        </thead>
        <tbody className="divide-y divide-ink/10">
          {rows.map((row) => (
            <tr key={row.id}>
              <td className="px-2 py-3"><div className="font-medium">{dryRunItemName(row)}</div><div className="text-xs text-ink/50">{readable(row.itemType)}</div></td>
              <td className="px-2 py-3"><StatusPill value={row.action} /></td>
              <td className="px-2 py-3">{row.sourceRowNumber ?? "-"}</td>
              <td className="max-w-[420px] px-2 py-3 text-xs leading-5 text-ink/65">{dryRunImpact(row.preview)}</td>
              <td className="px-2 py-3">{row.errorCode ? <StatusPill value={row.errorCode} /> : row.warningCode ? <StatusPill value={row.warningCode} /> : "-"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function StatusPill({ value }: { value?: string }) {
  const normalized = value || "UNKNOWN";
  const positive = /MATCH_EXISTING|AUTO_RESOLVED|RESOLVED|NO_CHANGE|READY|STAGED/.test(normalized);
  const danger = /ERROR|BLOCKED|INVALID|NEGATIVE/.test(normalized);
  const warning = /REVIEW|POSSIBLE|AMBIGUOUS|UNKNOWN|CREATE_NEW|CREATE_/.test(normalized);
  return <span className={clsx("inline-flex max-w-[230px] whitespace-normal rounded px-2 py-1 text-[11px] font-semibold leading-4", positive ? "bg-mint text-moss" : danger ? "bg-coral/10 text-coral" : warning ? "bg-amber-100 text-amber-800" : "bg-ink/[0.06] text-ink/65")}>{readable(normalized)}</span>;
}

function stagingMetrics(summary?: StagingSummary): Array<[string, number]> {
  return [
    ["Products staged", summary?.productsStaged ?? 0], ["Parties staged", summary?.partiesStaged ?? 0],
    ["Warehouses staged", summary?.warehousesStaged ?? 0], ["Units staged", summary?.unitsStaged ?? 0],
    ["Stock snapshots staged", summary?.stockSnapshotsStaged ?? 0], ["Vouchers staged", summary?.vouchersStaged ?? 0],
    ["Voucher items staged", summary?.voucherItemsStaged ?? 0], ["Credit notes staged", summary?.creditNotesStaged ?? 0],
    ["Debit notes staged", summary?.debitNotesStaged ?? 0], ["Ledger lines captured", summary?.ledgerAdjustmentLinesStaged ?? 0],
    ["Debtors staged", summary?.debtorRowsStaged ?? 0], ["Creditors staged", summary?.creditorRowsStaged ?? 0],
    ["Cashbook entries staged", summary?.cashbookEntriesStaged ?? 0],
    ["Stock ageing rows staged", summary?.stockAgeingRowsStaged ?? 0]
  ];
}

function matchingMetrics(summary?: MatchingSummary): Array<[string, number]> {
  return [
    ["Products to create", summary?.productsToCreate ?? 0], ["Products matched", summary?.productsMatchedExisting ?? 0],
    ["Possible product duplicates", summary?.possibleDuplicateProducts ?? 0], ["Parties to create", summary?.partiesToCreate ?? 0],
    ["Parties matched", summary?.partiesMatchedExisting ?? 0], ["Warehouses to create or map", summary?.warehousesToCreateOrMap ?? 0],
    ["Warehouses matched", summary?.warehousesMatchedExisting ?? 0], ["Likely duplicate vouchers", summary?.vouchersLikelyDuplicates ?? 0],
    ["Cashbook rows matched", summary?.cashbookRowsMatched ?? 0], ["Cashbook rows unmatched", summary?.cashbookRowsUnmatched ?? 0],
    ["Cashbook review required", summary?.cashbookRowsReviewRequired ?? 0], ["Payments to create", summary?.paymentsToCreate ?? 0]
  ];
}

function dryRunMetrics(summary?: DryRunSummary): Array<[string, number]> {
  return [
    ["Products to create", Number(summary?.productsToCreate ?? 0)],
    ["Products matched", Number(summary?.productsMatchedExisting ?? 0)],
    ["Invoices previewed", Number(summary?.purchaseInvoicesPreviewed ?? 0) + Number(summary?.salesInvoicesPreviewed ?? 0) + Number(summary?.creditNotesPreviewed ?? 0) + Number(summary?.debitNotesPreviewed ?? 0)],
    ["Snapshot adjustments", Number(summary?.snapshotAdjustmentsPreviewed ?? 0)],
    ["Stock movements previewed", Number(summary?.stockMovementsPreviewed ?? 0)],
    ["Stock movements skipped", Number(summary?.stockMovementsSkippedDueToInvoiceOnly ?? 0) + Number(summary?.stockMovementsSkippedBeforeSnapshotDate ?? 0)],
    ["Outstanding snapshots", Number(summary?.outstandingSnapshotsPreviewed ?? 0)],
    ["Payments previewed", Number(summary?.customerPaymentsPreviewed ?? 0) + Number(summary?.supplierPaymentsPreviewed ?? 0)],
    ["Cashbook review", Number(summary?.cashbookRowsUnmatched ?? 0) + Number(summary?.cashbookRowsReviewRequired ?? 0)],
    ["Warnings", Number(summary?.warnings ?? 0)],
    ["Blocking errors", Number(summary?.blockingErrors ?? 0)],
    ["Review required", Number(summary?.reviewRequiredCount ?? 0)]
  ];
}

function commitMetrics(summary?: CommitSummary): Array<[string, number]> {
  return [
    ["Products created", Number(summary?.productsCreated ?? 0)],
    ["Products matched", Number(summary?.productsMatchedExisting ?? 0)],
    ["Products updated", Number(summary?.productsUpdated ?? 0)],
    ["Parties created", Number(summary?.partiesCreated ?? 0)],
    ["Parties matched", Number(summary?.partiesMatchedExisting ?? 0)],
    ["Warehouses created", Number(summary?.warehousesCreated ?? 0)],
    ["Units created", Number(summary?.unitsCreated ?? 0)],
    ["Snapshot rows", Number(summary?.snapshotRowsProcessed ?? 0)],
    ["Snapshot movements", Number(summary?.snapshotMovementsCreated ?? 0)],
    ["Snapshot no change", Number(summary?.snapshotRowsNoChange ?? 0)],
    ["Sales invoices", Number(summary?.salesInvoicesCreated ?? 0)],
    ["Sales invoice items", Number(summary?.salesInvoiceItemsCreated ?? 0)],
    ["Purchase invoices", Number(summary?.purchaseInvoicesCreated ?? 0)],
    ["Purchase invoice items", Number(summary?.purchaseInvoiceItemsCreated ?? 0)],
    ["Credit notes", Number(summary?.creditNotesCreated ?? 0)],
    ["Credit note items", Number(summary?.creditNoteItemsCreated ?? 0)],
    ["Debit notes", Number(summary?.debitNotesCreated ?? 0)],
    ["Debit note items", Number(summary?.debitNoteItemsCreated ?? 0)],
    ["Financial ledger lines", Number(summary?.taxLinesCaptured ?? 0) + Number(summary?.discountLinesCaptured ?? 0) + Number(summary?.freightLinesCaptured ?? 0) + Number(summary?.roundOffLinesCaptured ?? 0) + Number(summary?.otherChargeLinesCaptured ?? 0)],
    ["Return movements created", Number(summary?.stockMovementsCreatedFromReturns ?? 0)],
    ["Return movements deferred", Number(summary?.returnStockMovementsDeferred ?? 0)],
    ["Outstanding snapshots", Number(summary?.outstandingSnapshotsCreated ?? 0)],
    ["Customer payments", Number(summary?.customerPaymentsCreated ?? 0)],
    ["Supplier payments", Number(summary?.supplierPaymentsCreated ?? 0)],
    ["Payments linked to invoices", Number(summary?.paymentsLinkedToSalesInvoices ?? 0) + Number(summary?.paymentsLinkedToPurchaseInvoices ?? 0)],
    ["Cashbook unmatched", Number(summary?.cashbookRowsUnmatched ?? 0) + Number(summary?.cashbookRowsReviewRequired ?? 0)],
    ["Cashbook manually resolved", Number(summary?.cashbookRowsManuallyResolved ?? 0)],
    ["Cashbook ignored", Number(summary?.cashbookRowsIgnored ?? 0)],
    ["Manual payments", Number(summary?.manualCustomerPaymentsCreated ?? 0) + Number(summary?.manualSupplierPaymentsCreated ?? 0)],
    ["Manual invoice links", Number(summary?.manualPaymentsLinkedToSalesInvoices ?? 0) + Number(summary?.manualPaymentsLinkedToPurchaseInvoices ?? 0)],
    ["Duplicate payments skipped", Number(summary?.duplicatePaymentsSkipped ?? 0)],
    ["Duplicate vouchers skipped", Number(summary?.duplicateSalesVouchersSkipped ?? 0) + Number(summary?.duplicatePurchaseVouchersSkipped ?? 0)],
    ["Voucher stock movements", Number(summary?.voucherStockMovementsCreated ?? 0)],
    ["Stock movements skipped", Number(summary?.stockMovementsSkippedDueToInvoiceOnly ?? 0)],
    ["Records still deferred", Number(summary?.vouchersDeferredForFuturePhase ?? 0) + Number(summary?.futurePhaseCashbookRowsSkipped ?? 0) + Number(summary?.futurePhaseAgeingRowsSkipped ?? 0)]
  ];
}

function voucherReadyMetrics(summary?: DryRunSummary): Array<[string, number]> {
  return [
    ["Sales vouchers ready", Number(summary?.salesInvoicesPreviewed ?? 0)],
    ["Purchase vouchers ready", Number(summary?.purchaseInvoicesPreviewed ?? 0)],
    ["Credit notes ready", Number(summary?.creditNotesPreviewed ?? 0)],
    ["Debit notes ready", Number(summary?.debitNotesPreviewed ?? 0)],
    ["Debtors ready", Number(summary?.debtorRowsProcessed ?? 0)],
    ["Creditors ready", Number(summary?.creditorRowsProcessed ?? 0)],
    ["Cashbook rows ready", Number(summary?.cashbookRowsReady ?? 0)],
    ["Payments to create", Number(summary?.customerPaymentsPreviewed ?? 0) + Number(summary?.supplierPaymentsPreviewed ?? 0)],
    ["Review required entries", Number(summary?.cashbookRowsReviewRequired ?? 0) + Number(summary?.cashbookRowsUnmatched ?? 0)],
    ["Voucher items ready", Number(summary?.voucherItemsReady ?? 0)],
    ["Ledger lines captured", Number(summary?.taxLinesCaptured ?? 0) + Number(summary?.discountLinesCaptured ?? 0) + Number(summary?.freightLinesCaptured ?? 0) + Number(summary?.roundOffLinesCaptured ?? 0)],
    ["Vouchers blocked", Number(summary?.vouchersBlocked ?? 0)],
    ["Duplicates skipped", Number(summary?.duplicateVouchersSkipped ?? 0)]
  ];
}

function defaultStockImpactMode(): StockImpactMode {
  return "CREATE_INVOICES_ONLY";
}

function strategyExplanation(strategy: Strategy) {
  if (strategy === "SNAPSHOT_FIRST") return "Snapshot-first uses closing stock as the stock baseline.";
  if (strategy === "TRANSACTION_HISTORY") return "Transaction history reconstructs stock from purchase, sale, and return vouchers.";
  if (strategy === "HYBRID_RECONCILIATION") return "Hybrid reconciliation combines transaction history with a dated closing-stock checkpoint.";
  return "Choose a strategy before running the simulation.";
}

function stockImpactExplanation(strategy: Strategy, mode: StockImpactMode) {
  if (mode === "CREATE_INVOICES_ONLY") return "Vouchers are previewed as invoices without ledger movements, preventing snapshot stock from being counted twice.";
  if (mode === "APPLY_STOCK_MOVEMENTS_AFTER_SNAPSHOT_DATE") return "Vouchers on or before the snapshot date are invoice-only; later vouchers preview stock movements.";
  if (strategy === "SNAPSHOT_FIRST") return "All voucher movements are previewed. Review this carefully because historical movements may overlap the snapshot.";
  return "Purchases, sales, and supported returns preview their corresponding stock-ledger movements.";
}

function dryRunItemName(row: DryRunItem) {
  const preview = row.preview;
  return String(preview.productName ?? preview.name ?? preview.voucherNumber ?? preview.partyName ?? preview.fileName ?? readable(row.itemType));
}

function dryRunImpact(preview: Record<string, unknown>) {
  const preferred = ["snapshotDate", "stockAtSnapshotDate", "importedSnapshotQuantity", "deltaAtSnapshotDate", "projectedCurrentStockAfterApplyingLaterTransactions", "quantity", "rateSource", "invoiceItemCount", "reason"];
  const details = preferred
    .filter((key) => preview[key] !== undefined && preview[key] !== "")
    .map((key) => `${readable(key)}: ${String(preview[key])}`);
  return details.length ? details.join(" | ") : "No stock or financial write would be made by this dry run.";
}

function readable(value?: string) {
  return (value || "Unknown").replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function fileSize(bytes: number) {
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  if (bytes >= 1024) return `${Math.ceil(bytes / 1024)} KB`;
  return `${bytes} bytes`;
}

function dateRange(file: SessionFile) {
  if (file.dateRangeStart && file.dateRangeEnd) return `${file.dateRangeStart} to ${file.dateRangeEnd}`;
  return file.dateRangeStart || file.dateRangeEnd || "Date range not detected";
}

function number(value?: number) {
  return Number(value || 0).toLocaleString("en-IN", { maximumFractionDigits: 3 });
}

function signedNumber(value?: number) {
  const numeric = Number(value || 0);
  return `${numeric > 0 ? "+" : ""}${number(numeric)}`;
}

function money(value?: number) {
  if (value == null) return "-";
  return `₹${Number(value).toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;
}
