import EndpointInfo from "./EndpointInfo.jsx";
import RequestFeed from "./RequestFeed.jsx";

export default function Dashboard({
  endpoint,
  connected,
  requests,
  relayUrl,
  replayTarget,
  setReplayTarget,
  replayResult,
  diffIds,
  diffResult,
  onNew,
  onReplay,
  onToggleDiff,
  onDiff,
}) {
  return (
    <div className="max-w-6xl mx-auto px-5 py-8">
      <header className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-2">
          <span className="w-3 h-3 rounded-full bg-gradient-to-br from-brass to-signal" />
          <span className="font-display font-bold text-lg text-paper">
            WebhookRelay
          </span>
          <span className="font-body text-muted text-sm">inspection dashboard</span>
        </div>
      </header>

      <div className="grid lg:grid-cols-[320px_1fr] gap-6 items-start">
        <EndpointInfo endpoint={endpoint} connected={connected} onNew={onNew} />

        <div className="flex flex-col gap-4">
          <div className="bg-panel rounded-xl border border-white/5 p-4">
            <label className="font-display uppercase tracking-[0.2em] text-[0.62rem] font-semibold text-muted/70">
              Replay target URL
            </label>
            <input
              type="url"
              value={replayTarget}
              onChange={(e) => setReplayTarget(e.target.value)}
              className="mt-2 w-full bg-ink border border-white/10 rounded-md px-3 py-2 font-mono text-sm text-paper focus:border-brass"
              placeholder="https://example.com/webhook"
            />
          </div>

          <RequestFeed
            requests={requests}
            relayUrl={relayUrl}
            diffIds={diffIds}
            onToggleDiff={onToggleDiff}
            onReplay={onReplay}
            replayResult={replayResult}
            onDiff={onDiff}
            diffResult={diffResult}
          />
        </div>
      </div>
    </div>
  );
}
