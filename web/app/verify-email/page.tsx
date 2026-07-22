"use client";

import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";

export default function VerifyEmailPage() {
  const [token, setToken] = useState("");
  const verify = useMutation({
    mutationFn: (rawToken: string) => api<{ message: string }>("/api/auth/verify-email", { method: "POST", body: JSON.stringify({ token: rawToken }) })
  });
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    const rawToken = new URLSearchParams(window.location.search).get("token") ?? "";
    setToken(rawToken);
    if (rawToken) {
      verify.mutate(rawToken);
    }
  }, [verify]);

  return (
    <main className="flex min-h-screen items-center justify-center bg-[#eef7f0] px-5">
      <section className="w-full max-w-md rounded border border-ink/10 bg-white p-6 shadow-soft">
        <h1 className="text-2xl font-semibold">Verify email</h1>
        {!token ? <p className="mt-3 text-sm text-coral">Verification link is invalid or expired.</p> : null}
        {verify.isPending ? <p className="mt-3 text-sm text-ink/60">Verifying your account...</p> : null}
        {verify.data ? <p className="mt-3 text-sm text-moss">{verify.data.message}</p> : null}
        {verify.error ? <p className="mt-3 text-sm text-coral">{verify.error.message}</p> : null}
        <Link className="mt-6 inline-block font-medium text-moss" href="/login">Go to login</Link>
      </section>
    </main>
  );
}
