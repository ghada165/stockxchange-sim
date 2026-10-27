"use client";

import { useState } from "react";
import { Candle, toNumber } from "@/lib/api";
import { formatPrice, formatQty } from "@/lib/format";

const INTERVALS = ["1m", "5m", "15m", "1h"] as const;
export type CandleInterval = (typeof INTERVALS)[number];

const W = 600;
const H = 180;
const PAD = { top: 10, right: 8, bottom: 8, left: 8 };

/**
 * Historique de prix (cloture de chaque chandelier): une seule serie, donc
 * pas de legende. Repere vertical + infobulle au survol.
 */
export function PriceChart({
  symbol,
  candles,
  interval,
  onIntervalChange,
}: {
  symbol: string;
  candles: Candle[];
  interval: CandleInterval;
  onIntervalChange: (interval: CandleInterval) => void;
}) {
  const [hover, setHover] = useState<number | null>(null);

  const closes = candles.map((c) => toNumber(c.close));
  const highs = candles.map((c) => toNumber(c.high));
  const lows = candles.map((c) => toNumber(c.low));
  const max = Math.max(...highs);
  const min = Math.min(...lows);
  const span = max - min || max * 0.01 || 1;
  const yMin = min - span * 0.1;
  const yMax = max + span * 0.1;

  const x = (i: number) => PAD.left + (candles.length <= 1 ? 0.5 : i / (candles.length - 1)) * (W - PAD.left - PAD.right);
  const y = (v: number) => PAD.top + (1 - (v - yMin) / (yMax - yMin)) * (H - PAD.top - PAD.bottom);

  const line = closes.map((v, i) => `${i === 0 ? "M" : "L"}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(" ");
  const area = `${line} L${x(closes.length - 1).toFixed(1)},${H - PAD.bottom} L${x(0).toFixed(1)},${H - PAD.bottom} Z`;
  const gridValues = [yMax - (yMax - yMin) * 0.25, (yMax + yMin) / 2, yMin + (yMax - yMin) * 0.25];

  function handleMove(e: React.PointerEvent<SVGSVGElement>) {
    const rect = e.currentTarget.getBoundingClientRect();
    const ratio = (e.clientX - rect.left) / rect.width;
    const index = Math.round(ratio * (candles.length - 1));
    setHover(Math.min(Math.max(index, 0), candles.length - 1));
  }

  const hovered = hover !== null ? candles[hover] : null;
  const hoveredTime = hovered
    ? new Date(hovered.start).toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" })
    : null;

  return (
    <section className="border border-ink-700">
      <header className="flex items-center justify-between border-b border-ink-700 px-3 py-2">
        <h2 className="text-xs tracking-wide text-paper-500">Historique de prix — {symbol}</h2>
        <div className="flex gap-1" role="group" aria-label="Intervalle">
          {INTERVALS.map((i) => (
            <button
              key={i}
              type="button"
              onClick={() => onIntervalChange(i)}
              aria-pressed={interval === i}
              className={`num border px-1.5 py-0.5 text-xs ${
                interval === i ? "border-paper-300 text-paper-100" : "border-ink-600 text-paper-500 hover:border-ink-500"
              }`}
            >
              {i}
            </button>
          ))}
        </div>
      </header>

      {candles.length < 2 ? (
        <p className="px-3 py-10 text-center text-sm text-paper-500">
          Pas encore assez d&apos;exécutions pour tracer la courbe. Passez quelques ordres !
        </p>
      ) : (
        <div className="relative px-3 pb-2 pt-3">
          <div className="num mb-1 flex justify-between text-xs text-paper-500">
            <span>
              {hovered ? (
                <>
                  <span className="text-paper-300">{hoveredTime}</span> · O {formatPrice(hovered.open)} · H{" "}
                  {formatPrice(hovered.high)} · B {formatPrice(hovered.low)} · C{" "}
                  <span className="text-paper-100">{formatPrice(hovered.close)}</span> · vol {formatQty(hovered.volume)}
                </>
              ) : (
                <>Survolez la courbe pour le détail de chaque intervalle</>
              )}
            </span>
            <span>
              {formatPrice(min)} – {formatPrice(max)}
            </span>
          </div>
          <div className="relative h-44 w-full">
            <svg
              viewBox={`0 0 ${W} ${H}`}
              preserveAspectRatio="none"
              className="absolute inset-0 h-full w-full touch-none"
              role="img"
              aria-label={`Prix de clôture de ${symbol}, de ${formatPrice(closes[0])} à ${formatPrice(closes[closes.length - 1])}`}
              onPointerMove={handleMove}
              onPointerLeave={() => setHover(null)}
            >
              {gridValues.map((v) => (
                <line
                  key={v}
                  x1={PAD.left}
                  x2={W - PAD.right}
                  y1={y(v)}
                  y2={y(v)}
                  stroke="var(--ink-700)"
                  strokeWidth={1}
                  vectorEffect="non-scaling-stroke"
                />
              ))}
              <path d={area} fill="var(--gold-500)" opacity={0.08} />
              <path
                d={line}
                fill="none"
                stroke="var(--gold-400)"
                strokeWidth={2}
                strokeLinejoin="round"
                strokeLinecap="round"
                vectorEffect="non-scaling-stroke"
              />
              {hover !== null && (
                <line
                  x1={x(hover)}
                  x2={x(hover)}
                  y1={PAD.top}
                  y2={H - PAD.bottom}
                  stroke="var(--paper-500)"
                  strokeWidth={1}
                  strokeDasharray="3 3"
                  vectorEffect="non-scaling-stroke"
                />
              )}
            </svg>
            {hover !== null && (
              // Point en HTML: un cercle SVG serait deforme par preserveAspectRatio="none"
              <span
                aria-hidden
                className="pointer-events-none absolute h-2.5 w-2.5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-ink-950 bg-gold-400"
                style={{ left: `${(x(hover) / W) * 100}%`, top: `${(y(closes[hover]) / H) * 100}%` }}
              />
            )}
          </div>
        </div>
      )}
    </section>
  );
}
