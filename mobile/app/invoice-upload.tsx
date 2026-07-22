import * as DocumentPicker from "expo-document-picker";
import { useState } from "react";
import { Text } from "react-native";
import { api } from "@/src/lib/api";
import { Button, Card, Screen } from "@/src/components/ui";

export default function InvoiceUploadScreen() {
  const [result, setResult] = useState<Record<string, unknown> | null>(null);
  const [error, setError] = useState("");

  async function upload() {
    setError("");
    const picked = await DocumentPicker.getDocumentAsync({ copyToCacheDirectory: true });
    if (picked.canceled) return;
    const asset = picked.assets[0];
    const form = new FormData();
    form.append("file", {
      uri: asset.uri,
      name: asset.name,
      type: asset.mimeType ?? "application/octet-stream"
    } as unknown as Blob);
    try {
      setResult(await api<Record<string, unknown>>("/api/imports/invoice-upload", { method: "POST", body: form }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Upload failed");
    }
  }

  return (
    <Screen title="Invoice Upload">
      <Card>
        <Button label="Pick invoice file" onPress={upload} />
        {error ? <Text style={{ color: "#df6b57" }}>{error}</Text> : null}
        {result ? <Text style={{ color: "#10201b" }}>{JSON.stringify(result, null, 2)}</Text> : null}
      </Card>
    </Screen>
  );
}
