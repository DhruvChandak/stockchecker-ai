"use client";

import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { FormEvent, useState } from "react";
import { api } from "@/lib/api";
import { Field, PrimaryButton, TextInput } from "@/components/FormControls";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const forgot = useMutation({
    mutationFn: () => api<{ message: string }>("/api/auth/forgot-password", { method: "POST", body: JSON.stringify({ email }) })
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    forgot.mutate();
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-[#eef7f0] px-5">
      <form onSubmit={submit} className="w-full max-w-md rounded border border-ink/10 bg-white p-6 shadow-soft">
        <h1 className="text-2xl font-semibold">Reset password</h1>
        <p className="mt-2 text-sm text-ink/60">Enter your email and we will send password reset instructions if an account exists.</p>
        <div className="mt-6">
          <Field label="Email">
            <TextInput type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
          </Field>
        </div>
        {forgot.data ? <p className="mt-4 text-sm text-moss">{forgot.data.message}</p> : null}
        {forgot.error ? <p className="mt-4 text-sm text-coral">{forgot.error.message}</p> : null}
        <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
          <Link className="text-sm font-medium text-moss" href="/login">Back to login</Link>
          <PrimaryButton disabled={forgot.isPending}>{forgot.isPending ? "Sending..." : "Send reset link"}</PrimaryButton>
        </div>
      </form>
    </main>
  );
}
