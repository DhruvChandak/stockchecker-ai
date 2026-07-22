import { ReactNode } from "react";

export function EmptyState({ title, label, children, action }: { title?: string; label?: string; children?: ReactNode; action?: ReactNode }) {
  const heading = title ?? label ?? "Nothing to show yet.";
  return (
    <div className="rounded border border-dashed border-ink/20 bg-ink/[0.02] p-4 text-sm">
      <div className="font-semibold text-ink">{heading}</div>
      {children ? <div className="mt-1 text-ink/60">{children}</div> : null}
      {action ? <div className="mt-3">{action}</div> : null}
    </div>
  );
}
