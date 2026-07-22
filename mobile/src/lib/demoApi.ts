type Init = RequestInit;

const products = [
  product("p1", "Maggi Masala Noodles 70g", "8901058844505", 94, 8.5, 12, 120),
  product("p2", "Parle-G 250g", "8901719101018", 18, 19, 25, 80),
  product("p3", "Dairy Milk 50g", "7622201145213", 38, 32, 45, 45),
  product("p4", "Surf Excel 1kg", "8901030865476", 21, 124, 145, 25),
  product("p5", "Tata Salt 1kg", "8904043901015", 124, 18, 24, 70),
  product("p6", "Dettol Soap 75g", "8901396311829", 42, 27, 36, 55),
  product("p7", "Fortune Oil 1L", "8906007280012", 26, 112, 135, 35),
  product("p8", "Colgate Toothpaste 100g", "8901314011759", 31, 44, 58, 35)
];

const warehouses = [
  { id: "w1", name: "Main Godown" },
  { id: "w2", name: "Retail Counter" }
];

export async function demoApi<T>(path: string, init: Init = {}): Promise<T> {
  await new Promise((resolve) => setTimeout(resolve, 120));
  const method = (init.method ?? "GET").toUpperCase();

  if (path === "/api/auth/login") {
    const body = parseBody(init);
    return {
      accessToken: "mobile-demo-token",
      tenantId: "tenant-demo",
      userId: "user-owner",
      role: "OWNER",
      email: body.email ?? "owner@demo.com",
      fullName: "Demo Owner"
    } as T;
  }

  if (path.startsWith("/api/products/barcode/")) {
    const barcode = decodeURIComponent(path.split("/").pop() ?? "");
    const product = products.find((row) => row.barcode === barcode);
    if (!product) throw new Error("Barcode not found in demo data");
    return product as T;
  }

  if (path.startsWith("/api/products")) {
    const query = new URL(`http://demo${path}`).searchParams.get("query")?.toLowerCase();
    const rows = query ? products.filter((row) => row.name.toLowerCase().includes(query)) : products;
    return page(rows) as T;
  }

  if (path === "/api/warehouses") return warehouses as T;

  if (path === "/api/dashboard/summary") {
    return {
      totalStockValue: 238640,
      monthlySales: 184250,
      grossProfit: 42680,
      lowStockCount: lowStock().length,
      deadStockValue: 42600,
      outstandingReceivables: 108550
    } as T;
  }

  if (path === "/api/dashboard/low-stock" || path === "/api/alerts/low-stock") return lowStock() as T;

  if (path === "/api/stock/adjustment" && method === "POST") {
    const body = parseBody(init);
    adjustStock(body.productId, Number(body.quantityDelta || 0));
    return { id: `movement-${Date.now()}`, movementType: "ADJUSTMENT", ...body } as T;
  }

  if (path === "/api/stock/transfer" && method === "POST") {
    return { id: `transfer-${Date.now()}`, status: "RECORDED", ...parseBody(init) } as T;
  }

  if (path === "/api/forecast/run") return { status: "COMPLETED" } as T;
  if (path === "/api/reorder/suggestions") return reorderSuggestions() as T;

  if (path === "/api/imports/invoice-upload" && method === "POST") {
    return {
      status: "EXTRACTED",
      supplierName: "ABC FMCG Supplier",
      invoiceNumber: "MOBILE-DEMO-INV",
      invoiceDate: today(),
      totalAmount: 12840,
      items: [
        { productName: "Parle-G 250g", quantity: 24, rate: 19 },
        { productName: "Surf Excel 1kg", quantity: 10, rate: 124 }
      ],
      note: "Demo extraction only. No backend or OCR service required."
    } as T;
  }

  return {} as T;
}

function product(id: string, name: string, barcode: string, currentStock: number, defaultPurchasePrice: number, defaultSalesPrice: number, reorderPoint: number) {
  return { id, name, barcode, currentStock, defaultPurchasePrice, defaultSalesPrice, reorderPoint };
}

function page<T>(content: T[]) {
  return { content };
}

function parseBody(init: Init) {
  return typeof init.body === "string" ? JSON.parse(init.body || "{}") : {};
}

function lowStock() {
  return products
    .filter((row) => row.currentStock <= row.reorderPoint)
    .map((row) => ({ productId: row.id, productName: row.name, currentStock: row.currentStock, reorderPoint: row.reorderPoint }));
}

function adjustStock(productId: string, quantityDelta: number) {
  const row = products.find((item) => item.id === productId);
  if (row) row.currentStock = Math.max(0, row.currentStock + quantityDelta);
}

function reorderSuggestions() {
  return [
    {
      productId: "p2",
      productName: "Parle-G 250g",
      warehouseId: "w1",
      warehouseName: "Main Godown",
      currentStock: products.find((row) => row.id === "p2")?.currentStock ?? 18,
      averageDailyDemand: 3,
      last7DaysDemand: 25,
      last30DaysDemand: 92,
      expectedStockoutDate: today(6),
      supplierLeadTimeDays: 5,
      safetyStock: 25,
      minimumOrderQuantity: 24,
      pendingPurchaseQuantity: 0,
      pendingSalesQuantity: 12,
      recommendedQuantity: 45,
      reason: "Sales increased 22% in the last 4 weeks"
    },
    {
      productId: "p4",
      productName: "Surf Excel 1kg",
      warehouseId: "w1",
      warehouseName: "Main Godown",
      currentStock: products.find((row) => row.id === "p4")?.currentStock ?? 21,
      averageDailyDemand: 2.5,
      last7DaysDemand: 20,
      last30DaysDemand: 78,
      expectedStockoutDate: today(8),
      supplierLeadTimeDays: 5,
      safetyStock: 10,
      minimumOrderQuantity: 24,
      pendingPurchaseQuantity: 0,
      pendingSalesQuantity: 8,
      recommendedQuantity: 34,
      reason: "Current stock plus pending orders is near reorder point"
    }
  ];
}

function today(offset = 0) {
  const date = new Date();
  date.setDate(date.getDate() + offset);
  return date.toISOString().slice(0, 10);
}
