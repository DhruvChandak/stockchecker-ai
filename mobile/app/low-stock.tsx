import { useEffect, useState } from "react";
import { ScrollView, Text } from "react-native";
import { api } from "@/src/lib/api";
import { Card, Screen } from "@/src/components/ui";

type LowStock = { productId: string; productName: string; currentStock: number; reorderPoint: number };

export default function LowStockScreen() {
  const [rows, setRows] = useState<LowStock[]>([]);
  useEffect(() => { api<LowStock[]>("/api/alerts/low-stock").then(setRows); }, []);
  return (
    <Screen title="Low-Stock Alerts">
      <ScrollView contentContainerStyle={{ gap: 12 }}>
        {rows.map((row) => (
          <Card key={row.productId}>
            <Text style={{ fontWeight: "800", color: "#10201b" }}>{row.productName}</Text>
            <Text>Current stock: {row.currentStock}</Text>
            <Text>Reorder point: {row.reorderPoint}</Text>
          </Card>
        ))}
      </ScrollView>
    </Screen>
  );
}
