"use client";

import { TradeResponse } from "@/lib/api";

export function TradeFeed({ trades }: { trades: TradeResponse[] }) {
  return (
    <section className="border border-ink-700">
      <header className="border-b border-ink-700 px-3 py-2">
        <h2 className="text-xs tracking-wide text-paper-500">Transactions en direct</h2>
      </header>

      {trades.length === 0 ? (
        <p className="p-3 text-sm text-paper-500">En attente de la premiere execution...</p>
      ) : (
        <ul className="divide-y divide-ink-700">
          {trades.map((trade) => (
            <li key={trade.id} className="flex items-center justify-between px-3 py-2 text-sm">
              <span className="num text-paper-100">{parseFloat(trade.price).toFixed(2)}</span>
              <span className="num text-paper-500">{trade.quantity}</span>
              <time className="num text-xs text-paper-500">
                {new Date(trade.executedAt).toLocaleTimeString("fr-FR")}
              </time>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
