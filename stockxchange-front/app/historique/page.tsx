"use client";

import { useEffect, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { RequireRole } from "@/components/RequireRole";
import { api, TradeHistory, toNumber } from "@/lib/api";
import { formatDateTime, formatMoney, formatPrice, formatQty } from "@/lib/format";

export default function HistoryPage() {
  return (
    <div className="min-h-screen">
      <AppHeader />
      <RequireRole role="TRADER">
        <History />
      </RequireRole>
    </div>
  );
}

function History() {
  const [history, setHistory] = useState<TradeHistory | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [symbolFilter, setSymbolFilter] = useState("ALL");

  useEffect(() => {
    api.history().then(setHistory).catch((e: Error) => setError(e.message));
  }, []);

  if (error) return <p className="p-6 text-sm text-ask-400">{error}</p>;
  if (!history) return <p className="p-6 text-sm text-paper-500">Chargement…</p>;

  const symbols = Array.from(new Set(history.trades.map((t) => t.symbol))).sort();
  const trades = symbolFilter === "ALL" ? history.trades : history.trades.filter((t) => t.symbol === symbolFilter);
  const { summary } = history;
  const net = toNumber(summary.netCashFlow);

  function exportCsv() {
    const header = "date;symbole;sens;prix;quantite;montant";
    const rows = trades.map((t) =>
      [t.executedAt, t.symbol, t.side === "BUY" ? "achat" : "vente", t.price, t.quantity, t.amount].join(";"),
    );
    const blob = new Blob([[header, ...rows].join("\n")], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `releve-${history!.username}-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-4 px-4 py-6 sm:px-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl text-paper-100">Historique &amp; relevé</h1>
          <p className="text-xs text-paper-500">Relevé généré le {formatDateTime(history.generatedAt)}</p>
        </div>
        <div className="flex items-center gap-2 text-sm">
          <label htmlFor="symbol-filter" className="text-xs text-paper-500">
            Titre
          </label>
          <select
            id="symbol-filter"
            value={symbolFilter}
            onChange={(e) => setSymbolFilter(e.target.value)}
            className="border border-ink-600 bg-ink-800 px-2 py-1 text-paper-100"
          >
            <option value="ALL">Tous</option>
            {symbols.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
          <button
            type="button"
            onClick={exportCsv}
            disabled={trades.length === 0}
            className="border border-ink-600 px-3 py-1 text-xs text-paper-300 hover:border-ink-500 disabled:opacity-50"
          >
            Exporter en CSV
          </button>
        </div>
      </div>

      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        <Card label="Exécutions" value={String(summary.tradeCount)} />
        <Card label="Achats" value={`${summary.buyCount} · ${formatMoney(summary.totalBought)}`} />
        <Card label="Ventes" value={`${summary.sellCount} · ${formatMoney(summary.totalSold)}`} />
        <Card
          label="Flux net (ventes − achats)"
          value={formatMoney(net)}
          tone={net > 0 ? "text-bid-400" : net < 0 ? "text-ask-400" : "text-paper-100"}
        />
        <Card label="Titres négociés" value={String(symbols.length)} />
      </dl>

      <section className="border border-ink-700">
        {trades.length === 0 ? (
          <p className="p-4 text-sm text-paper-500">Aucune exécution pour l&apos;instant.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[36rem] text-sm">
              <thead>
                <tr className="text-xs text-paper-500">
                  <th className="px-3 py-2 text-left font-normal">Date</th>
                  <th className="px-3 py-2 text-left font-normal">Titre</th>
                  <th className="px-3 py-2 text-left font-normal">Sens</th>
                  <th className="px-3 py-2 text-right font-normal">Prix</th>
                  <th className="px-3 py-2 text-right font-normal">Quantité</th>
                  <th className="px-3 py-2 text-right font-normal">Montant</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-ink-700">
                {trades.map((t) => (
                  <tr key={t.id}>
                    <td className="num px-3 py-1.5 text-xs text-paper-500">{formatDateTime(t.executedAt)}</td>
                    <td className="px-3 py-1.5 text-paper-300">{t.symbol}</td>
                    <td className={`px-3 py-1.5 ${t.side === "BUY" ? "text-bid-400" : "text-ask-400"}`}>
                      {t.side === "BUY" ? "Achat" : "Vente"}
                    </td>
                    <td className="num px-3 py-1.5 text-right text-paper-100">{formatPrice(t.price)}</td>
                    <td className="num px-3 py-1.5 text-right text-paper-300">{formatQty(t.quantity)}</td>
                    <td className="num px-3 py-1.5 text-right text-paper-100">{formatMoney(t.amount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </main>
  );
}

function Card({ label, value, tone = "text-paper-100" }: { label: string; value: string; tone?: string }) {
  return (
    <div className="border border-ink-700 px-3 py-2">
      <dt className="text-xs text-paper-500">{label}</dt>
      <dd className={`num text-sm ${tone}`}>{value}</dd>
    </div>
  );
}
