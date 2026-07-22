import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");

test("backend mode keeps demo API behind the env flag", () => {
  const api = read("lib/api.ts");
  const demoApi = read("lib/demoApi.ts");

  assert.match(api, /if \(DEMO_MODE\)/);
  assert.match(api, /return demoApi<T>\(path, init\)/);
  assert.match(demoApi, /Demo API cannot be used while Backend mode is active\./);
});

test("backend pages include clean empty states instead of demo fallback values", () => {
  const dashboard = read("app/dashboard/page.tsx");
  const forecasts = read("app/forecasts/page.tsx");
  const deadStock = read("app/dead-stock/page.tsx");
  const dataQuality = read("app/data-quality/page.tsx");
  const imports = read("app/imports/page.tsx");

  assert.match(dashboard, /No business data yet\. Import Tally\/Excel data or add your first product\./);
  assert.match(forecasts, /No forecasts yet\. Import sales history or run forecasting after adding stock movements\./);
  assert.match(deadStock, /No dead-stock items found\. Dead-stock detection will appear after inventory history exists\./);
  assert.match(dataQuality, /No data-quality issues yet\. Import or add products to begin cleanup\./);
  assert.match(imports, /No imports yet\./);

  for (const source of [dashboard, forecasts, deadStock, dataQuality, imports]) {
    assert.doesNotMatch(source, /Parle-G|Surf Excel|MAGGI|demo-tally-vouchers/);
  }
});
