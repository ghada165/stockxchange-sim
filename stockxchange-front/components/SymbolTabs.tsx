"use client";

import { MarketSummary, toNumber } from "@/lib/api";
import { formatPercent, formatPrice } from "@/lib/format";

export function SymbolTabs({
  symbols,
  symbol,
  onSymbolChange,
}: {
  symbols: MarketSummary[];
  symbol: string;
  onSymbolChange: (symbol: string) => void;
}) {
  return (
    <nav aria-label="Symboles" className="flex overflow-x-auto border-b border-ink-700">
      {symbols.map((s) => {
        const last = toNumber(s.lastPrice ?? s.referencePrice);
        const change = ((last - toNumber(s.referencePrice)) / toNumber(s.referencePrice)) * 100;
        const active = s.symbol === symbol;
        const suspended = s.status === "SUSPENDED";
        return (
          <button
            key={s.symbol}
            type="button"
            onClick={() => onSymbolChange(s.symbol)}
            aria-current={active ? "true" : undefined}
            title={s.name}
            className={`flex min-w-36 shrink-0 flex-col items-start border-r border-ink-700 px-4 py-2 text-left transition-colors ${
              active ? "bg-ink-800 shadow-[inset_0_-2px_0_var(--gold-500)]" : "hover:bg-ink-900"
            }`}
          >
            <span className="flex items-center gap-2 text-xs font-medium text-paper-300">
              {s.symbol}
              {suspended && <span className="border border-ask-500/60 px-1 text-[10px] text-ask-400">Suspendu</span>}
            </span>
            <span className="flex items-baseline gap-2">
              <span className="num text-sm text-paper-100">{formatPrice(last)}</span>
              <span
                className={`num text-xs ${change > 0 ? "text-bid-400" : change < 0 ? "text-ask-400" : "text-paper-500"}`}
              >
                {formatPercent(change)}
              </span>
            </span>
          </button>
        );
      })}
    </nav>
  );
}
