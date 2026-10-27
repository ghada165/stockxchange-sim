"use client";

import { MarketSummary, OrderBookResponse, TradeResponse, toNumber } from "@/lib/api";
import { formatPercent, formatPrice } from "@/lib/format";

export function MarketHeader({
  symbol,
  summary,
  book,
  trades,
}: {
  symbol: string;
  summary: MarketSummary | undefined;
  book: OrderBookResponse | null;
  trades: TradeResponse[];
}) {
  const reference = toNumber(summary?.referencePrice);
  const last = trades[0] ? toNumber(trades[0].price) : toNumber(summary?.lastPrice ?? summary?.referencePrice);
  const change = ((last - reference) / reference) * 100;
  const bestBid = book?.bids[0] ? toNumber(book.bids[0].price) : NaN;
  const bestAsk = book?.asks[0] ? toNumber(book.asks[0].price) : NaN;
  const spread = bestAsk - bestBid;
  const volume = trades.reduce((sum, t) => sum + toNumber(t.quantity), 0);

  const trend = change > 0 ? "text-bid-400" : change < 0 ? "text-ask-400" : "text-paper-300";

  return (
    <section className="flex flex-wrap items-end gap-x-8 gap-y-3 border border-ink-700 px-4 py-3">
      <div>
        <p className="text-xs text-paper-500">{symbol} · dernier prix</p>
        <p className="flex items-baseline gap-3">
          <span className={`num text-3xl font-medium ${trend}`}>{formatPrice(last)}</span>
          <span className={`num text-sm ${trend}`}>{formatPercent(change)}</span>
        </p>
      </div>
      <Stat label="Meilleur achat" value={formatPrice(bestBid)} tone="text-bid-400" />
      <Stat label="Meilleure vente" value={formatPrice(bestAsk)} tone="text-ask-400" />
      <Stat label="Spread" value={formatPrice(spread)} />
      <Stat label="Réf. ouverture" value={formatPrice(reference)} />
      <Stat label="Volume récent" value={String(volume)} />
    </section>
  );
}

function Stat({ label, value, tone = "text-paper-100" }: { label: string; value: string; tone?: string }) {
  return (
    <div>
      <p className="text-xs text-paper-500">{label}</p>
      <p className={`num text-sm ${tone}`}>{value}</p>
    </div>
  );
}
