import { ReactNode } from "react";

export function StatCard({ label, value, icon, tone = "moss" }: { label: string; value: ReactNode; icon?: ReactNode; tone?: "moss" | "amber" | "coral" }) {
  const toneClasses = {
    moss: "bg-mint text-moss",
    amber: "bg-amber/15 text-amber",
    coral: "bg-coral/15 text-coral"
  };
  return (
    <div className="rounded border border-ink/10 bg-white p-4 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="text-sm font-medium text-ink/55">{label}</div>
          <div className="mt-2 text-2xl font-semibold tracking-normal text-ink">{value}</div>
        </div>
        {icon ? <div className={`rounded p-2 ${toneClasses[tone]}`}>{icon}</div> : null}
      </div>
    </div>
  );
}
