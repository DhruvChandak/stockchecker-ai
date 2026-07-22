"use client";

import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { Field, PrimaryButton, TextInput } from "@/components/FormControls";

export default function ResetPasswordPage() {
  const [token, setToken] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [validationError, setValidationError] = useState("");
  const reset = useMutation({
    mutationFn: () => api<{ message: string }>("/api/auth/reset-password", { method: "POST", body: JSON.stringify({ token, newPassword: password }) })
  });

  useEffect(() => {
    setToken(new URLSearchParams(window.location.search).get("token") ?? "");
  }, []);

  function submit(event: FormEvent) {
    event.preventDefault();
    setValidationError("");
    if (password !== confirmPassword) {
      setValidationError("Passwords do not match.");
      return;
    }
    reset.mutate();
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-[#eef7f0] px-5">
      <form onSubmit={submit} className="w-full max-w-md rounded border border-ink/10 bg-white p-6 shadow-soft">
        <h1 className="text-2xl font-semibold">Choose a new password</h1>
        {!token ? <p className="mt-3 text-sm text-coral">Password reset link is invalid or expired.</p> : null}
        <div className="mt-6 space-y-4">
          <Field label="New password">
            <TextInput type="password" value={password} onChange={(event) => setPassword(event.target.value)} required minLength={8} />
          </Field>
          <Field label="Confirm password">
            <TextInput type="password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} required minLength={8} />
          </Field>
        </div>
        {validationError ? <p className="mt-4 text-sm text-coral">{validationError}</p> : null}
        {reset.data ? <p className="mt-4 text-sm text-moss">{reset.data.message}</p> : null}
        {reset.error ? <p className="mt-4 text-sm text-coral">{reset.error.message}</p> : null}
        <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
          <Link className="text-sm font-medium text-moss" href="/login">Back to login</Link>
          <PrimaryButton disabled={!token || reset.isPending}>{reset.isPending ? "Saving..." : "Reset password"}</PrimaryButton>
        </div>
      </form>
    </main>
  );
}
