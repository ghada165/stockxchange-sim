"use client";

import { useState } from "react";
import { api, OrderBookResponse, OrderSide, OrderType, toNumber, WalletResponse } from "@/lib/api";
import { formatMoney, formatQty } from "@/lib/format";

interface OrderFormProps {
  userId: string;
  symbol: string;
  book: OrderBookResponse | null;
  wallet: WalletResponse | null;
  /** Prix choisi en cliquant sur un niveau du carnet (nonce pour re-declencher le meme prix). */
  pickedPrice: { price: number; nonce: number } | null;
  onPlaced: (message: string, isError?: boolean) => void;
}

const QUICK_QTY = [1, 5, 10, 25];

/** Cout estime d'un ordre MARKET en consommant le carnet oppose niveau par niveau. */
function estimateMarket(book: OrderBookResponse | null, side: OrderSide, qty: number) {
  const levels = side === "BUY" ? book?.asks ?? [] : book?.bids ?? [];
  let remaining = qty;
  let total = 0;
  for (const level of levels) {
    if (remaining <= 0) break;
    const take = Math.min(remaining, toNumber(level.totalQuantity));
    total += take * toNumber(level.price);
    remaining -= take;
  }
  return { total, filled: qty - remaining, shortfall: remaining };
}

export function OrderForm({ userId, symbol, book, wallet, pickedPrice, onPlaced }: OrderFormProps) {
  const [side, setSide] = useState<OrderSide>("BUY");
  const [type, setType] = useState<OrderType>("LIMIT");
  const [price, setPrice] = useState("");
  const [quantity, setQuantity] = useState("");
  const [submitting, setSubmitting] = useState(false);

  // Ajustement d'etat pendant le rendu quand une prop change (pattern recommande
  // par React plutot qu'un useEffect): clic sur le carnet, ou changement de symbole.
  const [lastPick, setLastPick] = useState(pickedPrice);
  const [lastSymbol, setLastSymbol] = useState(symbol);
  if (pickedPrice !== lastPick) {
    setLastPick(pickedPrice);
    if (pickedPrice) {
      setType("LIMIT");
      setPrice(pickedPrice.price.toFixed(2));
    }
  }
  if (symbol !== lastSymbol) {
    setLastSymbol(symbol);
    setPrice("");
  }

  const qty = Number(quantity);
  const position = wallet?.positions.find((p) => p.symbol === symbol);
  const availableCash = toNumber(wallet?.availableBalance);
  const availableShares = toNumber(position?.availableQuantity);

  const market = type === "MARKET" && qty > 0 ? estimateMarket(book, side, qty) : null;
  const total = type === "LIMIT" ? qty * Number(price) : market?.total ?? NaN;

  let warning: string | null = null;
  if (qty > 0) {
    if (side === "BUY" && Number.isFinite(total) && total > availableCash) warning = "Solde disponible insuffisant";
    if (side === "SELL" && qty > availableShares) warning = `Vous n'avez que ${formatQty(availableShares)} ${symbol} disponibles`;
    if (market && market.filled === 0) warning = "Aucune contrepartie dans le carnet";
    else if (market && market.shortfall > 0) warning = `Liquidité insuffisante: ${formatQty(market.shortfall)} ne seront pas exécutées`;
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      const response = await api.placeOrder(userId, {
        symbol,
        side,
        type,
        price: type === "LIMIT" ? Number(price) : undefined,
        quantity: qty,
      });
      const executed = response.trades.reduce((sum, t) => sum + toNumber(t.quantity), 0);
      const { status } = response.order;
      let message: string;
      if (status === "FILLED") message = `Ordre exécuté: ${formatQty(executed)} ${symbol}`;
      else if (status === "PARTIALLY_FILLED") message = `Exécuté en partie (${formatQty(executed)}), le reste attend dans le carnet`;
      else if (status === "CANCELLED")
        message =
          executed > 0
            ? `Exécuté en partie (${formatQty(executed)}), reliquat annulé`
            : "Ordre annulé: il aurait croisé l'un de vos propres ordres";
      else message = "Ordre placé, en attente dans le carnet";
      onPlaced(message, status === "CANCELLED" && executed === 0);
      setQuantity("");
    } catch (err) {
      onPlaced(err instanceof Error ? err.message : "Erreur inconnue", true);
    } finally {
      setSubmitting(false);
    }
  }

  const isBuy = side === "BUY";

  return (
    <section className="border border-ink-700">
      <header className="border-b border-ink-700 px-3 py-2">
        <h2 className="text-xs tracking-wide text-paper-500">Passer un ordre — {symbol}</h2>
      </header>

      <form onSubmit={handleSubmit} className="flex flex-col gap-3 p-3">
        <div className="grid grid-cols-2 gap-2" role="group" aria-label="Sens">
          {(["BUY", "SELL"] as const).map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => setSide(s)}
              aria-pressed={side === s}
              className={`border py-1.5 text-sm transition-colors ${
                side === s
                  ? s === "BUY"
                    ? "border-bid-500 bg-bid-500/10 text-bid-400"
                    : "border-ask-500 bg-ask-500/10 text-ask-400"
                  : "border-ink-600 text-paper-500 hover:border-ink-500"
              }`}
            >
              {s === "BUY" ? "Achat" : "Vente"}
            </button>
          ))}
        </div>

        <div className="grid grid-cols-2 gap-2" role="group" aria-label="Type d'ordre">
          {(["LIMIT", "MARKET"] as const).map((t) => (
            <button
              key={t}
              type="button"
              onClick={() => setType(t)}
              aria-pressed={type === t}
              className={`border py-1 text-xs transition-colors ${
                type === t ? "border-paper-300 text-paper-100" : "border-ink-600 text-paper-500 hover:border-ink-500"
              }`}
            >
              {t === "LIMIT" ? "À cours limité" : "Au marché"}
            </button>
          ))}
        </div>

        {type === "LIMIT" ? (
          <label className="flex flex-col gap-1 text-xs text-paper-500">
            Prix limite ($)
            <input
              required
              type="number"
              inputMode="decimal"
              step="0.01"
              min="0.01"
              value={price}
              onChange={(e) => setPrice(e.target.value)}
              className="num border border-ink-600 bg-ink-800 px-2 py-1.5 text-sm text-paper-100 focus-visible:border-gold-500"
              placeholder="ex. 230.00"
            />
          </label>
        ) : (
          <p className="text-xs text-paper-500">
            Exécution immédiate au meilleur prix disponible. Le prix est plafonné au pire niveau du carnet au moment
            de l&apos;envoi; ce qui ne peut pas être exécuté est annulé.
          </p>
        )}

        <label className="flex flex-col gap-1 text-xs text-paper-500">
          Quantité (actions)
          <input
            required
            type="number"
            inputMode="numeric"
            step="1"
            min="1"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            className="num border border-ink-600 bg-ink-800 px-2 py-1.5 text-sm text-paper-100 focus-visible:border-gold-500"
            placeholder="ex. 10"
          />
        </label>
        <div className="flex gap-1">
          {QUICK_QTY.map((q) => (
            <button
              key={q}
              type="button"
              onClick={() => setQuantity(String(q))}
              className="num flex-1 border border-ink-600 py-0.5 text-xs text-paper-500 hover:border-ink-500 hover:text-paper-300"
            >
              {q}
            </button>
          ))}
        </div>

        <dl className="flex flex-col gap-1 border-t border-ink-700 pt-2 text-xs">
          <div className="flex justify-between">
            <dt className="text-paper-500">{type === "MARKET" ? "Total estimé" : "Total"}</dt>
            <dd className="num text-paper-100">{qty > 0 ? formatMoney(total) : "—"}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-paper-500">{isBuy ? "Cash disponible" : `${symbol} disponibles`}</dt>
            <dd className="num text-paper-300">{isBuy ? formatMoney(availableCash) : formatQty(availableShares)}</dd>
          </div>
        </dl>

        {warning && (
          <p role="alert" className="text-xs text-ask-400">
            {warning}
          </p>
        )}

        <button
          type="submit"
          disabled={submitting}
          className={`mt-1 py-2 text-sm font-medium text-ink-950 transition-opacity hover:opacity-90 disabled:opacity-50 ${
            isBuy ? "bg-bid-500" : "bg-ask-500"
          }`}
        >
          {submitting ? "Envoi…" : `${isBuy ? "Acheter" : "Vendre"} ${symbol}`}
        </button>
      </form>
    </section>
  );
}
