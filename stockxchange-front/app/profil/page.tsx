"use client";

import { useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { RequireRole } from "@/components/RequireRole";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { getSession, setSession, useSession } from "@/lib/session";

export default function ProfilePage() {
  return (
    <div className="min-h-screen">
      <AppHeader />
      <RequireRole>
        <ProfileForm />
      </RequireRole>
    </div>
  );
}

function ProfileForm() {
  const session = useSession();
  const user = session!.user;
  const [displayName, setDisplayName] = useState(user.displayName ?? "");
  const [email, setEmail] = useState(user.email ?? "");
  const [message, setMessage] = useState<{ text: string; isError: boolean } | null>(null);
  const [saving, setSaving] = useState(false);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setMessage(null);
    try {
      const updated = await api.updateProfile({ displayName, email });
      const current = getSession();
      if (current) setSession({ ...current, user: updated });
      setMessage({ text: "Profil enregistré", isError: false });
    } catch (err) {
      setMessage({ text: err instanceof Error ? err.message : "Erreur inconnue", isError: true });
    } finally {
      setSaving(false);
    }
  }

  const input = "border border-ink-600 bg-ink-800 px-2 py-1.5 text-sm text-paper-100 focus-visible:border-gold-500";

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 px-4 py-8">
      <h1 className="text-xl text-paper-100">Mon profil</h1>

      <dl className="grid grid-cols-2 gap-3 border border-ink-700 p-4 text-sm">
        <div>
          <dt className="text-xs text-paper-500">Nom d&apos;utilisateur</dt>
          <dd className="text-paper-100">{user.username}</dd>
        </div>
        <div>
          <dt className="text-xs text-paper-500">Rôle</dt>
          <dd className="text-paper-100">{user.role === "ADMIN" ? "Administrateur" : "Trader"}</dd>
        </div>
        <div>
          <dt className="text-xs text-paper-500">Statut</dt>
          <dd className={user.status === "ACTIVE" ? "text-bid-400" : "text-ask-400"}>
            {user.status === "ACTIVE" ? "Actif" : "Suspendu"}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-paper-500">Membre depuis</dt>
          <dd className="num text-paper-100">{formatDateTime(user.createdAt)}</dd>
        </div>
      </dl>

      <form onSubmit={save} className="flex flex-col gap-3 border border-ink-700 p-4">
        <h2 className="text-xs tracking-wide text-paper-500">Informations</h2>
        <label className="flex flex-col gap-1 text-xs text-paper-500">
          Nom affiché
          <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} className={input} maxLength={100} />
        </label>
        <label className="flex flex-col gap-1 text-xs text-paper-500">
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} className={input} />
        </label>
        {message && (
          <p role="status" className={`text-sm ${message.isError ? "text-ask-400" : "text-bid-400"}`}>
            {message.text}
          </p>
        )}
        <button
          type="submit"
          disabled={saving}
          className="bg-gold-500 py-2 text-sm font-medium text-ink-950 transition-opacity hover:opacity-90 disabled:opacity-50"
        >
          {saving ? "Enregistrement…" : "Enregistrer"}
        </button>
      </form>

      <PasswordForm />
    </main>
  );
}

/** Changement de mot de passe: les autres sessions ouvertes sont deconnectees par le serveur. */
function PasswordForm() {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [message, setMessage] = useState<{ text: string; isError: boolean } | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (next !== confirm) {
      setMessage({ text: "La confirmation ne correspond pas au nouveau mot de passe", isError: true });
      return;
    }
    setSaving(true);
    setMessage(null);
    try {
      await api.changePassword(current, next);
      setCurrent("");
      setNext("");
      setConfirm("");
      setMessage({ text: "Mot de passe modifié. Vos autres sessions ont été déconnectées.", isError: false });
    } catch (err) {
      setMessage({ text: err instanceof Error ? err.message : "Erreur inconnue", isError: true });
    } finally {
      setSaving(false);
    }
  }

  const input = "border border-ink-600 bg-ink-800 px-2 py-1.5 text-sm text-paper-100 focus-visible:border-gold-500";

  return (
    <form onSubmit={submit} className="flex flex-col gap-3 border border-ink-700 p-4">
      <h2 className="text-xs tracking-wide text-paper-500">Changer de mot de passe</h2>
      <label className="flex flex-col gap-1 text-xs text-paper-500">
        Mot de passe actuel
        <input
          required
          type="password"
          autoComplete="current-password"
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
          className={input}
        />
      </label>
      <label className="flex flex-col gap-1 text-xs text-paper-500">
        Nouveau mot de passe (8 caractères minimum, au moins une lettre et un chiffre)
        <input
          required
          type="password"
          autoComplete="new-password"
          minLength={8}
          maxLength={72}
          pattern="(?=.*[A-Za-z])(?=.*\d).+"
          title="Au moins une lettre et un chiffre"
          value={next}
          onChange={(e) => setNext(e.target.value)}
          className={input}
        />
      </label>
      <label className="flex flex-col gap-1 text-xs text-paper-500">
        Confirmer le nouveau mot de passe
        <input
          required
          type="password"
          autoComplete="new-password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          className={input}
        />
      </label>
      {message && (
        <p role="status" className={`text-sm ${message.isError ? "text-ask-400" : "text-bid-400"}`}>
          {message.text}
        </p>
      )}
      <button
        type="submit"
        disabled={saving}
        className="border border-ink-600 py-2 text-sm text-paper-100 hover:border-gold-500 disabled:opacity-50"
      >
        {saving ? "Modification…" : "Changer le mot de passe"}
      </button>
    </form>
  );
}
