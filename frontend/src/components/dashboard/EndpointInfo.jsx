import { useEffect, useState } from "react";
import CopyButton from "../ui/CopyButton.jsx";
import Field from "../ui/Field.jsx";
import { API_BASE } from "../../api.js";

function useCountdown(expiresAt) {
  const [remaining, setRemaining] = useState(() =>
    expiresAt ? Math.max(0, new Date(expiresAt).getTime() - Date.now()) : 0
  );
  useEffect(() => {
    if (!expiresAt) return;
    const t = setInterval(() => {
      setRemaining(Math.max(0, new Date(expiresAt).getTime() - Date.now()));
    }, 1000);
    return () => clearInterval(t);
  }, [expiresAt]);

  const m = Math.floor(remaining / 60000);
  const s = Math.floor((remaining % 60000) / 1000);
  return `${m}m ${String(s).padStart(2, "0")}s`;
}

/**
 * The endpoint "manifest" — paper surface, visually distinct from the dark app chrome.
 */
export default function EndpointInfo({ endpoint, connected, onNew }) {
  const url = `${API_BASE}/relay/${endpoint.slug}`;
  const countdown = useCountdown(endpoint.expiresAt);

  return (
    <aside className="bg-paper text-ink rounded-xl border border-black/10 p-5 shadow-xl">
      <div className="flex items-center justify-between">
        <span className="font-display uppercase tracking-[0.2em] text-[0.62rem] font-semibold text-ink/50">
          Endpoint manifest
        </span>
        <span
          className={`font-mono text-xs ${connected ? "text-signal" : "text-ink/40"}`}
        >
          {connected ? "● live" : "○ connecting"}
        </span>
      </div>

      <dl className="mt-4 space-y-4">
        <Field label="Slug">
          <span className="break-all">{endpoint.slug}</span>
        </Field>
        <div>
          <span className="font-display uppercase tracking-[0.2em] text-[0.62rem] font-semibold text-ink/50">
            URL
          </span>
          <div className="mt-1 flex items-center gap-2">
            <code className="font-mono text-xs bg-ink/5 rounded px-2 py-1 break-all flex-1">
              {url}
            </code>
            <CopyButton value={url} />
          </div>
        </div>
        <Field label="Expires in">{countdown}</Field>
      </dl>

      <button
        onClick={onNew}
        className="mt-5 w-full bg-ink text-paper font-semibold text-sm py-2 rounded-md hover:bg-ink/90 transition"
      >
        New endpoint
      </button>
    </aside>
  );
}
