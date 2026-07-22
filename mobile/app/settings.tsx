import { useRouter } from "expo-router";
import { Text } from "react-native";
import { clearToken } from "@/src/lib/api";
import { Button, Card, Screen } from "@/src/components/ui";

export default function SettingsScreen() {
  const router = useRouter();
  async function signOut() {
    await clearToken();
    router.replace("/");
  }
  return (
    <Screen title="Settings">
      <Card>
        <Text style={{ color: "#53645d" }}>API base URL is controlled by EXPO_PUBLIC_API_URL.</Text>
        <Button label="Sign out" onPress={signOut} />
      </Card>
    </Screen>
  );
}
