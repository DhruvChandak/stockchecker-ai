"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Ban, CheckCircle2, Clock3 } from "lucide-react";
import { useMemo, useState } from "react";
import { ErrorState } from "@/components/ErrorState";
import { LoadingState } from "@/components/LoadingState";
import { PrimaryButton, SecondaryButton } from "@/components/FormControls";
import { api } from "@/lib/api";

type CashbookCandidate = {
  type: "CUSTOMER" | "SUPPLIER" | "SALES_INVOICE" | "PURCHASE_INVOICE";
  action: "MAP_CUSTOMER" | "MAP_SUPPLIER" | "MAP_SALES_INVOICE" | "MAP_PURCHASE_INVOICE";
  targetId: string;
  label: string;
  detail: string;
  confidence: number;
  reason: string;
};

type CashbookReviewEntry = {
  id: string;
  sourceRowNumber: number;
  entryDate?: string;
  partyName: string;
  amount: number;
  direction: string;
  referenceNumber?: string;
  cashbookMatchStatus: string;
  matchReason?: string;
  candidates: CashbookCandidate[];
};

export function CashbookReviewPanel({ sessionId }: { sessionId: string }) {
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<Record<string, string>>({});
  const queryKey = ["smart-import-cashbook-review", sessionId];
  const rows = useQuery({
    queryKey,
    queryFn: () => api<CashbookReviewEntry[]>(`/api/import-sessions/${sessionId}/cashbook-review`),
    enabled: Boolean(sessionId)
  });
  const resolve = useMutation({
    mutationFn: ({ entryId, action, targetId, confirmation }: { entryId: string; action: string; targetId?: string; confirmation?: string }) =>
      api(`/api/import-sessions/${sessionId}/cashbook-review/${entryId}/resolve`, {
        method: "POST",
        body: JSON.stringify({ action, targetId, confirmation })
      }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey }),
        queryClient.invalidateQueries({ queryKey: ["smart-import-staging", sessionId] }),
        queryClient.invalidateQueries({ queryKey: ["smart-import-commit", sessionId] })
      ]);
    }
  });

  if (rows.isLoading) return <LoadingState label="Loading cashbook review entries..." />;
  if (rows.error) return <ErrorState error={rows.error} />;
  if (!rows.data?.length) {
    return <div className="border border-dashed border-ink/20 px-4 py-5 text-sm text-ink/55">No unresolved cashbook entries.</div>;
  }

  return (
    <div className="divide-y divide-ink/10 border-y border-ink/10">
      {rows.data.map((row) => {
        const selectedValue = selected[row.id] ?? "";
        return (
          <CashbookReviewRow
            key={row.id}
            row={row}
            selectedValue={selectedValue}
            onSelect={(value) => setSelected((current) => ({ ...current, [row.id]: value }))}
            pending={resolve.isPending}
            error={resolve.error}
            onPost={() => {
              const [action, targetId] = selectedValue.split("|");
              if (!action || !targetId) return;
              if (!window.confirm("Post this cashbook entry as a payment to the selected record?")) return;
              resolve.mutate({ entryId: row.id, action, targetId, confirmation: "POST PAYMENT" });
            }}
            onIgnore={() => {
              if (!window.confirm("Ignore this entry as non-business? No payment will be created.")) return;
              resolve.mutate({ entryId: row.id, action: "IGNORE" });
            }}
            onKeep={() => resolve.mutate({ entryId: row.id, action: "KEEP_UNRESOLVED" })}
          />
        );
      })}
    </div>
  );
}

function CashbookReviewRow({
  row, selectedValue, onSelect, pending, error, onPost, onIgnore, onKeep
}: {
  row: CashbookReviewEntry;
  selectedValue: string;
  onSelect: (value: string) => void;
  pending: boolean;
  error: Error | null;
  onPost: () => void;
  onIgnore: () => void;
  onKeep: () => void;
}) {
  const candidate = useMemo(
    () => row.candidates.find((item) => `${item.action}|${item.targetId}` === selectedValue),
    [row.candidates, selectedValue]
  );
  return (
    <section className="grid gap-4 py-5 lg:grid-cols-[minmax(220px,0.8fr)_minmax(340px,1.4fr)]">
      <div>
        <div className="flex flex-wrap items-center gap-2 text-sm"><strong>{row.partyName || "Unidentified party"}</strong><span className="text-ink/45">Row {row.sourceRowNumber}</span></div>
        <div className="mt-2 text-sm text-ink/65">{row.entryDate || "No date"} | {row.direction} | {formatMoney(row.amount)}</div>
        <div className="mt-1 text-xs text-ink/50">{row.referenceNumber || "No reference"}</div>
        <div className="mt-3 text-xs leading-5 text-amber-800">{row.matchReason || row.cashbookMatchStatus}</div>
      </div>
      <div className="space-y-3">
        <select
          aria-label={`Resolution target for row ${row.sourceRowNumber}`}
          className="focus-ring min-h-11 w-full border border-ink/20 bg-white px-3 text-sm"
          value={selectedValue}
          onChange={(event) => onSelect(event.target.value)}
        >
          <option value="">Select customer, supplier, or invoice</option>
          {row.candidates.map((item) => (
            <option key={`${item.action}:${item.targetId}`} value={`${item.action}|${item.targetId}`}>
              {readable(item.type)} | {item.label} | {Math.round(Number(item.confidence) * 100)}%
            </option>
          ))}
        </select>
        {candidate ? (
          <div className="border-l-2 border-moss/50 pl-3 text-xs leading-5 text-ink/65"><div className="font-semibold text-ink">{candidate.reason}</div><div>{candidate.detail}</div></div>
        ) : null}
        <div className="flex flex-wrap gap-2">
          <PrimaryButton className="gap-2" onClick={onPost} disabled={!candidate || pending}><CheckCircle2 size={16} /> Post payment</PrimaryButton>
          <SecondaryButton className="gap-2" onClick={onIgnore} disabled={pending}><Ban size={16} /> Ignore</SecondaryButton>
          <SecondaryButton className="gap-2" onClick={onKeep} disabled={pending}><Clock3 size={16} /> Keep unresolved</SecondaryButton>
        </div>
        {error ? <div className="text-sm text-coral">{error.message}</div> : null}
      </div>
    </section>
  );
}

function readable(value: string) {
  return value.toLowerCase().replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function formatMoney(value: number) {
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(Number(value || 0));
}
