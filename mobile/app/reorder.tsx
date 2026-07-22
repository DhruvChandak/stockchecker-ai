import { useEffect, useState } from "react";
import { ScrollView, Text } from "react-native";
import { api } from "@/src/lib/api";
import { Button, Card, Screen } from "@/src/components/ui";

type Suggestion = {
  productId: string;
  productName: string;
  warehouseName: string;
  currentStock: number;
  averageDailyDemand: number;
  expectedStockoutDate?: string;
  supplierLeadTimeDays: number;
  pendingPurchaseQuantity: number;
  pendingSalesQuantity: number;
  recommendedQuantity: number;
  reason: string;
};

export default function ReorderScreen() {
  const [rows, setRows] = useState<Suggestion[]>([]);
  async function load() {
    let suggestions = await api<Suggestion[]>("/api/reorder/suggestions");
    if (suggestions.length === 0) {
      await api("/api/forecast/run", { method: "POST" });
      suggestions = await api<Suggestion[]>("/api/reorder/suggestions");
    }
    setRows(suggestions);
  }
  useEffect(() => { load(); }, []);
  return (
    <Screen title="Reorder Suggestions">
      <ScrollView contentContainerStyle={{ gap: 12 }}>
        <Button label="Run forecast" onPress={load} />
        {rows.map((row) => (
          <Card key={row.productId}>
            <Text style={{ fontWeight: "800", color: "#10201b" }}>{row.productName}</Text>
            <Text>{row.warehouseName} - stock {row.currentStock}</Text>
            <Text>Recommended reorder: {row.recommendedQuantity}</Text>
            <Text>Avg daily sale: {row.averageDailyDemand}</Text>
            <Text>Expected stockout: {row.expectedStockoutDate ?? "not projected"}</Text>
            <Text>Lead time: {row.supplierLeadTimeDays} days</Text>
            <Text>Pending PO/SO: {row.pendingPurchaseQuantity} / {row.pendingSalesQuantity}</Text>
            <Text style={{ color: "#53645d" }}>{row.reason}</Text>
          </Card>
        ))}
      </ScrollView>
    </Screen>
  );
}
