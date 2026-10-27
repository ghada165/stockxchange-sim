"use client";

import { useState } from "react";
import { OrderResponse, OrderStatus, toNumber } from "@/lib/api";
import { formatPrice, formatQty, formatTime } from "@/lib/format";

const STATUS_LABEL: Record<OrderStatus, string> = {
  OPEN: "En attente",
  PARTIALLY_FILLED: "Partiel",
  FILLED: "Exécuté",
  CANCELLED: "Annulé",
  REJECTED: "Rejeté",
};

const STATUS_TONE: Record<OrderStatus, string> = {
  OPEN: "text-gold-400",
  PARTIALLY_FILLED: "text-gold-400",
  FILLED: "text-bid-400",
  CANCELLED: "text-paper-500",
  REJECTED: "text-ask-400",
};

const isActive = (o: OrderResponse) => o.status === "OPEN" || o.status === "PARTIALLY_FILLED";

export function MyOrdersPanel({
  orders,
  onCancel,
}: {
  orders: OrderResponse[];
  onCancel: (order: OrderResponse) => Promise<void>;
}) {
  const [filter, setFilter] = useState<"active" | "all">("active");
  const [cancelling, setCancelling] = useState<string | null>(null);
  const visible = filter === "active" ? orders.filter(isActive) : orders;
  const activeCount = orders.filter(isActive).length;

  async function cancel(order: OrderResponse) {
    setCancelling(order.id);
    try {
      await onCancel(order);
    } finally {
      setCancelling(null);
    }
  }

  return (
    <section className="border border-ink-700">
      <header className="flex items-center justify-between border-b border-ink-700 px-3 py-2">
        <h2 className="text-xs tracking-wide text-paper-500">Mes ordres</h2>
        <div className="flex gap-1 text-xs">
          {(["active", "all"] as const).map((f) => (
            <button
              key={f}
              type="button"
              onClick={() => setFilter(f)}
              aria-pressed={filter === f}
              className={`border px-2 py-0.5 ${
                filter === f ? "border-paper-300 text-paper-100" : "border-ink-600 text-paper-500 hover:border-ink-500"
              }`}
            >
              {f === "active" ? `Actifs (${activeCount})` : `Historique (${orders.length})`}
            </button>
          ))}
        </div>
      </header>

      {visible.length === 0 ? (
        <p className="p-3 text-sm text-paper-500">
          {filter === "active" ? "Aucun ordre en attente." : "Aucun ordre pour l'instant."}
        </p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[40rem] text-sm">
            <thead>
              <tr className="text-xs text-paper-500">
                <th className="px-3 py-1 text-left font-normal">Heure</th>
                <th className="px-3 py-1 text-left font-normal">Titre</th>
                <th className="px-3 py-1 text-left font-normal">Sens</th>
                <th className="px-3 py-1 text-left font-normal">Type</th>
                <th className="px-3 py-1 text-right font-normal">Prix</th>
                <th className="px-3 py-1 text-right font-normal">Exécuté / Qté</th>
                <th className="px-3 py-1 text-left font-normal">Statut</th>
                <th className="px-3 py-1" />
              </tr>
            </thead>
            <tbody className="divide-y divide-ink-700">
              {visible.map((o) => {
                const filled = toNumber(o.quantity) - toNumber(o.remainingQuantity);
                return (
                  <tr key={o.id}>
                    <td className="num px-3 py-1.5 text-xs text-paper-500">{formatTime(o.createdAt)}</td>
                    <td className="px-3 py-1.5 text-paper-300">{o.symbol}</td>
                    <td className={`px-3 py-1.5 ${o.side === "BUY" ? "text-bid-400" : "text-ask-400"}`}>
                      {o.side === "BUY" ? "Achat" : "Vente"}
                    </td>
                    <td className="px-3 py-1.5 text-paper-500">{o.type === "LIMIT" ? "Limite" : "Marché"}</td>
                    <td className="num px-3 py-1.5 text-right text-paper-100">
                      {o.price === null ? "—" : formatPrice(o.price)}
                    </td>
                    <td className="num px-3 py-1.5 text-right text-paper-300">
                      {formatQty(filled)} / {formatQty(o.quantity)}
                    </td>
                    <td className={`px-3 py-1.5 text-xs ${STATUS_TONE[o.status]}`}>{STATUS_LABEL[o.status]}</td>
                    <td className="px-3 py-1.5 text-right">
                      {isActive(o) && (
                        <button
                          type="button"
                          onClick={() => cancel(o)}
                          disabled={cancelling === o.id}
                          className="border border-ink-600 px-2 py-0.5 text-xs text-paper-300 hover:border-ask-500 hover:text-ask-400 disabled:opacity-50"
                        >
                          {cancelling === o.id ? "…" : "Annuler"}
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
