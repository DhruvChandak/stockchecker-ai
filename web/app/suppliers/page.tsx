"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { FormEvent, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { Field, PrimaryButton, TextInput } from "@/components/FormControls";
import { Panel } from "@/components/Panel";
import { api, Page } from "@/lib/api";

type Supplier = { id: string; name: string; phone?: string; email?: string; gstin?: string; creditDays: number; payable: number };

export default function SuppliersPage() {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({ name: "", phone: "", email: "", gstin: "", creditDays: "0" });
  const suppliers = useQuery({ queryKey: ["suppliers"], queryFn: () => api<Page<Supplier>>("/api/suppliers?size=100") });
  const create = useMutation({
    mutationFn: () => api("/api/suppliers", { method: "POST", body: JSON.stringify({ ...form, creditDays: Number(form.creditDays || 0) }) }),
    onSuccess: () => {
      setForm({ name: "", phone: "", email: "", gstin: "", creditDays: "0" });
      queryClient.invalidateQueries({ queryKey: ["suppliers"] });
    }
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    create.mutate();
  }

  return (
    <AppShell title="Suppliers">
      <div className="grid gap-4 xl:grid-cols-[1fr_360px]">
        <Panel title="Supplier Directory">
          <table className="w-full text-left text-sm">
            <thead className="text-ink/50"><tr><th className="py-2">Name</th><th>GSTIN</th><th>Payable</th><th>Credit days</th><th>Contact</th></tr></thead>
            <tbody>{(suppliers.data?.content ?? []).map((supplier) => <tr key={supplier.id} className="border-t border-ink/10"><td className="py-3 font-medium">{supplier.name}</td><td>{supplier.gstin ?? "-"}</td><td className="font-semibold text-moss">₹{Number(supplier.payable || 0).toLocaleString("en-IN")}</td><td>{supplier.creditDays}</td><td>{supplier.phone ?? supplier.email ?? "-"}</td></tr>)}</tbody>
          </table>
        </Panel>
        <Panel title="Add Supplier">
          <form onSubmit={submit} className="space-y-3">
            <Field label="Name"><TextInput value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></Field>
            <Field label="Phone"><TextInput value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} /></Field>
            <Field label="Email"><TextInput value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} type="email" /></Field>
            <Field label="GSTIN"><TextInput value={form.gstin} onChange={(e) => setForm({ ...form, gstin: e.target.value })} /></Field>
            <Field label="Credit days"><TextInput value={form.creditDays} onChange={(e) => setForm({ ...form, creditDays: e.target.value })} type="number" /></Field>
            {create.error ? <p className="text-sm text-coral">{create.error.message}</p> : null}
            <PrimaryButton className="w-full" disabled={create.isPending}>Save supplier</PrimaryButton>
          </form>
        </Panel>
      </div>
    </AppShell>
  );
}
