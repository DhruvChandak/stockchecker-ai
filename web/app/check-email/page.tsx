"use client";

import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { PrimaryButton } from "@/components/FormControls";

export default function CheckEmailPage() {
  const [email, setEmail] = useState("");
  useEffect(() => {
    setEmail(new URLSearchParams(window.location.search).get("email") ?? "");
  }, []);
  const resend = useMutation({
    mutationFn: () => api<{ message: string }>("/api/auth/resend-verification", { method: "POST", body: JSON.stringify({ email }) })
  });

  return (
    <main className="flex min-h-screen items-center justify-center bg-[#eef7f0] px-5">
      <section className="w-full max-w-md rounded border border-ink/10 bg-white p-6 shadow-soft">
        <h1 className="text-2xl font-semibold">Check your email</h1>
        <p className="mt-2 text-sm text-ink/60">Check your email to verify your StockPilot AI account.</p>
        {email ? <p className="mt-3 rounded bg-mint px-3 py-2 text-sm font-medium text-moss">{email}</p> : null}
        {resend.data ? <p className="mt-4 text-sm text-ink/60">{resend.data.message}</p> : null}
        {resend.error ? <p className="mt-4 text-sm text-coral">{resend.error.message}</p> : null}
        <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
          <Link className="text-sm font-medium text-moss" href="/login">Back to login</Link>
          <PrimaryButton disabled={!email || resend.isPending} onClick={() => resend.mutate()}>
            {resend.isPending ? "Sending..." : "Resend email"}
          </PrimaryButton>
        </div>
      </section>
    </main>
  );
}
