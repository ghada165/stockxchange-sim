"use client";

import Link from "next/link";
import { Role, useSession } from "@/lib/session";

/** Affiche le contenu seulement pour le role demande (ou tout utilisateur connecte si role absent). */
export function RequireRole({ role, children }: { role?: Role; children: React.ReactNode }) {
  const session = useSession();

  if (!session) {
    return (
      <Notice title="Connexion requise">
        Cette page est réservée aux utilisateurs connectés.{" "}
        <Link href="/login" className="text-gold-400 underline underline-offset-2">
          Se connecter
        </Link>
      </Notice>
    );
  }
  if (role && session.user.role !== role) {
    return (
      <Notice title="Accès refusé">
        Cette page est réservée aux {role === "ADMIN" ? "administrateurs" : "traders"}.
      </Notice>
    );
  }
  return <>{children}</>;
}

function Notice({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="mx-auto mt-16 max-w-md border border-ink-700 p-6 text-center">
      <h1 className="mb-2 text-lg text-paper-100">{title}</h1>
      <p className="text-sm text-paper-500">{children}</p>
    </div>
  );
}
