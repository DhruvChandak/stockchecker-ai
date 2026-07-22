import { InputHTMLAttributes, SelectHTMLAttributes } from "react";
import clsx from "clsx";

export function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-ink/70">{label}</span>
      {children}
    </label>
  );
}

export function TextInput(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className="focus-ring h-10 w-full rounded border border-ink/15 bg-white px-3 text-sm text-ink" />;
}

export function SelectInput(props: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...props} className="focus-ring h-10 w-full rounded border border-ink/15 bg-white px-3 text-sm text-ink" />;
}

export function PrimaryButton(props: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return <button {...props} className={clsx("focus-ring inline-flex h-10 items-center justify-center rounded bg-moss px-4 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50", props.className)} />;
}

export function SecondaryButton(props: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return <button {...props} className={clsx("focus-ring inline-flex h-10 items-center justify-center rounded border border-ink/15 bg-white px-4 text-sm font-semibold text-ink/75 disabled:cursor-not-allowed disabled:opacity-50", props.className)} />;
}
