"use client";

import { TradeResponse, toNumber } from "@/lib/api";
import { formatPrice, formatQty, formatTime } from "@/lib/format";

export function TradeFeed({ trades }: { trades: TradeResponse[] }) {
  return (
    <section className="flex flex-col border border-ink-700">
      <header className="border-b border-ink-700 px-3 py-2">
        <h2 className="text-xs tracking-wide text-paper-500">Transactions</h2>
      </header>

      <div className="grid grid-cols-3 px-3 py-1 text-xs text-paper-500">
        <span>Prix</span>
        <span className="text-right">Quantité</span>
        <span className="text-right">Heure</span>
      </div>

      {trades.length === 0 ? (
        <p className="p-3 text-sm text-paper-500">En attente de la première exécution…</p>
      ) : (
        <ul className="max-h-[26rem] overflow-y-auto">
          {trades.map((trade, i) => {
            const previous = trades[i + 1];
            const direction = previous ? Math.sign(toNumber(trade.price) - toNumber(previous.price)) : 0;
            const tone = direction > 0 ? "text-bid-400" : direction < 0 ? "text-ask-400" : "text-paper-100";
            return (
              <li key={trade.id} className="grid grid-cols-3 px-3 py-1 text-sm">
                <span className={`num ${tone}`}>
                  {formatPrice(trade.price)}
                  <span aria-hidden className="ml-1 text-xs">
                    {direction > 0 ? "▲" : direction < 0 ? "▼" : ""}
                  </span>
                </span>
                <span className="num text-right text-paper-300">{formatQty(trade.quantity)}</span>
                <time className="num text-right text-xs text-paper-500">{formatTime(trade.executedAt)}</time>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
