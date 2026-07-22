export function ErrorState({ error }: { error: unknown }) {
  return <div className="rounded border border-coral/30 bg-coral/10 p-4 text-sm text-coral">{error instanceof Error ? error.message : "Something went wrong"}</div>;
}
