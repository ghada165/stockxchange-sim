"use client";

import { WalletResponse } from "@/lib/api";

export function WalletPanel({ wallet }: { wallet: WalletResponse | null }) {
  return (
    <section className="border border-ink-700">
      <header className="border-b border-ink-700 px-3 py-2">
        <h2 className="text-xs tracking-wide text-paper-500">Portefeuille</h2>
      </header>

      {!wallet ? (
        <p className="p-3 text-sm text-paper-500">Chargement...</p>
      ) : (
        <div className="flex flex-col gap-2 p-3 text-sm">
          <Row label="Disponible" value={wallet.availableBalance} highlight />
          <Row label="Bloque" value={wallet.lockedBalance} />
          <Row label="Solde total" value={wallet.balance} />

          {Object.keys(wallet.positions).length > 0 && (
            <div className="mt-2 border-t border-ink-700 pt-2">
              <p className="mb-1 text-xs text-paper-500">Positions</p>
              {Object.entries(wallet.positions).map(([symbol, qty]) => (
                <Row key={symbol} label={symbol} value={qty} unit="" />
              ))}
            </div>
          )}
        </div>
      )}
    </section>
  );
}

function Row({
  label,
  value,
  unit = "$",
  highlight = false,
}: {
  label: string;
  value: string;
  unit?: string;
  highlight?: boolean;
}) {
  return (
    <div className="flex justify-between">
      <span className="text-paper-500">{label}</span>
      <span className={`num ${highlight ? "text-gold-400" : "text-paper-100"}`}>
        {unit}
        {parseFloat(value).toLocaleString("fr-FR", { minimumFractionDigits: 2, maximumFractionDigits: 8 })}
      </span>
    </div>
  );
}
