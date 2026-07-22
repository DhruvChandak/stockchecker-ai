export function LoadingState({ label = "Loading data" }: { label?: string }) {
  return <div className="rounded border border-ink/10 bg-white p-6 text-sm text-ink/60">{label}...</div>;
}
