"use client";

import { useState } from "react";
import { api, OrderSide, OrderType } from "@/lib/api";

export function OrderForm({
  userId,
  symbol,
  onPlaced,
}: {
  userId: string;
  symbol: string;
  onPlaced: (message: string, isError?: boolean) => void;
}) {
  const [side, setSide] = useState<OrderSide>("BUY");
  const [type, setType] = useState<OrderType>("LIMIT");
  const [price, setPrice] = useState("");
  const [quantity, setQuantity] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      const response = await api.placeOrder(userId, {
        symbol,
        side,
        type,
        price: type === "LIMIT" ? parseFloat(price) : undefined,
        quantity: parseFloat(quantity),
      });
      const filled = response.trades.length;
      onPlaced(
        filled > 0
          ? `Ordre ${response.order.status.toLowerCase()} — ${filled} execution(s)`
          : `Ordre place — en attente dans le carnet`
      );
      setQuantity("");
      if (type === "LIMIT") setPrice("");
    } catch (err) {
      onPlaced(err instanceof Error ? err.message : "Erreur inconnue", true);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="border border-ink-700">
      <header className="border-b border-ink-700 px-3 py-2">
        <h2 className="text-xs tracking-wide text-paper-500">Passer un ordre</h2>
      </header>

      <form onSubmit={handleSubmit} className="flex flex-col gap-3 p-3">
        <div className="grid grid-cols-2 gap-2">
          <button
            type="button"
            onClick={() => setSide("BUY")}
            className={`border py-1.5 text-sm transition-colors ${
              side === "BUY"
                ? "border-bid-500 bg-bid-500/10 text-bid-400"
                : "border-ink-600 text-paper-500 hover:border-ink-500"
            }`}
          >
            Achat
          </button>
          <button
            type="button"
            onClick={() => setSide("SELL")}
            className={`border py-1.5 text-sm transition-colors ${
              side === "SELL"
                ? "border-ask-500 bg-ask-500/10 text-ask-400"
                : "border-ink-600 text-paper-500 hover:border-ink-500"
            }`}
          >
            Vente
          </button>
        </div>

        <label className="flex flex-col gap-1 text-xs text-paper-500">
          Type d&apos;ordre
          <select
            value={type}
            onChange={(e) => setType(e.target.value as OrderType)}
            className="border border-ink-600 bg-ink-800 px-2 py-1.5 text-sm text-paper-100"
          >
            <option value="LIMIT">Limite</option>
            <option value="MARKET">Marche</option>
          </select>
        </label>

        {type === "LIMIT" && (
          <label className="flex flex-col gap-1 text-xs text-paper-500">
            Prix
            <input
              required
              type="number"
              step="0.01"
              min="0.0001"
              value={price}
              onChange={(e) => setPrice(e.target.value)}
              className="num border border-ink-600 bg-ink-800 px-2 py-1.5 text-sm text-paper-100 focus-visible:border-gold-500"
              placeholder="150.00"
            />
          </label>
        )}

        <label className="flex flex-col gap-1 text-xs text-paper-500">
          Quantite
          <input
            required
            type="number"
            step="0.00000001"
            min="0.00000001"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            className="num border border-ink-600 bg-ink-800 px-2 py-1.5 text-sm text-paper-100 focus-visible:border-gold-500"
            placeholder="10"
          />
        </label>

        <button
          type="submit"
          disabled={submitting}
          className="mt-1 bg-gold-500 py-2 text-sm font-medium text-ink-950 transition-opacity hover:opacity-90 disabled:opacity-50"
        >
          {submitting ? "Envoi..." : `${side === "BUY" ? "Acheter" : "Vendre"} ${symbol}`}
        </button>
      </form>
    </section>
  );
}
