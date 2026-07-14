// API base is injected at build time (VITE_API_BASE). Falls back to same-origin.
// If VITE_API_BASE points at the current origin (e.g. the Vercel URL with a
// vercel.json rewrite in place), treat it as same-origin so relative paths work.
const rawBase = (import.meta.env.VITE_API_BASE || "").replace(/\/+$/, "");
export const API_BASE =
  rawBase && typeof window !== "undefined" && new URL(rawBase).origin === window.location.origin
    ? ""
    : rawBase;

const TOKEN_KEY = "webhookrelay_token";
const OWNER_KEY = "webhookrelay_owner";

// Lazily obtain a JWT. Account-less: the backend mints an ownerId (or reuses a
// stored one so a browser keeps owning its endpoints across reloads).
async function ensureToken() {
  const existing = localStorage.getItem(TOKEN_KEY);
  if (existing) return existing;

  const ownerId = localStorage.getItem(OWNER_KEY);
  const res = await fetch(`${API_BASE}/api/auth/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: ownerId ? JSON.stringify({ ownerId }) : "{}",
  });
  if (!res.ok) throw new Error("failed to obtain auth token");
  const data = await res.json();
  localStorage.setItem(TOKEN_KEY, data.token);
  localStorage.setItem(OWNER_KEY, data.ownerId);
  return data.token;
}

function authHeaders() {
  const token = localStorage.getItem(TOKEN_KEY);
  return token ? { Authorization: `Bearer ${token}` } : {};
}

// Fetch with bearer auth; on 401 (expired token) clear it and retry once.
async function authFetch(url, options = {}) {
  await ensureToken();
  let res = await fetch(url, {
    ...options,
    headers: { ...authHeaders(), ...(options.headers || {}) },
  });
  if (res.status === 401) {
    localStorage.removeItem(TOKEN_KEY);
    await ensureToken();
    res = await fetch(url, {
      ...options,
      headers: { ...authHeaders(), ...(options.headers || {}) },
    });
  }
  return res;
}

export async function createEndpoint() {
  const res = await authFetch(`${API_BASE}/api/endpoints`, { method: "POST" });
  if (!res.ok) throw new Error("failed to create endpoint");
  return res.json();
}

export async function fetchRequests(slug) {
  const res = await authFetch(`${API_BASE}/api/endpoints/${slug}/requests`);
  if (!res.ok) throw new Error("failed to fetch requests");
  return res.json();
}

// Anonymous, ownership-free read used by shared ("?e=slug") view links.
export async function fetchPublicRequests(slug) {
  const res = await fetch(`${API_BASE}/api/public/endpoints/${slug}/requests`);
  if (!res.ok) throw new Error("failed to fetch shared requests");
  return res.json();
}

export async function replayRequest(requestId, targetUrl) {
  const res = await authFetch(`${API_BASE}/api/replay`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ requestId, targetUrl }),
  });
  if (!res.ok) throw new Error("replay failed: " + res.status);
  return res.json();
}

export async function diffRequests(idA, idB) {
  const res = await authFetch(`${API_BASE}/api/replay/diff`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ idA, idB }),
  });
  if (!res.ok) throw new Error("diff failed: " + res.status);
  return res.json();
}
