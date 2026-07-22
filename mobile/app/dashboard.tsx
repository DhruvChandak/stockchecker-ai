import { useEffect, useState } from "react";
import { RefreshControl, ScrollView, Text, View } from "react-native";
import { api } from "@/src/lib/api";
import { ActionTile, Card, Screen } from "@/src/components/ui";

type Summary = { totalStockValue: number; monthlySales: number; grossProfit: number; lowStockCount: number; deadStockValue: number; outstandingReceivables: number };

export default function DashboardScreen() {
  const [summary, setSummary] = useState<Summary | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  async function load() {
    setRefreshing(true);
    try {
      setSummary(await api<Summary>("/api/dashboard/summary"));
    } finally {
      setRefreshing(false);
    }
  }

  useEffect(() => { load(); }, []);

  return (
    <Screen title="Dashboard">
      <ScrollView refreshControl={<RefreshControl refreshing={refreshing} onRefresh={load} />} contentContainerStyle={{ gap: 12 }}>
        <Card>
          <Text style={{ color: "#10201b", fontSize: 18, fontWeight: "900" }}>Today&apos;s warehouse work</Text>
          <Text style={{ color: "#53645d", lineHeight: 20 }}>Use mobile for fast floor operations: lookup products, update stock, transfer goods, upload invoices, and check urgent reorder alerts.</Text>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 10 }}>
            <ActionTile href="/products" title="Find item" subtitle="Search or barcode input" />
            <ActionTile href="/stock-in" title="Stock in" subtitle="Receive goods into warehouse" />
            <ActionTile href="/stock-out" title="Stock out" subtitle="Issue or reduce stock with reason" tone="amber" />
            <ActionTile href="/transfer" title="Transfer" subtitle="Move stock across godowns" />
            <ActionTile href="/low-stock" title="Alerts" subtitle="Products needing attention" tone="coral" />
            <ActionTile href="/invoice-upload" title="Upload bill" subtitle="Capture invoice for review" />
          </View>
        </Card>
        {[
          ["Stock value", summary?.totalStockValue],
          ["Monthly sales", summary?.monthlySales],
          ["Gross profit", summary?.grossProfit],
          ["Low-stock count", summary?.lowStockCount],
          ["Dead-stock value", summary?.deadStockValue],
          ["Receivables", summary?.outstandingReceivables]
        ].map(([label, value]) => (
          <Card key={String(label)}>
            <Text style={{ color: "#53645d" }}>{label}</Text>
            <Text style={{ fontSize: 24, fontWeight: "800", color: "#10201b" }}>{Number(value ?? 0).toLocaleString("en-IN")}</Text>
          </Card>
        ))}
      </ScrollView>
    </Screen>
  );
}
