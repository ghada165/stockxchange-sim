"use client";

import { useCallback, useEffect, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { RequireRole } from "@/components/RequireRole";
import {
  AccountStatus,
  ActivityLog,
  AdminOverview,
  AdminUser,
  api,
  DayOfWeek,
  MarketStatus,
  MarketSummary,
} from "@/lib/api";
import { DAY_LABELS, formatClock, formatDateTime, formatMoney, formatPrice, formatQty } from "@/lib/format";
import { useSession } from "@/lib/session";

const ALL_DAYS: DayOfWeek[] = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];
const ACTIONS = [
  "USER_REGISTERED",
  "LOGIN_SUCCESS",
  "LOGIN_FAILED",
  "PROFILE_UPDATED",
  "ORDER_PLACED",
  "ORDER_CANCELLED",
  "ORDER_REJECTED",
  "SYMBOL_CREATED",
  "SYMBOL_STATUS_CHANGED",
  "ACCOUNT_LOCKED",
  "LOGOUT",
  "PASSWORD_CHANGED",
  "USER_STATUS_CHANGED",
  "USER_ROLE_CHANGED",
  "MARKET_HOURS_UPDATED",
];

const input = "border border-ink-600 bg-ink-800 px-2 py-1.5 text-sm text-paper-100 focus-visible:border-gold-500";

export default function AdminPage() {
  return (
    <div className="min-h-screen">
      <AppHeader />
      <RequireRole role="ADMIN">
        <AdminDashboard />
      </RequireRole>
    </div>
  );
}

function AdminDashboard() {
  const [overview, setOverview] = useState<AdminOverview | null>(null);
  const [symbols, setSymbols] = useState<MarketSummary[]>([]);
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [status, setStatus] = useState<MarketStatus | null>(null);
  const [toast, setToast] = useState<{ message: string; isError: boolean } | null>(null);
  const [logsVersion, setLogsVersion] = useState(0);

  const loadData = useCallback(() => {
    api.admin.overview().then(setOverview).catch(() => {});
    api.symbols().then(setSymbols).catch(() => {});
    api.admin.users().then(setUsers).catch(() => {});
    api.marketStatus().then(setStatus).catch(() => {});
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  /** Apres une action ou sur demande: tout recharger, journal compris. */
  function refresh() {
    loadData();
    setLogsVersion((v) => v + 1);
  }

  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), 4500);
    return () => clearTimeout(timer);
  }, [toast]);

  async function run(action: () => Promise<unknown>, success: string) {
    try {
      await action();
      setToast({ message: success, isError: false });
      refresh();
    } catch (err) {
      setToast({ message: err instanceof Error ? err.message : "Erreur inconnue", isError: true });
    }
  }

  return (
    <main className="mx-auto flex max-w-7xl flex-col gap-6 px-4 py-6 sm:px-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl text-paper-100">Administration</h1>
        <button
          type="button"
          onClick={refresh}
          className="border border-ink-600 px-3 py-1 text-xs text-paper-300 hover:border-ink-500"
        >
          Actualiser
        </button>
      </div>

      <OverviewCards overview={overview} />

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <SymbolsSection
          symbols={symbols}
          onCreate={(payload) => run(() => api.admin.createSymbol(payload), `${payload.symbol} introduit en bourse`)}
          onToggle={(s) => {
            const next: AccountStatus = s.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE";
            return run(
              () => api.admin.setSymbolStatus(s.symbol, next),
              `${s.symbol} ${next === "ACTIVE" ? "réactivé" : "suspendu"}`,
            );
          }}
        />
        {status && (
          <MarketHoursSection
            key={`${status.openTime}-${status.closeTime}-${status.zone}-${status.tradingDays.join()}-${status.enforced}`}
            status={status}
            onSave={(payload) => run(() => api.admin.updateMarketHours(payload), "Heures de marché enregistrées")}
          />
        )}
      </div>

      <UsersSection
        users={users}
        onRoleChange={(u, role) =>
          run(() => api.admin.setUserRole(u.username, role), `${u.username} est maintenant ${role === "ADMIN" ? "administrateur" : "trader"}`)
        }
        onUnlock={(u) => run(() => api.admin.unlockUser(u.username), `${u.username} déverrouillé`)}
        onToggle={(u) => {
          const next: AccountStatus = u.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE";
          return run(
            () => api.admin.setUserStatus(u.username, next),
            `${u.username} ${next === "ACTIVE" ? "réactivé" : "suspendu"}`,
          );
        }}
      />

      <LogsSection version={logsVersion} />

      {toast && (
        <div
          role="status"
          className={`fixed bottom-6 right-6 max-w-sm border px-4 py-3 text-sm ${
            toast.isError ? "border-ask-500 bg-ink-900 text-ask-400" : "border-bid-500 bg-ink-900 text-bid-400"
          }`}
        >
          {toast.message}
        </div>
      )}
    </main>
  );
}

