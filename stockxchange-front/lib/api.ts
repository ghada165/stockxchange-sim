export type OrderSide = "BUY" | "SELL";
export type OrderType = "LIMIT" | "MARKET";
export type OrderStatus = "OPEN" | "PARTIALLY_FILLED" | "FILLED" | "CANCELLED" | "REJECTED";

export interface OrderResponse {
  id: string;
  symbol: string;
  side: OrderSide;
  type: OrderType;
  price: string | null;
  quantity: string;
  remainingQuantity: string;
  status: OrderStatus;
  createdAt: string;
}

export interface TradeResponse {
  id: string;
  symbol: string;
  price: string;
  quantity: string;
  executedAt: string;
}

export interface PlaceOrderResponse {
  order: OrderResponse;
  trades: TradeResponse[];
}

export interface OrderBookLevel {
  price: string;
  totalQuantity: string;
  orderCount: number;
}

export interface OrderBookResponse {
  symbol: string;
  bids: OrderBookLevel[];
  asks: OrderBookLevel[];
}

export interface WalletResponse {
  balance: string;
  lockedBalance: string;
  availableBalance: string;
  positions: Record<string, string>;
}

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

async function request<T>(path: string, userId: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      "X-User-Id": userId,
      ...(init?.headers ?? {}),
    },
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ message: res.statusText }));
    throw new Error(body.message ?? `Erreur ${res.status}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export const api = {
  placeOrder: (userId: string, order: {
    symbol: string;
    side: OrderSide;
    type: OrderType;
    price?: number;
    quantity: number;
  }) =>
    request<PlaceOrderResponse>("/api/orders", userId, {
      method: "POST",
      body: JSON.stringify(order),
    }),

  cancelOrder: (userId: string, symbol: string, orderId: string) =>
    request<void>(`/api/orders/${orderId}?symbol=${symbol}`, userId, { method: "DELETE" }),

  myOrders: (userId: string) => request<OrderResponse[]>("/api/orders/my", userId),

  wallet: (userId: string) => request<WalletResponse>("/api/wallet", userId),

  orderBook: (userId: string, symbol: string) =>
    request<OrderBookResponse>(`/api/market/${symbol}/orderbook`, userId),

  recentTrades: (userId: string, symbol: string) =>
    request<TradeResponse[]>(`/api/market/${symbol}/trades`, userId),
};

export { API_BASE };
