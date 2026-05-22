/**
 * Token-based auth helper for the Web Dashboard.
 *
 * Two-token model:
 *  - **link token** (URL `?t=`): one-shot, server-issued from the in-game UI / chat.
 *  - **session token** (localStorage `ro.token` + cookie `ro_session`): long-lived,
 *    obtained by exchanging the link token via `POST /api/auth/exchange`.
 *
 * Boot flow:
 *  1. If URL contains a link token, exchange it for a session token, persist to
 *     localStorage, and let the server set the `ro_session` cookie.
 *  2. The cookie acts as a fallback so re-opening the browser keeps the session
 *     even if localStorage is cleared.
 *  3. Subsequent fetches send `Authorization: Bearer <session-token>` (also relies
 *     on `credentials: 'include'` so the cookie is sent).
 */

const STORAGE_KEY = 'ro.token';

type UnauthorizedHandler = () => void;
let onUnauthorized: UnauthorizedHandler | null = null;

export interface ExchangeResponse {
  session_token: string;
  uuid: string;
  name: string;
  admin: boolean;
}

/**
 * If the URL carries a link token (`?t=...`), exchange it for a session token
 * and persist it. Always strips the token query param afterwards, regardless of
 * whether the exchange succeeded, so it does not stay in browser history.
 */
export async function consumeUrlToken(): Promise<void> {
  if (typeof window === 'undefined') return;
  let linkToken: string | null = null;
  try {
    const params = new URLSearchParams(window.location.search);
    const t = params.get('t') || params.get('token');
    if (t && t.trim().length > 0) {
      linkToken = t.trim();
      params.delete('t');
      params.delete('token');
      const search = params.toString();
      const newUrl =
        window.location.pathname + (search ? `?${search}` : '') + window.location.hash;
      window.history.replaceState({}, document.title, newUrl);
    }
  } catch {
    /* ignore */
  }
  if (!linkToken) return;
  try {
    const resp = await fetch('/api/auth/exchange', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ token: linkToken }),
    });
    if (!resp.ok) return;
    const data = (await resp.json()) as ExchangeResponse;
    if (data && data.session_token) {
      try {
        localStorage.setItem(STORAGE_KEY, data.session_token);
      } catch {
        /* ignore */
      }
    }
  } catch {
    /* network/parse error — leave session unset so LoginGate prompts user */
  }
}

export function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  try {
    const t = localStorage.getItem(STORAGE_KEY);
    return t && t.trim().length > 0 ? t : null;
  } catch {
    return null;
  }
}

export function clearToken(): void {
  if (typeof window === 'undefined') return;
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    /* ignore */
  }
  // Also expire the cookie so a stale cookie does not silently re-auth.
  try {
    document.cookie = 'ro_session=; Path=/; Max-Age=0; SameSite=Lax';
  } catch {
    /* ignore */
  }
}

export function setUnauthorizedHandler(h: UnauthorizedHandler | null): void {
  onUnauthorized = h;
}

export function notifyUnauthorized(): void {
  clearToken();
  if (onUnauthorized) onUnauthorized();
}

export function authHeaders(extra?: HeadersInit): HeadersInit {
  const token = getToken();
  const base: Record<string, string> = {};
  if (token) base.Authorization = `Bearer ${token}`;
  return { ...base, ...(extra as Record<string, string> | undefined) };
}

export interface WhoAmIResponse {
  uuid: string;
  name: string;
  admin: boolean;
  authMode?: string;
}
