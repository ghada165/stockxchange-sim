"use client";

import Link from "next/link";
import { useSession } from "@/lib/session";

/** Remplace le formulaire d'ordre pour un visiteur (ou un administrateur, qui ne trade pas). */
export function VisitorPanel() {
  const session = useSession();

  if (session?.user.role === "ADMIN") {
    return (
      <section className="border border-ink-700 p-4 text-sm text-paper-500">
        <h2 className="mb-2 text-paper-100">Mode administrateur</h2>
        <p className="mb-3">Les administrateurs supervisent la plateforme mais ne passent pas d&apos;ordres.</p>
        <Link href="/admin" className="text-gold-400 underline underline-offset-2">
          Ouvrir l&apos;administration
        </Link>
      </section>
    );
  }

  return (
    <section className="border border-ink-700 p-4 text-sm text-paper-500">
      <h2 className="mb-2 text-paper-100">Vous consultez le marché en visiteur</h2>
      <p className="mb-4">
        Connectez-vous pour passer des ordres. Chaque nouveau compte reçoit 100 000 $ et 100 actions de chaque titre.
      </p>
      <div className="flex gap-2">
        <Link
          href="/login"
          className="flex-1 bg-gold-500 py-2 text-center font-medium text-ink-950 transition-opacity hover:opacity-90"
        >
          Se connecter
        </Link>
        <Link
          href="/login?mode=register"
          className="flex-1 border border-ink-600 py-2 text-center text-paper-300 hover:border-ink-500"
        >
          Créer un compte
        </Link>
      </div>
    </section>
  );
}
