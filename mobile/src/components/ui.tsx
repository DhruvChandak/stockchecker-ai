import { Link, usePathname } from "expo-router";
import { ReactNode } from "react";
import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput, TextInputProps, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

export function Screen({ title, children }: { title: string; children: ReactNode }) {
  const pathname = usePathname();
  const tabs = [
    { href: "/dashboard", label: "Home" },
    { href: "/products", label: "Lookup" },
    { href: "/stock-in", label: "In" },
    { href: "/stock-out", label: "Out" },
    { href: "/settings", label: "More" }
  ] as const;

  return (
    <SafeAreaView style={styles.shell}>
      <View style={styles.header}>
        <Text style={styles.eyebrow}>StockPilot AI mobile</Text>
        <Text style={styles.title}>{title}</Text>
      </View>
      <View style={styles.content}>{children}</View>
      <View style={styles.bottomNav}>
        {tabs.map((tab) => {
          const active = pathname === tab.href;
          return (
            <Link key={tab.href} href={tab.href} style={[styles.tab, active && styles.tabActive]}>
              <Text style={[styles.tabText, active && styles.tabTextActive]}>{tab.label}</Text>
            </Link>
          );
        })}
      </View>
    </SafeAreaView>
  );
}

export function Card({ children }: { children: ReactNode }) {
  return <View style={styles.card}>{children}</View>;
}

export function Label({ children }: { children: ReactNode }) {
  return <Text style={styles.label}>{children}</Text>;
}

export function Input(props: TextInputProps) {
  return <TextInput {...props} style={styles.input} placeholderTextColor="#74847d" />;
}

export function Button({ label, onPress, loading }: { label: string; onPress: () => void; loading?: boolean }) {
  return (
    <Pressable style={styles.button} onPress={onPress} disabled={loading}>
      {loading ? <ActivityIndicator color="white" /> : <Text style={styles.buttonText}>{label}</Text>}
    </Pressable>
  );
}

export function ActionTile({ title, subtitle, href, tone = "moss" }: { title: string; subtitle: string; href: string; tone?: "moss" | "amber" | "coral" }) {
  const toneStyle = tone === "amber" ? styles.amberTile : tone === "coral" ? styles.coralTile : styles.mossTile;
  return (
    <Link href={href} style={[styles.actionTile, toneStyle]}>
      <Text style={styles.actionTitle}>{title}</Text>
      <Text style={styles.actionSubtitle}>{subtitle}</Text>
    </Link>
  );
}

export function ErrorText({ error }: { error?: string }) {
  return error ? <Text style={styles.error}>{error}</Text> : null;
}

export const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#f7faf8", padding: 18 },
  shell: { flex: 1, backgroundColor: "#f7faf8" },
  header: { paddingHorizontal: 18, paddingTop: 10, paddingBottom: 12, backgroundColor: "white", borderBottomColor: "#dfe8e2", borderBottomWidth: 1 },
  content: { flex: 1, padding: 16 },
  eyebrow: { color: "#53645d", fontSize: 12, fontWeight: "800", textTransform: "uppercase", letterSpacing: 0 },
  title: { fontSize: 24, fontWeight: "800", color: "#10201b", marginTop: 3 },
  bottomNav: { minHeight: 64, flexDirection: "row", alignItems: "center", justifyContent: "space-around", borderTopColor: "#dfe8e2", borderTopWidth: 1, backgroundColor: "white", paddingHorizontal: 8, paddingBottom: 4 },
  tab: { minWidth: 58, paddingVertical: 9, paddingHorizontal: 8, borderRadius: 8, textAlign: "center" },
  tabActive: { backgroundColor: "#e8f3ed" },
  tabText: { color: "#53645d", fontSize: 12, fontWeight: "800", textAlign: "center" },
  tabTextActive: { color: "#326657" },
  card: { backgroundColor: "white", borderColor: "#dfe8e2", borderWidth: 1, borderRadius: 8, padding: 14, gap: 10 },
  label: { color: "#53645d", fontSize: 13, fontWeight: "700" },
  input: { height: 44, borderColor: "#ccd9d1", borderWidth: 1, borderRadius: 6, paddingHorizontal: 12, color: "#10201b", backgroundColor: "white" },
  button: { height: 44, borderRadius: 6, backgroundColor: "#326657", alignItems: "center", justifyContent: "center", paddingHorizontal: 16 },
  buttonText: { color: "white", fontWeight: "700" },
  error: { color: "#df6b57" },
  actionTile: { width: "48%", minHeight: 92, borderRadius: 8, padding: 12, borderWidth: 1, justifyContent: "space-between" },
  mossTile: { backgroundColor: "#e8f3ed", borderColor: "#b8d5c8" },
  amberTile: { backgroundColor: "#fff5df", borderColor: "#f6d48c" },
  coralTile: { backgroundColor: "#fff0ed", borderColor: "#efb4aa" },
  actionTitle: { color: "#10201b", fontSize: 15, fontWeight: "900" },
  actionSubtitle: { color: "#53645d", fontSize: 12, lineHeight: 17, marginTop: 8 }
});
