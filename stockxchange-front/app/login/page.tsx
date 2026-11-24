"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { AppHeader } from "@/components/AppHeader";
import { api } from "@/lib/api";

const DEMO_ACCOUNTS = [
  { username: "alice", password: "password123", label: "alice · trader" },
  { username: "bob", password: "password123", label: "bob · trader" },
  { username: "carol", password: "password123", label: "carol · trader" },
  { username: "admin", password: "admin123", label: "admin · administrateur" },
];

export default function LoginPage() {
  return (
    <div className="min-h-screen">
      <AppHeader />
      <Suspense>
        <AuthForms />
      </Suspense>
    </div>
  );
}

function AuthForms() {
  const router = useRouter();
  const params = useSearchParams();
  const [mode, setMode] = useState<"login" | "register">(params.get("mode") === "register" ? "register" : "login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const session =
        mode === "login"
          ? await api.login(username, password)
          : await api.register({
              username,
              password,
              displayName: displayName || undefined,
              email: email || undefined,
            });
      router.push(session.user.role === "ADMIN" ? "/admin" : "/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Erreur inconnue");
    } finally {
      setSubmitting(false);
    }
  }

  const input =
    "border border-ink-600 bg-ink-800 px-2 py-1.5 text-sm text-paper-100 focus-visible:border-gold-500";

  return (
    <main className="mx-auto flex max-w-md flex-col gap-4 px-4 py-10">
      <div className="grid grid-cols-2 border border-ink-700" role="tablist">
        {(["login", "register"] as const).map((m) => (
          <button
            key={m}
            type="button"
            role="tab"
            aria-selected={mode === m}
            onClick={() => {
              setMode(m);
              setError(null);
            }}
            className={`py-2 text-sm ${mode === m ? "bg-ink-800 text-paper-100" : "text-paper-500 hover:text-paper-300"}`}
          >
            {m === "login" ? "Connexion" : "Inscription"}
          </button>
        ))}
      </div>

      <form onSubmit={submit} className="flex flex-col gap-3 border border-ink-700 p-4">
        <label className="flex flex-col gap-1 text-xs text-paper-500">
          Nom d&apos;utilisateur
          <input
            required
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className={input}
            minLength={3}
            maxLength={32}
            pattern="[a-zA-Z0-9._\-]+"
          />
        </label>
        <label className="flex flex-col gap-1 text-xs text-paper-500">
          Mot de passe {mode === "register" && "(8 caractères minimum, au moins une lettre et un chiffre)"}
          <input
            required
            type="password"
            autoComplete={mode === "login" ? "current-password" : "new-password"}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className={input}
            minLength={mode === "register" ? 8 : undefined}
            maxLength={72}
            pattern={mode === "register" ? "(?=.*[A-Za-z])(?=.*\\d).+" : undefined}
            title={mode === "register" ? "Au moins une lettre et un chiffre" : undefined}
          />
        </label>
        {mode === "register" && (
          <>
            <label className="flex flex-col gap-1 text-xs text-paper-500">
              Nom affiché (facultatif)
              <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} className={input} maxLength={100} />
            </label>
            <label className="flex flex-col gap-1 text-xs text-paper-500">
              Email (facultatif)
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} className={input} />
            </label>
          </>
        )}

        {error && (
          <p role="alert" className="text-sm text-ask-400">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="mt-1 bg-gold-500 py-2 text-sm font-medium text-ink-950 transition-opacity hover:opacity-90 disabled:opacity-50"
        >
          {submitting ? "…" : mode === "login" ? "Se connecter" : "Créer mon compte trader"}
        </button>
        {mode === "register" && (
          <p className="text-xs text-paper-500">Votre compte démarre avec 100 000 $ et 100 actions de chaque titre.</p>
        )}
      </form>

      {mode === "login" && (
        <section className="border border-ink-700 p-4">
          <h2 className="mb-2 text-xs tracking-wide text-paper-500">Comptes de démonstration</h2>
          <div className="grid grid-cols-2 gap-2">
            {DEMO_ACCOUNTS.map((demo) => (
              <button
                key={demo.username}
                type="button"
                onClick={() => {
                  setUsername(demo.username);
                  setPassword(demo.password);
                }}
                className="border border-ink-600 px-2 py-1.5 text-left text-xs text-paper-300 hover:border-ink-500"
              >
                {demo.label}
              </button>
            ))}
          </div>
        </section>
      )}

      <Link href="/" className="text-center text-xs text-paper-500 hover:text-paper-300">
        Continuer en visiteur →
      </Link>
    </main>
  );
}
