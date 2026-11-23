import { getSession, setSession, Session, SessionUser } from "./session";

export type OrderSide = "BUY" | "SELL";
export type OrderType = "LIMIT" | "MARKET";
export type OrderStatus = "OPEN" | "PARTIALLY_FILLED" | "FILLED" | "CANCELLED" | "REJECTED";
export type AccountStatus = "ACTIVE" | "SUSPENDED";
export type DayOfWeek = "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";

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
  name: string;
  status: AccountStatus;
  referencePrice: Decimal;
  lastPrice: Decimal | null;
  bestBid: Decimal | null;
  bestAsk: Decimal | null;
}

export interface MarketStatus {
  open: boolean;
  enforced: boolean;
  openTime: string;
  closeTime: string;
  zone: string;
  tradingDays: DayOfWeek[];
  serverTime: string;
}

export interface Candle {
  start: string;
  open: Decimal;
  high: Decimal;
  low: Decimal;
  close: Decimal;
  volume: Decimal;
}

export interface SymbolStats {
  symbol: string;
  since: string;
  tradeCount: number;
  volume: Decimal;
  turnover: Decimal;
  averagePrice: Decimal | null;
  open: Decimal | null;
  high: Decimal | null;
  low: Decimal | null;
  last: Decimal | null;
  changePercent: Decimal | null;
}

export interface MyTrade {
  id: string;
  symbol: string;
  side: OrderSide;
  price: Decimal;
  quantity: Decimal;
  amount: Decimal;
  executedAt: string;
}

export interface TradeHistory {
  username: string;
  generatedAt: string;
  summary: {
    tradeCount: number;
    buyCount: number;
    sellCount: number;
    totalBought: Decimal;
    totalSold: Decimal;
    netCashFlow: Decimal;
  };
  trades: MyTrade[];
}

export interface AdminOverview {
  users: number;
  traders: number;
  suspendedUsers: number;
  symbols: number;
  suspendedSymbols: number;
  activeOrders: number;
  tradesLast24h: number;
  volumeLast24h: Decimal;
  turnoverLast24h: Decimal;
  marketOpen: boolean;
}

export interface AdminUser {
  username: string;
  displayName: string | null;
  email: string | null;
  role: "TRADER" | "ADMIN";
  status: AccountStatus;
  createdAt: string;
  lockedUntil: string | null;
  cashBalance: Decimal | null;
  activeOrders: number;
}

export interface ActivityLog {
  id: number;
  occurredAt: string;
  actor: string;
  action: string;
  details: string | null;
}

