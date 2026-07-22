import assert from "node:assert/strict";
import test from "node:test";

test("formats INR money values", () => {
  const formatted = new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0
  }).format(1234);
  assert.match(formatted, /1,234/);
});
