import { useEffect, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { api, Page, Product, Warehouse } from "@/src/lib/api";
import { Button, Card, Input, Label, Screen } from "@/src/components/ui";

export default function StockAdjustmentScreen({ mode }: { mode: "in" | "out" }) {
  const [products, setProducts] = useState<Product[]>([]);
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [productId, setProductId] = useState("");
  const [warehouseId, setWarehouseId] = useState("");
  const [quantity, setQuantity] = useState("1");
  const [reason, setReason] = useState(mode === "in" ? "Received stock" : "Issued stock");
  const [message, setMessage] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let mounted = true;
    Promise.all([
      api<Page<Product>>("/api/products?size=200"),
      api<Warehouse[]>("/api/warehouses")
    ]).then(([productPage, warehouseRows]) => {
      if (!mounted) return;
      setProducts(productPage.content);
      setWarehouses(warehouseRows);
      setProductId((current) => current || productPage.content[0]?.id || "");
      setWarehouseId((current) => current || warehouseRows[0]?.id || "");
    }).catch((error) => setMessage(error.message));
    return () => {
      mounted = false;
    };
  }, []);

  async function save() {
    const amount = Number(quantity || 0);
    if (!productId || !warehouseId) {
      setMessage("Select a product and warehouse first.");
      return;
    }
    if (!Number.isFinite(amount) || amount <= 0) {
      setMessage("Enter a quantity greater than zero.");
      return;
    }
    if (!reason.trim()) {
      setMessage("Enter a reason for the audit log.");
      return;
    }
    setSaving(true);
    setMessage("");
    try {
      const qty = amount * (mode === "out" ? -1 : 1);
      await api("/api/stock/adjustment", { method: "POST", body: JSON.stringify({ productId, warehouseId, quantityDelta: qty, notes: reason.trim() }) });
      setMessage("Stock updated");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Could not update stock");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Screen title={mode === "in" ? "Stock In" : "Stock Out"}>
      <ScrollView contentContainerStyle={{ gap: 12 }}>
        <Card>
          <Label>Product</Label>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={localStyles.pillRow}>
            {products.map((product) => (
              <Pressable key={product.id} onPress={() => setProductId(product.id)} style={[localStyles.pill, productId === product.id && localStyles.pillSelected]}>
                <Text style={[localStyles.pillText, productId === product.id && localStyles.pillTextSelected]}>{product.name}</Text>
                <Text style={localStyles.pillMeta}>Stock {product.currentStock}</Text>
              </Pressable>
            ))}
          </ScrollView>
          <Label>Warehouse</Label>
          <View style={localStyles.wrapRow}>
            {warehouses.map((warehouse) => (
              <Pressable key={warehouse.id} onPress={() => setWarehouseId(warehouse.id)} style={[localStyles.pill, warehouseId === warehouse.id && localStyles.pillSelected]}>
                <Text style={[localStyles.pillText, warehouseId === warehouse.id && localStyles.pillTextSelected]}>{warehouse.name}</Text>
              </Pressable>
            ))}
          </View>
          <Label>Quantity</Label>
          <Input value={quantity} onChangeText={setQuantity} keyboardType="numeric" />
          <Label>Reason</Label>
          <Input value={reason} onChangeText={setReason} placeholder="Reason for stock adjustment" />
          <Button label="Save stock movement" onPress={save} loading={saving} />
          {message ? <Text style={{ color: message.includes("updated") ? "#326657" : "#df6b57", fontWeight: "700" }}>{message}</Text> : null}
        </Card>
      </ScrollView>
    </Screen>
  );
}

const localStyles = StyleSheet.create({
  pillRow: { gap: 8, paddingVertical: 2 },
  wrapRow: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  pill: { borderWidth: 1, borderColor: "#ccd9d1", borderRadius: 6, paddingHorizontal: 10, paddingVertical: 8, backgroundColor: "white", maxWidth: 220 },
  pillSelected: { borderColor: "#326657", backgroundColor: "#e8f3ed" },
  pillText: { color: "#10201b", fontWeight: "700" },
  pillTextSelected: { color: "#326657" },
  pillMeta: { color: "#74847d", fontSize: 12, marginTop: 2 }
});
