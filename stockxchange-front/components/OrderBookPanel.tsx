"use client";

import { OrderBookLevel, OrderBookResponse, toNumber } from "@/lib/api";
import { formatPrice, formatQty } from "@/lib/format";

const DEPTH = 8;

function Levels({
  levels,
  side,
  maxQty,
  onPickPrice,
}: {
  levels: OrderBookLevel[];
  side: "bid" | "ask";
  maxQty: number;
  onPickPrice: (price: number) => void;
}) {
  const color = side === "bid" ? "text-bid-400" : "text-ask-400";
  const barColor = side === "bid" ? "bg-bid-500/15" : "bg-ask-500/15";

  if (levels.length === 0) {
    return <p className="py-4 text-center text-xs text-paper-500">Aucun ordre</p>;
  }

  return (
    <ul>
      {levels.map((level) => {
        const qty = toNumber(level.totalQuantity);
        const widthPct = Math.max((qty / maxQty) * 100, 3);
        return (
          <li key={String(level.price)}>
            <button
              type="button"
              onClick={() => onPickPrice(toNumber(level.price))}
              title="Reprendre ce prix dans le formulaire"
              className="relative grid w-full grid-cols-3 px-3 py-1 text-sm hover:bg-ink-800"
            >
              <span className={`absolute inset-y-0 right-0 ${barColor}`} style={{ width: `${widthPct}%` }} aria-hidden />
              <span className={`num relative text-left ${color}`}>{formatPrice(level.price)}</span>
              <span className="num relative text-right text-paper-300">{formatQty(level.totalQuantity)}</span>
              <span className="num relative text-right text-paper-500">{level.orderCount}</span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}

export function OrderBookPanel({
  book,
  symbol,
  onPickPrice,
}: {
  book: OrderBookResponse | null;
  symbol: string;
  onPickPrice: (price: number) => void;
}) {
  const bids = (book?.bids ?? []).slice(0, DEPTH);
  const asksNearFirst = (book?.asks ?? []).slice(0, DEPTH);
  const asks = [...asksNearFirst].reverse(); // meilleure vente en bas, au contact du spread
  const maxQty = Math.max(...[...bids, ...asksNearFirst].map((l) => toNumber(l.totalQuantity)), 1);

  const bestBid = bids[0] ? toNumber(bids[0].price) : NaN;
  const bestAsk = asksNearFirst[0] ? toNumber(asksNearFirst[0].price) : NaN;

  return (
    <section className="border border-ink-700">
      <header className="flex items-center justify-between border-b border-ink-700 px-3 py-2">
        <h2 className="text-xs tracking-wide text-paper-500">Carnet d&apos;ordres — {symbol}</h2>
        <span className="text-xs text-paper-500">clic = reprendre le prix</span>
      </header>

      <div className="grid grid-cols-3 px-3 py-1 text-xs text-paper-500">
        <span>Prix</span>
        <span className="text-right">Quantité</span>
        <span className="text-right">Ordres</span>
      </div>

      {!book ? (
        <p className="py-6 text-center text-xs text-paper-500">Chargement…</p>
      ) : (
        <>
          <Levels levels={asks} side="ask" maxQty={maxQty} onPickPrice={onPickPrice} />
          <div className="num flex justify-between border-y border-ink-700 bg-ink-900 px-3 py-1.5 text-xs text-paper-500">
            <span>spread</span>
            <span className="text-paper-300">{formatPrice(bestAsk - bestBid)}</span>
          </div>
          <Levels levels={bids} side="bid" maxQty={maxQty} onPickPrice={onPickPrice} />
        </>
      )}
    </section>
  );
}
