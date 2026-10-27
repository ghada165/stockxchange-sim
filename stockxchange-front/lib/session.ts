"use client";

import { useSyncExternalStore } from "react";

export type Role = "TRADER" | "ADMIN";

export interface SessionUser {
  username: string;
  displayName: string | null;
  email: string | null;
  role: Role;
  status: "ACTIVE" | "SUSPENDED";
  createdAt: string;
}

export interface Session {
  token: string;
  expiresAt: string;
  user: SessionUser;
}

const STORAGE_KEY = "stockxchange.session";

/** undefined = pas encore lu depuis le navigateur. */
let current: Session | null | undefined;
const listeners = new Set<() => void>();

function load(): Session | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const session = JSON.parse(raw) as Session;
    return new Date(session.expiresAt).getTime() > Date.now() ? session : null;
  } catch {
    return null;
  }
}

export function getSession(): Session | null {
  if (current === undefined) {
    current = typeof window === "undefined" ? null : load();
  }
  return current;
}

export function setSession(session: Session | null) {
  current = session;
  try {
    if (session) window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    else window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    // stockage indisponible (navigation privee): la session reste en memoire
  }
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** Session courante (jeton JWT + profil), partagee par toutes les pages. Null pour un visiteur. */
export function useSession(): Session | null {
  return useSyncExternalStore(subscribe, getSession, () => null);
}
