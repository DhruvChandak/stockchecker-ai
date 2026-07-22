import { ReactNode } from "react";

export function Panel({ title, children, action }: { title: string; children: ReactNode; action?: ReactNode }) {
  return (
    <section className="rounded border border-ink/10 bg-white shadow-sm">
      <div className="flex min-h-14 items-center justify-between gap-3 border-b border-ink/10 px-4 py-3">
        <h2 className="text-base font-semibold tracking-normal">{title}</h2>
        {action}
      </div>
      <div className="p-4">{children}</div>
    </section>
  );
}
