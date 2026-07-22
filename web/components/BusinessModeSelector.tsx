"use client";

import clsx from "clsx";

export type BusinessMode = "RETAIL" | "WHOLESALE" | "HYBRID";

const modes: Array<{ value: BusinessMode; title: string; description: string }> = [
  {
    value: "RETAIL",
    title: "Retail",
    description: "Counter sales, product lookup, stock in/out, low-stock alerts."
  },
  {
    value: "WHOLESALE",
    title: "Wholesale",
    description: "Bulk units, godowns, customer pricing, credit and reorder workflows."
  },
  {
    value: "HYBRID",
    title: "Hybrid",
    description: "Use retail counter and wholesale distributor workflows together."
  }
];

export function BusinessModeSelector({
  value,
  onChange,
  compact = false
}: {
  value: string;
  onChange: (value: BusinessMode) => void;
  compact?: boolean;
}) {
  return (
    <div className={clsx("grid gap-3", compact ? "sm:grid-cols-3" : "lg:grid-cols-3")}>
      {modes.map((mode) => {
        const active = value === mode.value;
        return (
          <button
            key={mode.value}
            type="button"
            onClick={() => onChange(mode.value)}
            className={clsx(
              "focus-ring rounded border p-4 text-left transition",
              active ? "border-moss bg-mint text-moss" : "border-ink/10 bg-white text-ink hover:border-moss/40 hover:bg-ink/[0.02]"
            )}
          >
            <span className="block text-sm font-semibold">{mode.title}</span>
            <span className="mt-1 block text-xs leading-5 text-ink/60">{mode.description}</span>
            <span className="mt-3 inline-flex rounded bg-white/70 px-2 py-1 text-[11px] font-semibold tracking-normal text-ink/55">
              {mode.value}
            </span>
          </button>
        );
      })}
    </div>
  );
}

