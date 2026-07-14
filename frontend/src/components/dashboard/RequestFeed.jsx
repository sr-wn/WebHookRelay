import RequestRow from "./RequestRow.jsx";

export default function RequestFeed({
  requests,
  total,
  relayUrl,
  diffIds,
  onToggleDiff,
  onReplay,
  replayResult,
  onDiff,
  diffResult,
}) {
  if (total === 0) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center text-center py-20">
        <code className="font-mono text-paper/90 text-lg break-all max-w-xl px-2">
          {relayUrl}
        </code>
        <p className="text-muted mt-4 max-w-sm">
          Send a request here to see it appear — live, with its headers and body.
        </p>
      </div>
    );
  }

  return (
    <div className="flex-1">
      <div className="flex items-center justify-between mb-3">
        <h2 className="font-display font-semibold text-xl text-paper">
          Captured requests
          <span className="text-muted font-body font-normal text-sm ml-2">
            {requests.length}
            {requests.length !== total ? ` / ${total}` : ""}
          </span>
        </h2>
        <button
          onClick={onDiff}
          disabled={diffIds.length !== 2}
          className="bg-brass text-ink font-semibold text-sm px-4 py-2 rounded-md hover:brightness-110 transition disabled:opacity-50"
        >
          Diff selected ({diffIds.length}/2)
        </button>
      </div>

      <div className="space-y-2.5">
        {requests.length === 0 ? (
          <p className="text-muted text-sm py-6 text-center">
            No requests match your filter.
          </p>
        ) : (
          requests.map((r) => (
          <RequestRow
            key={r.id}
            req={r}
            selected={diffIds.includes(r.id)}
            onToggleDiff={onToggleDiff}
            onReplay={onReplay}
            replayResult={replayResult}
          />
          ))
        )}
      </div>

      {diffResult && (
        <pre className="mt-4 font-mono text-[0.75rem] text-paper/85 bg-ink rounded-lg border border-white/10 p-4 whitespace-pre-wrap break-words">
{diffResult.error ? diffResult.error : JSON.stringify(diffResult, null, 2)}
        </pre>
      )}
    </div>
  );
}
