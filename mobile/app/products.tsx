import { useEffect, useState } from "react";
import { ScrollView, Text } from "react-native";
import { api, Page, Product } from "@/src/lib/api";
import { Button, Card, Input, Label, Screen } from "@/src/components/ui";

export default function ProductsScreen() {
  const [query, setQuery] = useState("");
  const [barcode, setBarcode] = useState("");
  const [products, setProducts] = useState<Product[]>([]);
  const [error, setError] = useState("");

  async function search() {
    const response = await api<Page<Product>>(`/api/products?size=50${query ? `&query=${encodeURIComponent(query)}` : ""}`);
    setProducts(response.content);
  }

  async function barcodeLookup() {
    setError("");
    try {
      const product = await api<Product>(`/api/products/barcode/${barcode}`);
      setProducts([product]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Barcode not found");
    }
  }

  useEffect(() => { search(); }, []);

  return (
    <Screen title="Product Lookup">
      <ScrollView contentContainerStyle={{ gap: 12 }}>
        <Card>
          <Label>Search</Label>
          <Input value={query} onChangeText={setQuery} placeholder="Product name" />
          <Button label="Search products" onPress={search} />
        </Card>
        <Card>
          <Label>Barcode input</Label>
          <Input value={barcode} onChangeText={setBarcode} placeholder="Scan or type barcode" />
          <Button label="Lookup barcode" onPress={barcodeLookup} />
          {error ? <Text style={{ color: "#df6b57" }}>{error}</Text> : null}
        </Card>
        {products.map((product) => (
          <Card key={product.id}>
            <Text style={{ fontWeight: "800", color: "#10201b" }}>{product.name}</Text>
            <Text>Stock: {product.currentStock}</Text>
            <Text>Sale price: {product.defaultSalesPrice}</Text>
          </Card>
        ))}
      </ScrollView>
    </Screen>
  );
}
