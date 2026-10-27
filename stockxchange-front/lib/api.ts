export type OrderSide = "BUY" | "SELL";
export type OrderType = "LIMIT" | "MARKET";
export type OrderStatus = "OPEN" | "PARTIALLY_FILLED" | "FILLED" | "CANCELLED" | "REJECTED";

/** Le backend serialise les BigDecimal en nombres JSON. */
export type Decimal = number | string;

export const toNumber = (value: Decimal | null | undefined): number =>
  value === null || value === undefined ? NaN : Number(value);

export interface OrderResponse {
  id: string;
  symbol: string;
  side: OrderSide;
  type: OrderType;
  price: Decimal | null;
  quantity: Decimal;
  remainingQuantity: Decimal;
  status: OrderStatus;
  createdAt: string;
}

export interface TradeResponse {
  id: string;
  symbol: string;
  price: Decimal;
  quantity: Decimal;
  executedAt: string;
}

export interface PlaceOrderResponse {
  order: OrderResponse;
  trades: TradeResponse[];
}

export interface OrderBookLevel {
  price: Decimal;
  totalQuantity: Decimal;
  orderCount: number;
}

export interface OrderBookResponse {
  symbol: string;
  bids: OrderBookLevel[];
  asks: OrderBookLevel[];
}

export interface PositionResponse {
  symbol: string;
  quantity: Decimal;
  lockedQuantity: Decimal;
  availableQuantity: Decimal;
}

export interface WalletResponse {
  balance: Decimal;
  lockedBalance: Decimal;
  availableBalance: Decimal;
  positions: PositionResponse[];
}

export interface MarketSummary {
  symbol: string;
  referencePrice: Decimal;
  lastPrice: Decimal | null;
  bestBid: Decimal | null;
  bestAsk: Decimal | null;
}

export interface PlaceOrderPayload {
  symbol: string;
  side: OrderSide;
  type: OrderType;
  price?: number;
  quantity: number;
}

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

async function request<T>(path: string, userId: string | null, init?: RequestInit): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`${API_BASE}${path}`, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        ...(userId ? { "X-User-Id": userId } : {}),
        ...(init?.headers ?? {}),
      },
    });
  } catch {
    throw new Error("Serveur injoignable — le backend Spring tourne-t-il sur " + API_BASE + " ?");
  }
  if (!res.ok) {
    const body = await res.json().catch(() => ({ message: res.statusText }));
    throw new Error(body.message ?? `Erreur ${res.status}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export const api = {
  placeOrder: (userId: string, order: PlaceOrderPayload) =>
    request<PlaceOrderResponse>("/api/orders", userId, {
      method: "POST",
      body: JSON.stringify(order),
    }),

  cancelOrder: (userId: string, orderId: string) =>
    request<void>(`/api/orders/${encodeURIComponent(orderId)}`, userId, { method: "DELETE" }),

  myOrders: (userId: string) => request<OrderResponse[]>("/api/orders/my", userId),

  wallet: (userId: string) => request<WalletResponse>("/api/wallet", userId),

  symbols: () => request<MarketSummary[]>("/api/market/symbols", null),

  orderBook: (symbol: string) =>
    request<OrderBookResponse>(`/api/market/${encodeURIComponent(symbol)}/orderbook`, null),

  recentTrades: (symbol: string) =>
    request<TradeResponse[]>(`/api/market/${encodeURIComponent(symbol)}/trades`, null),
};

export { API_BASE };
