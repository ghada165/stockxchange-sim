"use client";

import { OrderBookLevel, OrderBookResponse } from "@/lib/api";

function Levels({ levels, side }: { levels: OrderBookLevel[]; side: "bid" | "ask" }) {
  const color = side === "bid" ? "text-bid-400" : "text-ask-400";
  const barColor = side === "bid" ? "bg-bid-500/15" : "bg-ask-500/15";
  const maxQty = Math.max(...levels.map((l) => parseFloat(l.totalQuantity)), 1);

  if (levels.length === 0) {
    return <p className="py-6 text-center text-xs text-paper-500">Aucun ordre</p>;
  }

  return (
    <div className="flex flex-col">
      {levels.map((level) => {
        const qty = parseFloat(level.totalQuantity);
        const widthPct = Math.max((qty / maxQty) * 100, 4);
        return (
          <div key={level.price} className="relative flex justify-between px-3 py-1 text-sm">
            <div
              className={`absolute inset-y-0 ${side === "bid" ? "right-0" : "left-0"} ${barColor}`}
              style={{ width: `${widthPct}%` }}
              aria-hidden
            />
            <span className={`num relative ${color}`}>{parseFloat(level.price).toFixed(2)}</span>
            <span className="num relative text-paper-300">{level.totalQuantity}</span>
          </div>
        );
      })}
    </div>
  );
}

export function OrderBookPanel({ book, symbol }: { book: OrderBookResponse | null; symbol: string }) {
  const bids = book?.bids ?? [];
  const asks = [...(book?.asks ?? [])].reverse();

  const bestBid = bids[0] ? parseFloat(bids[0].price) : null;
  const bestAsk = book?.asks[0] ? parseFloat(book.asks[0].price) : null;
  const spread = bestBid !== null && bestAsk !== null ? (bestAsk - bestBid).toFixed(2) : "—";

  return (
    <section className="border border-ink-700">
      <header className="flex items-center justify-between border-b border-ink-700 px-3 py-2">
        <h2 className="text-xs tracking-wide text-paper-500">Carnet d&apos;ordres — {symbol}</h2>
        <span className="num text-xs text-paper-500">spread {spread}</span>
      </header>
      <div className="grid grid-cols-2 divide-x divide-ink-700">
        <div>
          <div className="flex justify-between px-3 py-1 text-xs text-paper-500">
            <span>Vente</span>
            <span>Qte</span>
          </div>
          <Levels levels={asks} side="ask" />
        </div>
        <div>
          <div className="flex justify-between px-3 py-1 text-xs text-paper-500">
            <span>Achat</span>
            <span>Qte</span>
          </div>
          <Levels levels={bids} side="bid" />
        </div>
      </div>
    </section>
  );
}
