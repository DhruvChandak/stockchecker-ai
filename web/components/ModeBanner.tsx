"use client";

import { API_URL, isDemoMode } from "@/lib/api";

export function ModeBanner() {
  if (isDemoMode()) {
    return (
      <div className="border-b border-amber-300 bg-amber-50 px-4 py-2 text-center text-sm font-medium text-amber-900">
        Demo mode is enabled. Data is stored in your browser and does not use the backend database.
      </div>
    );
  }

  return (
    <div className="border-b border-moss/20 bg-mint px-4 py-2 text-center text-sm font-medium text-moss">
      Backend mode: web calls API at {API_URL}.
    </div>
  );
}
