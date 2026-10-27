"use client";

import { MarketSummary, OrderBookResponse, SymbolStats, TradeResponse, toNumber } from "@/lib/api";
import { formatMoney, formatPercent, formatPrice, formatQty } from "@/lib/format";

export function MarketHeader({
  symbol,
  summary,
  book,
  trades,
  stats,
}: {
  symbol: string;
  summary: MarketSummary | undefined;
  book: OrderBookResponse | null;
  trades: TradeResponse[];
  stats: SymbolStats | null;
}) {
  const reference = toNumber(summary?.referencePrice);
  const last = trades[0] ? toNumber(trades[0].price) : toNumber(summary?.lastPrice ?? summary?.referencePrice);
  const change = ((last - reference) / reference) * 100;
  const bestBid = book?.bids[0] ? toNumber(book.bids[0].price) : NaN;
  const bestAsk = book?.asks[0] ? toNumber(book.asks[0].price) : NaN;

  const trend = change > 0 ? "text-bid-400" : change < 0 ? "text-ask-400" : "text-paper-300";

  return (
    <section className="border border-ink-700 px-4 py-3">
      <div className="flex flex-wrap items-end gap-x-8 gap-y-3">
        <div>
          <p className="text-xs text-paper-500">
            {symbol} · {summary?.name ?? ""}
            {summary?.status === "SUSPENDED" && <span className="ml-2 text-ask-400">cotation suspendue</span>}
          </p>
          <p className="flex items-baseline gap-3">
            <span className={`num text-3xl font-medium ${trend}`}>{formatPrice(last)}</span>
            <span className={`num text-sm ${trend}`}>{formatPercent(change)}</span>
          </p>
        </div>
        <Stat label="Meilleur achat" value={formatPrice(bestBid)} tone="text-bid-400" />
        <Stat label="Meilleure vente" value={formatPrice(bestAsk)} tone="text-ask-400" />
        <Stat label="Spread" value={formatPrice(bestAsk - bestBid)} />
        <Stat label="Prix de référence" value={formatPrice(reference)} />
      </div>

      <dl className="mt-3 grid grid-cols-2 gap-x-6 gap-y-2 border-t border-ink-700 pt-3 sm:grid-cols-3 lg:grid-cols-6">
        <Stat label="Volume 24 h" value={formatQty(stats?.volume)} />
        <Stat label="Montant échangé 24 h" value={formatMoney(stats?.turnover)} />
        <Stat label="Prix moyen (VWAP)" value={formatPrice(stats?.averagePrice)} />
        <Stat label="Plus haut 24 h" value={formatPrice(stats?.high)} />
        <Stat label="Plus bas 24 h" value={formatPrice(stats?.low)} />
        <Stat label="Exécutions 24 h" value={stats ? String(stats.tradeCount) : "—"} />
      </dl>
    </section>
  );
}

function Stat({ label, value, tone = "text-paper-100" }: { label: string; value: string; tone?: string }) {
  return (
    <div>
      <dt className="text-xs text-paper-500">{label}</dt>
      <dd className={`num text-sm ${tone}`}>{value}</dd>
    </div>
  );
}
