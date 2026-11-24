"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { useSession } from "@/lib/session";

/** En-tete commun: navigation selon le role, compte connecte, etat du flux temps reel. */
export function AppHeader({ connected }: { connected?: boolean }) {
  const session = useSession();
  const pathname = usePathname();
  const router = useRouter();
  const role = session?.user.role;

  const links = [
    { href: "/", label: "Marché", show: true },
    { href: "/historique", label: "Historique", show: role === "TRADER" },
    { href: "/admin", label: "Administration", show: role === "ADMIN" },
    { href: "/profil", label: "Profil", show: !!session },
  ].filter((l) => l.show);

  async function logout() {
    await api.logout();
    router.push("/");
  }

  return (
    <header className="flex flex-wrap items-center justify-between gap-x-6 gap-y-2 border-b border-ink-700 px-4 py-3 sm:px-6">
      <div className="flex flex-wrap items-center gap-x-6 gap-y-2">
        <Link href="/" className="font-display text-lg font-bold tracking-tight text-paper-100">
          StockXchange<span className="text-gold-500">Sim</span>
        </Link>
        <nav aria-label="Navigation principale" className="flex gap-1 text-sm">
          {links.map((link) => {
            const active = pathname === link.href;
            return (
              <Link
                key={link.href}
                href={link.href}
                aria-current={active ? "page" : undefined}
                className={`px-2 py-1 transition-colors ${
                  active ? "text-paper-100 shadow-[inset_0_-2px_0_var(--gold-500)]" : "text-paper-500 hover:text-paper-300"
                }`}
              >
                {link.label}
              </Link>
            );
          })}
        </nav>
        {connected !== undefined && (
          <span className="flex items-center gap-2 text-xs text-paper-500">
            <span className={`h-1.5 w-1.5 rounded-full ${connected ? "bg-bid-500" : "bg-ask-500"}`} aria-hidden />
            {connected ? "Flux en direct" : "Reconnexion…"}
          </span>
        )}
      </div>

      {session ? (
        <div className="flex items-center gap-3 text-sm">
          <span className="text-paper-300">
            {session.user.displayName ?? session.user.username}
            <span className="ml-2 border border-ink-600 px-1.5 py-0.5 text-xs text-paper-500">
              {session.user.role === "ADMIN" ? "Admin" : "Trader"}
            </span>
          </span>
          <button
            type="button"
            onClick={logout}
            className="border border-ink-600 px-2 py-1 text-xs text-paper-500 hover:border-ink-500 hover:text-paper-300"
          >
            Déconnexion
          </button>
        </div>
      ) : (
        <Link
          href="/login"
          className="bg-gold-500 px-3 py-1.5 text-sm font-medium text-ink-950 transition-opacity hover:opacity-90"
        >
          Se connecter
        </Link>
      )}
    </header>
  );
}
