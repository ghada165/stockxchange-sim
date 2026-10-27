import { Decimal, toNumber } from "./api";

const priceFormat = new Intl.NumberFormat("fr-FR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const qtyFormat = new Intl.NumberFormat("fr-FR", { maximumFractionDigits: 0 });

export function formatPrice(value: Decimal | null | undefined): string {
  const n = toNumber(value);
  return Number.isFinite(n) ? priceFormat.format(n) : "—";
}

export function formatMoney(value: Decimal | null | undefined): string {
  const n = toNumber(value);
  return Number.isFinite(n) ? `${priceFormat.format(n)} $` : "—";
}

export function formatQty(value: Decimal | null | undefined): string {
  const n = toNumber(value);
  return Number.isFinite(n) ? qtyFormat.format(n) : "—";
}

export function formatPercent(value: number): string {
  if (!Number.isFinite(value)) return "—";
  const sign = value > 0 ? "+" : "";
  return `${sign}${value.toFixed(2)} %`;
}

export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString("fr-FR");
}
