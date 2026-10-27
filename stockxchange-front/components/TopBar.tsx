"use client";

import { useState } from "react";
import { MarketSummary, toNumber } from "@/lib/api";
import { formatPercent, formatPrice } from "@/lib/format";

interface TopBarProps {
  symbols: MarketSummary[];
  symbol: string;
  onSymbolChange: (symbol: string) => void;
  userId: string;
  onUserIdChange: (userId: string) => void;
  connected: boolean;
}

const QUICK_TRADERS = ["alice", "bob", "carol"];

/** Champ libre: l'utilisateur n'est change qu'a la validation (Entree ou sortie du champ). */
function TraderInput({ userId, onCommit }: { userId: string; onCommit: (userId: string) => void }) {
  const [draft, setDraft] = useState(userId);

  function commit() {
    const value = draft.trim();
    if (value && value !== userId) onCommit(value);
    else setDraft(userId);
  }

  return (
    <input
      id="trader"
      value={draft}
      onChange={(e) => setDraft(e.target.value)}
      onBlur={commit}
      onKeyDown={(e) => e.key === "Enter" && commit()}
      className="w-28 border border-ink-600 bg-ink-800 px-2 py-1 text-paper-100 focus-visible:border-gold-500"
      placeholder="ton-id"
      autoComplete="off"
    />
  );
}

export function TopBar({ symbols, symbol, onSymbolChange, userId, onUserIdChange, connected }: TopBarProps) {
  return (
    <header className="border-b border-ink-700">
      <div className="flex flex-wrap items-center justify-between gap-4 px-4 py-3 sm:px-6">
        <div className="flex items-center gap-4">
          <span className="font-display text-lg font-bold tracking-tight text-paper-100">
            StockXchange<span className="text-gold-500">Sim</span>
          </span>
          <span className="flex items-center gap-2 text-xs text-paper-500">
            <span
              className={`h-1.5 w-1.5 rounded-full ${connected ? "bg-bid-500" : "bg-ask-500"}`}
              aria-hidden
            />
            {connected ? "Marché en direct" : "Reconnexion…"}
          </span>
        </div>

        <div className="flex flex-wrap items-center gap-2 text-sm text-paper-500">
          <label htmlFor="trader">Trader</label>
          {/* key: le champ repart de la nouvelle valeur quand l'utilisateur change via un raccourci */}
          <TraderInput key={userId} userId={userId} onCommit={onUserIdChange} />
          {QUICK_TRADERS.map((name) => (
            <button
              key={name}
              type="button"
              onClick={() => onUserIdChange(name)}
              className={`border px-2 py-1 text-xs transition-colors ${
                name === userId
                  ? "border-gold-500 text-gold-400"
                  : "border-ink-600 text-paper-500 hover:border-ink-500 hover:text-paper-300"
              }`}
            >
              {name}
            </button>
          ))}
        </div>
      </div>

      <nav aria-label="Symboles" className="flex overflow-x-auto border-t border-ink-700">
        {symbols.map((s) => {
          const last = toNumber(s.lastPrice ?? s.referencePrice);
          const change = ((last - toNumber(s.referencePrice)) / toNumber(s.referencePrice)) * 100;
          const active = s.symbol === symbol;
          return (
            <button
              key={s.symbol}
              type="button"
              onClick={() => onSymbolChange(s.symbol)}
              aria-current={active ? "true" : undefined}
              className={`flex min-w-36 shrink-0 flex-col items-start border-r border-ink-700 px-4 py-2 text-left transition-colors ${
                active ? "bg-ink-800 shadow-[inset_0_-2px_0_var(--gold-500)]" : "hover:bg-ink-900"
              }`}
            >
              <span className="text-xs font-medium text-paper-300">{s.symbol}</span>
              <span className="flex items-baseline gap-2">
                <span className="num text-sm text-paper-100">{formatPrice(last)}</span>
                <span
                  className={`num text-xs ${
                    change > 0 ? "text-bid-400" : change < 0 ? "text-ask-400" : "text-paper-500"
                  }`}
                >
                  {formatPercent(change)}
                </span>
              </span>
            </button>
          );
        })}
      </nav>
    </header>
  );
}
