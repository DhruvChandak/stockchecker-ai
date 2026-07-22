import { useEffect, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { api, Page, Product, Warehouse } from "@/src/lib/api";
import { Button, Card, Input, Label, Screen } from "@/src/components/ui";

export default function TransferScreen() {
  const [products, setProducts] = useState<Product[]>([]);
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [productId, setProductId] = useState("");
  const [sourceWarehouseId, setSourceWarehouseId] = useState("");
  const [destinationWarehouseId, setDestinationWarehouseId] = useState("");
  const [quantity, setQuantity] = useState("1");
  const [message, setMessage] = useState("");

  useEffect(() => {
    Promise.all([api<Page<Product>>("/api/products?size=200"), api<Warehouse[]>("/api/warehouses")])
      .then(([productPage, warehouseRows]) => {
        setProducts(productPage.content);
        setWarehouses(warehouseRows);
        setProductId(productPage.content[0]?.id || "");
        setSourceWarehouseId(warehouseRows[0]?.id || "");
        setDestinationWarehouseId(warehouseRows[1]?.id || warehouseRows[0]?.id || "");
      })
      .catch((error) => setMessage(error.message));
  }, []);

  async function save() {
    const amount = Number(quantity || 0);
    if (!productId || !sourceWarehouseId || !destinationWarehouseId) {
      setMessage("Select a product and both warehouses.");
      return;
    }
    if (sourceWarehouseId === destinationWarehouseId) {
      setMessage("Source and destination warehouses must be different.");
      return;
    }
    if (!Number.isFinite(amount) || amount <= 0) {
      setMessage("Enter a quantity greater than zero.");
      return;
    }
    try {
      await api("/api/stock/transfer", { method: "POST", body: JSON.stringify({ productId, sourceWarehouseId, destinationWarehouseId, quantity: amount, notes: "Mobile warehouse transfer" }) });
      setMessage("Transfer recorded");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Transfer failed");
    }
  }

  return (
    <Screen title="Warehouse Transfer">
      <ScrollView contentContainerStyle={{ gap: 12 }}>
        <Card>
          <Label>Product</Label>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.row}>
            {products.map((product) => <Pill key={product.id} label={product.name} selected={productId === product.id} onPress={() => setProductId(product.id)} />)}
          </ScrollView>
          <Label>From warehouse</Label>
          <View style={styles.wrap}>{warehouses.map((warehouse) => <Pill key={warehouse.id} label={warehouse.name} selected={sourceWarehouseId === warehouse.id} onPress={() => setSourceWarehouseId(warehouse.id)} />)}</View>
          <Label>To warehouse</Label>
          <View style={styles.wrap}>{warehouses.map((warehouse) => <Pill key={warehouse.id} label={warehouse.name} selected={destinationWarehouseId === warehouse.id} onPress={() => setDestinationWarehouseId(warehouse.id)} />)}</View>
          <Label>Quantity</Label>
          <Input value={quantity} onChangeText={setQuantity} keyboardType="numeric" />
          <Button label="Record transfer" onPress={save} />
          {message ? <Text style={{ color: message.includes("recorded") ? "#326657" : "#df6b57", fontWeight: "700" }}>{message}</Text> : null}
        </Card>
      </ScrollView>
    </Screen>
  );
}

function Pill({ label, selected, onPress }: { label: string; selected: boolean; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} style={[styles.pill, selected && styles.selected]}>
      <Text style={[styles.text, selected && styles.selectedText]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: { gap: 8, paddingVertical: 2 },
  wrap: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  pill: { borderWidth: 1, borderColor: "#ccd9d1", borderRadius: 6, paddingHorizontal: 10, paddingVertical: 8, backgroundColor: "white" },
  selected: { borderColor: "#326657", backgroundColor: "#e8f3ed" },
  text: { color: "#10201b", fontWeight: "700" },
  selectedText: { color: "#326657" }
});
