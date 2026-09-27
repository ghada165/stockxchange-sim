"use client";

interface TopBarProps {
  symbol: string;
  onSymbolChange: (symbol: string) => void;
  userId: string;
  onUserIdChange: (userId: string) => void;
  connected: boolean;
}

const SYMBOLS = ["AAPL", "TSLA", "MSFT", "GOOGL"];

export function TopBar({ symbol, onSymbolChange, userId, onUserIdChange, connected }: TopBarProps) {
  return (
    <header className="flex items-center justify-between border-b border-ink-700 px-6 py-4">
      <div className="flex items-center gap-6">
        <span className="font-display text-lg font-bold tracking-tight text-paper-100">
          StockXchange<span className="text-gold-500">Sim</span>
        </span>

        <select
          value={symbol}
          onChange={(e) => onSymbolChange(e.target.value)}
          className="num border border-ink-600 bg-ink-800 px-3 py-1.5 text-sm text-paper-100"
        >
          {SYMBOLS.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>

        <span className="flex items-center gap-2 text-xs text-paper-500">
          <span
            className={`h-1.5 w-1.5 rounded-full ${connected ? "bg-bid-500" : "bg-ask-500"}`}
            aria-hidden
          />
          {connected ? "Marche en direct" : "Reconnexion..."}
        </span>
      </div>

      <label className="flex items-center gap-2 text-sm text-paper-500">
        Trader
        <input
          value={userId}
          onChange={(e) => onUserIdChange(e.target.value)}
          className="w-32 border border-ink-600 bg-ink-800 px-2 py-1.5 text-paper-100 focus-visible:border-gold-500"
          placeholder="ton-id"
        />
      </label>
    </header>
  );
}