function OverviewCards({ overview }: { overview: AdminOverview | null }) {
  const cards: [string, string, string?][] = overview
    ? [
        ["Marché", overview.marketOpen ? "Ouvert" : "Fermé", overview.marketOpen ? "text-bid-400" : "text-ask-400"],
        ["Utilisateurs", `${overview.users} (${overview.traders} traders)`],
        ["Comptes suspendus", String(overview.suspendedUsers)],
        ["Symboles", `${overview.symbols} (${overview.suspendedSymbols} suspendus)`],
        ["Ordres actifs", String(overview.activeOrders)],
        ["Exécutions 24 h", String(overview.tradesLast24h)],
        ["Volume 24 h", formatQty(overview.volumeLast24h)],
        ["Montant échangé 24 h", formatMoney(overview.turnoverLast24h)],
      ]
    : [];
  return (
    <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
      {cards.map(([label, value, tone]) => (
        <div key={label} className="border border-ink-700 px-3 py-2">
          <dt className="text-xs text-paper-500">{label}</dt>
          <dd className={`num text-sm ${tone ?? "text-paper-100"}`}>{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function SymbolsSection({
  symbols,
  onCreate,
  onToggle,
}: {
  symbols: MarketSummary[];
  onCreate: (payload: { symbol: string; name: string; referencePrice: number }) => Promise<void>;
  onToggle: (symbol: MarketSummary) => Promise<void>;
}) {
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [price, setPrice] = useState("");

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    await onCreate({ symbol: code.trim().toUpperCase(), name: name.trim(), referencePrice: Number(price) });
    setCode("");
    setName("");
    setPrice("");
  }

  return (
    <section className="border border-ink-700">
      <header className="border-b border-ink-700 px-3 py-2">
        <h2 className="text-xs tracking-wide text-paper-500">Symboles cotés</h2>
      </header>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[30rem] text-sm">
          <thead>
            <tr className="text-xs text-paper-500">
              <th className="px-3 py-1 text-left font-normal">Symbole</th>
              <th className="px-3 py-1 text-left font-normal">Nom</th>
              <th className="px-3 py-1 text-right font-normal">Réf.</th>
              <th className="px-3 py-1 text-right font-normal">Dernier</th>
              <th className="px-3 py-1 text-left font-normal">Statut</th>
              <th className="px-3 py-1" />
            </tr>
          </thead>
          <tbody className="divide-y divide-ink-700">
            {symbols.map((s) => (
              <tr key={s.symbol}>
                <td className="px-3 py-1.5 text-paper-100">{s.symbol}</td>
                <td className="px-3 py-1.5 text-paper-300">{s.name}</td>
                <td className="num px-3 py-1.5 text-right text-paper-300">{formatPrice(s.referencePrice)}</td>
                <td className="num px-3 py-1.5 text-right text-paper-100">{formatPrice(s.lastPrice)}</td>
                <td className={`px-3 py-1.5 text-xs ${s.status === "ACTIVE" ? "text-bid-400" : "text-ask-400"}`}>
                  {s.status === "ACTIVE" ? "Actif" : "Suspendu"}
                </td>
                <td className="px-3 py-1.5 text-right">
                  <ToggleButton active={s.status === "ACTIVE"} onClick={() => onToggle(s)} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <form onSubmit={submit} className="grid grid-cols-1 gap-2 border-t border-ink-700 p-3 sm:grid-cols-[6rem_1fr_7rem_auto]">
        <input
          required
          aria-label="Code du symbole"
          placeholder="NVDA"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          pattern="[A-Z]{1,10}"
          maxLength={10}
          className={`${input} num`}
        />
        <input
          required
          aria-label="Nom de la société"
          placeholder="Nvidia Corp."
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={100}
          className={input}
        />
        <input
          required
          aria-label="Prix de référence"
          placeholder="140.00"
          type="number"
          step="0.01"
          min="0.01"
          value={price}
          onChange={(e) => setPrice(e.target.value)}
          className={`${input} num`}
        />
        <button type="submit" className="bg-gold-500 px-3 py-1.5 text-sm font-medium text-ink-950 hover:opacity-90">
          Ajouter
        </button>
        <p className="text-xs text-paper-500 sm:col-span-4">
          Chaque compte reçoit 100 actions du nouveau titre et le teneur de marché amorce son carnet.
        </p>
      </form>
    </section>
  );
}

function MarketHoursSection({
  status,
  onSave,
}: {
  status: MarketStatus;
  onSave: (payload: { open: string; close: string; zone: string; tradingDays: DayOfWeek[]; enforced: boolean }) => Promise<void>;
}) {
  const [open, setOpen] = useState(formatClock(status.openTime));
  const [close, setClose] = useState(formatClock(status.closeTime));
  const [zone, setZone] = useState(status.zone);
  const [days, setDays] = useState<DayOfWeek[]>(status.tradingDays);
  const [enforced, setEnforced] = useState(status.enforced);

  function toggleDay(day: DayOfWeek) {
    setDays((current) => (current.includes(day) ? current.filter((d) => d !== day) : [...current, day]));
  }

  return (
    <section className="border border-ink-700">
      <header className="flex items-center justify-between border-b border-ink-700 px-3 py-2">
        <h2 className="text-xs tracking-wide text-paper-500">Heures d&apos;ouverture du marché</h2>
        <span className={`text-xs ${status.open ? "text-bid-400" : "text-ask-400"}`}>
          {status.open ? "Ouvert maintenant" : "Fermé maintenant"}
        </span>
      </header>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          onSave({ open, close, zone, tradingDays: days, enforced });
        }}
        className="flex flex-col gap-3 p-3"
      >
        <div className="grid grid-cols-3 gap-2">
          <label className="flex flex-col gap-1 text-xs text-paper-500">
            Ouverture
            <input type="time" required value={open} onChange={(e) => setOpen(e.target.value)} className={`${input} num`} />
          </label>
          <label className="flex flex-col gap-1 text-xs text-paper-500">
            Fermeture
            <input type="time" required value={close} onChange={(e) => setClose(e.target.value)} className={`${input} num`} />
          </label>
          <label className="flex flex-col gap-1 text-xs text-paper-500">
            Fuseau
            <input required value={zone} onChange={(e) => setZone(e.target.value)} className={input} />
          </label>
        </div>
        <fieldset className="flex flex-wrap gap-1">
          <legend className="mb-1 text-xs text-paper-500">Jours d&apos;ouverture</legend>
          {ALL_DAYS.map((day) => (
            <button
              key={day}
              type="button"
              onClick={() => toggleDay(day)}
              aria-pressed={days.includes(day)}
              className={`w-12 border py-1 text-xs ${
                days.includes(day) ? "border-gold-500 text-gold-400" : "border-ink-600 text-paper-500 hover:border-ink-500"
              }`}
            >
              {DAY_LABELS[day]}
            </button>
          ))}
        </fieldset>
        <label className="flex items-start gap-2 text-sm text-paper-300">
          <input type="checkbox" checked={enforced} onChange={(e) => setEnforced(e.target.checked)} className="mt-1" />
          <span>
            Appliquer ces horaires
            <span className="block text-xs text-paper-500">
              Décoché: le marché reste ouvert en permanence (pratique pour une démonstration).
            </span>
          </span>
        </label>
        <button
          type="submit"
          disabled={days.length === 0}
          className="self-start bg-gold-500 px-3 py-1.5 text-sm font-medium text-ink-950 hover:opacity-90 disabled:opacity-50"
        >
          Enregistrer
        </button>
      </form>
    </section>
  );
}

function UsersSection({
  users,
  onToggle,
  onRoleChange,
  onUnlock,
}: {
  users: AdminUser[];
  onToggle: (user: AdminUser) => Promise<void>;
  onRoleChange: (user: AdminUser, role: "TRADER" | "ADMIN") => Promise<void>;
  onUnlock: (user: AdminUser) => Promise<void>;
}) {
  const session = useSession();
  const me = session?.user.username;
  return (
    <section className="border border-ink-700">
      <header className="border-b border-ink-700 px-3 py-2">
        <h2 className="text-xs tracking-wide text-paper-500">Utilisateurs ({users.length})</h2>
      </header>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[46rem] text-sm">
          <thead>
            <tr className="text-xs text-paper-500">
              <th className="px-3 py-1 text-left font-normal">Utilisateur</th>
              <th className="px-3 py-1 text-left font-normal">Nom</th>
              <th className="px-3 py-1 text-left font-normal">Rôle</th>
              <th className="px-3 py-1 text-left font-normal">Inscrit le</th>
              <th className="px-3 py-1 text-right font-normal">Cash</th>
              <th className="px-3 py-1 text-right font-normal">Ordres actifs</th>
              <th className="px-3 py-1 text-left font-normal">Statut</th>
              <th className="px-3 py-1" />
            </tr>
          </thead>
          <tbody className="divide-y divide-ink-700">
            {users.map((u) => (
              <tr key={u.username}>
                <td className="px-3 py-1.5 text-paper-100">{u.username}</td>
                <td className="px-3 py-1.5 text-paper-300">{u.displayName ?? "—"}</td>
                <td className="px-3 py-1.5">
                  {u.username === me ? (
                    <span className="text-paper-500">Admin (vous)</span>
                  ) : (
                    <select
                      aria-label={`Rôle de ${u.username}`}
                      value={u.role}
                      onChange={(e) => onRoleChange(u, e.target.value as "TRADER" | "ADMIN")}
                      className="border border-ink-600 bg-ink-800 px-1 py-0.5 text-xs text-paper-300"
                    >
                      <option value="TRADER">Trader</option>
                      <option value="ADMIN">Admin</option>
                    </select>
                  )}
                </td>
                <td className="num px-3 py-1.5 text-xs text-paper-500">{formatDateTime(u.createdAt)}</td>
                <td className="num px-3 py-1.5 text-right text-paper-300">
                  {u.cashBalance === null ? "—" : formatMoney(u.cashBalance)}
                </td>
                <td className="num px-3 py-1.5 text-right text-paper-300">{u.activeOrders}</td>
                <td className={`px-3 py-1.5 text-xs ${u.status === "ACTIVE" ? "text-bid-400" : "text-ask-400"}`}>
                  {u.status === "ACTIVE" ? "Actif" : "Suspendu"}
                  {u.lockedUntil && (
                    <span className="ml-1 text-gold-400" title={`Verrouillé jusqu'à ${formatDateTime(u.lockedUntil)}`}>
                      · verrouillé
                    </span>
                  )}
                </td>
                <td className="whitespace-nowrap px-3 py-1.5 text-right">
                  {u.lockedUntil && (
                    <button
                      type="button"
                      onClick={() => onUnlock(u)}
                      className="mr-1 border border-ink-600 px-2 py-0.5 text-xs text-paper-300 hover:border-gold-500"
                    >
                      Déverrouiller
                    </button>
                  )}
                  {u.username !== me && <ToggleButton active={u.status === "ACTIVE"} onClick={() => onToggle(u)} />}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function LogsSection({ version }: { version: number }) {
  const [actor, setActor] = useState("");
  const [action, setAction] = useState("");
  const [logs, setLogs] = useState<ActivityLog[]>([]);
  const [filter, setFilter] = useState({ actor: "", action: "" });

  useEffect(() => {
    api.admin.logs(filter).then(setLogs).catch(() => setLogs([]));
  }, [filter, version]);

  return (
    <section className="border border-ink-700">
      <header className="flex flex-wrap items-center justify-between gap-2 border-b border-ink-700 px-3 py-2">
        <h2 className="text-xs tracking-wide text-paper-500">Journal d&apos;activité (200 dernières lignes)</h2>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            setFilter({ actor: actor.trim(), action });
          }}
          className="flex flex-wrap gap-2"
        >
          <input
            aria-label="Filtrer par utilisateur"
            placeholder="utilisateur"
            value={actor}
            onChange={(e) => setActor(e.target.value)}
            className={`${input} w-32 py-1 text-xs`}
          />
          <select
            aria-label="Filtrer par action"
            value={action}
            onChange={(e) => setAction(e.target.value)}
            className={`${input} py-1 text-xs`}
          >
            <option value="">Toutes les actions</option>
            {ACTIONS.map((a) => (
              <option key={a} value={a}>
                {a}
              </option>
            ))}
          </select>
          <button type="submit" className="border border-ink-600 px-2 py-1 text-xs text-paper-300 hover:border-ink-500">
            Filtrer
          </button>
        </form>
      </header>
      {logs.length === 0 ? (
        <p className="p-3 text-sm text-paper-500">Aucune entrée.</p>
      ) : (
        <div className="max-h-[28rem] overflow-auto">
          <table className="w-full min-w-[40rem] text-sm">
            <thead className="sticky top-0 bg-ink-950">
              <tr className="text-xs text-paper-500">
                <th className="px-3 py-1 text-left font-normal">Date</th>
                <th className="px-3 py-1 text-left font-normal">Acteur</th>
                <th className="px-3 py-1 text-left font-normal">Action</th>
                <th className="px-3 py-1 text-left font-normal">Détails</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-ink-700">
              {logs.map((log) => (
                <tr key={log.id}>
                  <td className="num whitespace-nowrap px-3 py-1.5 text-xs text-paper-500">{formatDateTime(log.occurredAt)}</td>
                  <td className="px-3 py-1.5 text-paper-300">{log.actor}</td>
                  <td
                    className={`px-3 py-1.5 text-xs ${
                      log.action.includes("FAILED") || log.action.includes("REJECTED") ? "text-ask-400" : "text-paper-300"
                    }`}
                  >
                    {log.action}
                  </td>
                  <td className="px-3 py-1.5 text-xs text-paper-500">{log.details}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function ToggleButton({ active, onClick }: { active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`border px-2 py-0.5 text-xs ${
        active
          ? "border-ink-600 text-paper-300 hover:border-ask-500 hover:text-ask-400"
          : "border-ink-600 text-paper-300 hover:border-bid-500 hover:text-bid-400"
      }`}
    >
      {active ? "Suspendre" : "Réactiver"}
    </button>
  );
}