export interface PlaceOrderPayload {
  symbol: string;
  side: OrderSide;
  type: OrderType;
  price?: number;
  quantity: number;
}

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = getSession()?.token;
  let res: Response;
  try {
    res = await fetch(`${API_BASE}${path}`, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(init?.headers ?? {}),
      },
    });
  } catch {
    throw new Error("Serveur injoignable — le backend Spring tourne-t-il sur " + API_BASE + " ?");
  }
  if (res.status === 401 && token) {
    setSession(null); // jeton expire ou invalide: retour en mode visiteur
    throw new Error("Session expirée, reconnectez-vous");
  }
  if (!res.ok) {
    const body = await res.json().catch(() => ({ message: res.statusText }));
    const fallback = res.status === 403 ? "Accès refusé" : `Erreur ${res.status}`;
    throw new Error(body.message || fallback);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

const json = (method: string, body?: unknown): RequestInit => ({
  method,
  body: body === undefined ? undefined : JSON.stringify(body),
});

interface AuthResponse {
  token: string;
  expiresAt: string;
  user: SessionUser;
}

async function authenticate(path: string, body: unknown): Promise<Session> {
  const response = await request<AuthResponse>(path, json("POST", body));
  const session: Session = { token: response.token, expiresAt: response.expiresAt, user: response.user };
  setSession(session);
  return session;
}

export const api = {
  // Auth & Utilisateurs
  login: (username: string, password: string) => authenticate("/api/auth/login", { username, password }),
  register: (payload: { username: string; password: string; displayName?: string; email?: string }) =>
    authenticate("/api/auth/register", payload),
  /** Revoque le jeton cote serveur, puis oublie la session meme si le serveur est injoignable. */
  logout: async () => {
    try {
      if (getSession()) await request<void>("/api/auth/logout", json("POST"));
    } catch {
      // jeton deja expire ou serveur injoignable: on se deconnecte quand meme localement
    } finally {
      setSession(null);
    }
  },
  /** Change le mot de passe; le serveur invalide les autres jetons et renvoie un nouveau jeton. */
  changePassword: async (currentPassword: string, newPassword: string) => {
    const response = await request<AuthResponse>("/api/auth/password", json("PUT", { currentPassword, newPassword }));
    const session: Session = { token: response.token, expiresAt: response.expiresAt, user: response.user };
    setSession(session);
    return session;
  },
  me: () => request<SessionUser>("/api/auth/me"),
  updateProfile: (payload: { displayName: string; email: string }) =>
    request<SessionUser>("/api/auth/me", json("PUT", payload)),

  // Ordres & Wallet (trader)
  placeOrder: (order: PlaceOrderPayload) => request<PlaceOrderResponse>("/api/orders", json("POST", order)),
  cancelOrder: (orderId: string) => request<void>(`/api/orders/${encodeURIComponent(orderId)}`, json("DELETE")),
  myOrders: () => request<OrderResponse[]>("/api/orders/my"),
  wallet: () => request<WalletResponse>("/api/wallet"),
  history: () => request<TradeHistory>("/api/history/trades"),

  // Marche (public)
  marketStatus: () => request<MarketStatus>("/api/market/status"),
  symbols: () => request<MarketSummary[]>("/api/market/symbols"),
  orderBook: (symbol: string) => request<OrderBookResponse>(`/api/market/${encodeURIComponent(symbol)}/orderbook`),
  recentTrades: (symbol: string) => request<TradeResponse[]>(`/api/market/${encodeURIComponent(symbol)}/trades`),
  candles: (symbol: string, interval: string) =>
    request<Candle[]>(`/api/market/${encodeURIComponent(symbol)}/candles?interval=${interval}`),
  stats: (symbol: string) => request<SymbolStats>(`/api/market/${encodeURIComponent(symbol)}/stats`),

  // Administration
  admin: {
    overview: () => request<AdminOverview>("/api/admin/overview"),
    users: () => request<AdminUser[]>("/api/admin/users"),
    setUserStatus: (username: string, status: AccountStatus) =>
      request<AdminUser>(`/api/admin/users/${encodeURIComponent(username)}/status`, json("PATCH", { status })),
    setUserRole: (username: string, role: "TRADER" | "ADMIN") =>
      request<AdminUser>(`/api/admin/users/${encodeURIComponent(username)}/role`, json("PATCH", { role })),
    unlockUser: (username: string) =>
      request<AdminUser>(`/api/admin/users/${encodeURIComponent(username)}/unlock`, json("POST")),
    createSymbol: (payload: { symbol: string; name: string; referencePrice: number }) =>
      request<MarketSummary>("/api/admin/symbols", json("POST", payload)),
    setSymbolStatus: (symbol: string, status: AccountStatus) =>
      request<MarketSummary>(`/api/admin/symbols/${encodeURIComponent(symbol)}/status`, json("PATCH", { status })),
    updateMarketHours: (payload: {
      open: string;
      close: string;
      zone: string;
      tradingDays: DayOfWeek[];
      enforced: boolean;
    }) => request<MarketStatus>("/api/admin/market-hours", json("PUT", payload)),
    logs: (filter: { actor?: string; action?: string }) => {
      const params = new URLSearchParams();
      if (filter.actor) params.set("actor", filter.actor);
      if (filter.action) params.set("action", filter.action);
      const query = params.toString();
      return request<ActivityLog[]>(`/api/admin/logs${query ? `?${query}` : ""}`);
    },
  },
};

export { API_BASE };
