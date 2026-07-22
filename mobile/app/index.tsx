import { useRouter } from "expo-router";
import { useState } from "react";
import { KeyboardAvoidingView, Platform, Text, View } from "react-native";
import { api, setToken } from "@/src/lib/api";
import { Button, Card, ErrorText, Input, Label, styles } from "@/src/components/ui";

type AuthResponse = { accessToken: string };

export default function LoginScreen() {
  const router = useRouter();
  const [email, setEmail] = useState("owner@demo.com");
  const [password, setPassword] = useState("password123");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function login() {
    setLoading(true);
    setError("");
    try {
      const response = await api<AuthResponse>("/api/auth/login", { method: "POST", body: JSON.stringify({ email, password }) });
      await setToken(response.accessToken);
      router.replace("/dashboard");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : undefined} style={styles.screen}>
      <View style={{ flex: 1, justifyContent: "center", gap: 18 }}>
        <View>
          <Text style={styles.title}>StockPilot AI</Text>
          <Text style={{ color: "#53645d", marginTop: 6 }}>Mobile inventory and warehouse actions</Text>
        </View>
        <Card>
          <Label>Email</Label>
          <Input value={email} onChangeText={setEmail} autoCapitalize="none" keyboardType="email-address" />
          <Label>Password</Label>
          <Input value={password} onChangeText={setPassword} secureTextEntry />
          <ErrorText error={error} />
          <Button label="Sign in" onPress={login} loading={loading} />
        </Card>
      </View>
    </KeyboardAvoidingView>
  );
}
