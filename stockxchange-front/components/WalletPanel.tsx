"use client";

import { MarketSummary, toNumber, WalletResponse } from "@/lib/api";
import { formatMoney, formatQty } from "@/lib/format";

export function WalletPanel({
  wallet,
  owner,
  prices,
}: {
  wallet: WalletResponse | null;
  owner: string;
  prices: MarketSummary[];
}) {
  const priceOf = (symbol: string) => {
    const s = prices.find((p) => p.symbol === symbol);
    return toNumber(s?.lastPrice ?? s?.referencePrice);
  };
  const holdingsValue =
    wallet?.positions.reduce((sum, p) => sum + toNumber(p.quantity) * (priceOf(p.symbol) || 0), 0) ?? 0;
  const totalValue = toNumber(wallet?.balance) + holdingsValue;

  return (
    <section className="border border-ink-700">
      <header className="flex items-center justify-between border-b border-ink-700 px-3 py-2">
        <h2 className="text-xs tracking-wide text-paper-500">Portefeuille</h2>
        <span className="text-xs text-paper-300">{owner}</span>
      </header>

      {!wallet ? (
        <p className="p-3 text-sm text-paper-500">Chargement…</p>
      ) : (
        <div className="flex flex-col gap-1.5 p-3 text-sm">
          <Row label="Valeur totale" value={formatMoney(totalValue)} highlight />
          <Row label="Cash disponible" value={formatMoney(wallet.availableBalance)} />
          <Row label="Cash bloqué (ordres)" value={formatMoney(wallet.lockedBalance)} />

          <table className="mt-2 w-full border-t border-ink-700 text-sm">
            <caption className="pt-2 text-left text-xs text-paper-500">Positions</caption>
            <thead>
              <tr className="text-xs text-paper-500">
                <th className="py-1 text-left font-normal">Titre</th>
                <th className="py-1 text-right font-normal">Qté</th>
                <th className="py-1 text-right font-normal">Bloq.</th>
                <th className="py-1 text-right font-normal">Valeur</th>
              </tr>
            </thead>
            <tbody>
              {wallet.positions.map((p) => (
                <tr key={p.symbol}>
                  <td className="py-0.5 text-paper-300">{p.symbol}</td>
                  <td className="num py-0.5 text-right text-paper-100">{formatQty(p.quantity)}</td>
                  <td className="num py-0.5 text-right text-paper-500">{formatQty(p.lockedQuantity)}</td>
                  <td className="num py-0.5 text-right text-paper-300">
                    {formatMoney(toNumber(p.quantity) * priceOf(p.symbol))}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function Row({ label, value, highlight = false }: { label: string; value: string; highlight?: boolean }) {
  return (
    <div className="flex justify-between">
      <span className="text-paper-500">{label}</span>
      <span className={`num ${highlight ? "text-gold-400" : "text-paper-100"}`}>{value}</span>
    </div>
  );
}
