import { useState } from "react";
import Stamp from "../ui/Stamp.jsx";

function fmt(ts) {
  try {
    return new Date(ts).toLocaleTimeString();
  } catch {
    return ts;
  }
}

export default function RequestRow({
  req,
  selected,
  onToggleDiff,
  onReplay,
  replayResult,
}) {
  const [open, setOpen] = useState(false);

  return (
    <div className="row-arrive border border-white/10 rounded-lg bg-panel overflow-hidden">
      <div className="flex items-center gap-3 p-3">
        <Stamp code={null} label={req.method} className="shrink-0" />
        <div className="min-w-0 flex-1">
          <div className="font-mono text-sm text-paper truncate">
            {req.sourceIp} · {fmt(req.receivedAt)}
            {req.bodyTruncated && (
              <em className="text-muted not-italic"> (truncated)</em>
            )}
          </div>
          <div className="font-mono text-xs text-muted truncate">
            {req.bodyContentType || "no content-type"}
          </div>
        </div>
        <label className="flex items-center gap-1 text-xs text-muted shrink-0">
          <input
            type="checkbox"
            checked={selected}
            onChange={() => onToggleDiff(req.id)}
          />
          compare
        </label>
        <button
          onClick={() => setOpen((o) => !o)}
          className="font-mono text-xs text-brass hover:underline shrink-0"
          aria-expanded={open}
        >
          {open ? "hide" : "expand"}
        </button>
      </div>

      {open && (
        <div className="border-t border-white/10 p-3 space-y-3 bg-ink/40">
          <div>
            <div className="font-display uppercase tracking-[0.2em] text-[0.6rem] font-semibold text-muted/70 mb-1">
              Headers
            </div>
            <pre className="font-mono text-[0.72rem] text-paper/90 whitespace-pre-wrap break-words">
{JSON.stringify(req.headers || {}, null, 2)}
            </pre>
          </div>
          <div>
            <div className="font-display uppercase tracking-[0.2em] text-[0.6rem] font-semibold text-muted/70 mb-1">
              Body
            </div>
            <pre className="font-mono text-[0.72rem] text-paper/90 whitespace-pre-wrap break-words max-h-64 overflow-auto">
{req.body || "(empty)"}
            </pre>
          </div>
          <div className="flex items-start gap-3">
            <button
              onClick={() => onReplay(req.id)}
              className="bg-brass text-ink font-semibold text-xs px-3 py-1.5 rounded-md hover:brightness-110 transition"
            >
              Replay
            </button>
            {replayResult && replayResult.requestId === req.id && (
              <pre className="font-mono text-[0.7rem] text-paper/80 whitespace-pre-wrap break-words flex-1">
{replayResult.error
  ? replayResult.error
  : JSON.stringify(replayResult, null, 2)}
              </pre>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
