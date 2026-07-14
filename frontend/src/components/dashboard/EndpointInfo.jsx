import { useEffect, useState } from "react";
import CopyButton from "../ui/CopyButton.jsx";
import { API_BASE } from "../../api.js";

function useCountdown(expiresAt) {
  const [remaining, setRemaining] = useState(() =>
    expiresAt ? Math.max(0, new Date(expiresAt).getTime() - Date.now()) : 0
  );
  useEffect(() => {
    if (!expiresAt) return;
    const tick = () => setRemaining(Math.max(0, new Date(expiresAt).getTime() - Date.now()));
    tick();
    const t = setInterval(tick, 1000);
    return () => clearInterval(t);
  }, [expiresAt]);

  return expiresAt ? remaining : null;
}

function fmtCountdown(ms) {
  const m = Math.floor(ms / 60000);
  const s = Math.floor((ms % 60000) / 1000);
  return `${m}m ${String(s).padStart(2, "0")}s`;
}

/**
 * The endpoint "manifest" — paper surface, visually distinct from the dark app chrome.
 */
export default function EndpointInfo({ endpoint, connected, onNew }) {
  const remaining = useCountdown(endpoint.expiresAt);
  const [copied, setCopied] = useState(false);

  const url = `${API_BASE}/relay/${endpoint.slug}`;
  const shareUrl = `${window.location.origin}/?e=${endpoint.slug}`;

  const copyShare = async () => {
    try {
      await navigator.clipboard.writeText(shareUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 1400);
    } catch {
      setCopied(false);
    }
  };

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
        <div>
          <span className="font-display uppercase tracking-[0.2em] text-[0.62rem] font-semibold text-ink/50">
            Slug
          </span>
          <div className="mt-1 font-mono text-sm break-all">{endpoint.slug}</div>
        </div>
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
        <div>
          <span className="font-display uppercase tracking-[0.2em] text-[0.62rem] font-semibold text-ink/50">
            {endpoint.shared ? "Sharing" : "Expires in"}
          </span>
          <div className="mt-1 font-mono text-sm">
            {endpoint.shared
              ? "anyone with the link"
              : remaining === null
              ? "—"
              : remaining <= 0
              ? "expired"
              : fmtCountdown(remaining)}
          </div>
        </div>
      </dl>

      <div className="mt-5 flex flex-col gap-2">
        <button
          onClick={copyShare}
          className="w-full bg-brass text-ink font-semibold text-sm py-2 rounded-md hover:brightness-110 transition"
        >
          {copied ? "Link copied" : "Copy share link"}
        </button>
        {!endpoint.shared && (
          <button
            onClick={onNew}
            className="w-full bg-ink text-paper font-semibold text-sm py-2 rounded-md hover:bg-ink/90 transition"
          >
            New endpoint
          </button>
        )}
      </div>
    </aside>
  );
}
