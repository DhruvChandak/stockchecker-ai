import AsyncStorage from "@react-native-async-storage/async-storage";
import { demoApi } from "./demoApi";

const API_URL = process.env.EXPO_PUBLIC_API_URL ?? "http://localhost:8080";
const DEMO_MODE = process.env.EXPO_PUBLIC_DEMO_MODE === "true";

export type Product = { id: string; name: string; barcode?: string; currentStock: number; defaultPurchasePrice: number; defaultSalesPrice: number };
export type Warehouse = { id: string; name: string };
export type Page<T> = { content: T[] };

export async function setToken(token: string) {
  await AsyncStorage.setItem("stockpilot.token", token);
}

export async function clearToken() {
  await AsyncStorage.removeItem("stockpilot.token");
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  if (DEMO_MODE) {
    return demoApi<T>(path, init);
  }
  const token = await AsyncStorage.getItem("stockpilot.token");
  const headers = new Headers(init.headers);
  if (!(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  const response = await fetch(`${API_URL}${path}`, { ...init, headers });
  if (!response.ok) {
    const body = await response.json().catch(() => ({ message: response.statusText }));
    if (response.status === 401) {
      await clearToken();
    }
    throw new Error(body.message ?? "Request failed");
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}
