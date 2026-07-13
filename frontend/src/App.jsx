import { useEffect, useState, useRef, useCallback } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import {
  API_BASE,
  createEndpoint,
  fetchRequests,
  replayRequest,
  diffRequests,
} from "./api.js";

export default function App() {
  const [endpoint, setEndpoint] = useState(null);
  const [requests, setRequests] = useState([]);
  const [connected, setConnected] = useState(false);
  const [replayTarget, setReplayTarget] = useState("https://example.com/webhook");
  const [replayResult, setReplayResult] = useState(null);
  const [diffIds, setDiffIds] = useState([]);
  const [diffResult, setDiffResult] = useState(null);
  const clientRef = useRef(null);

  const newEndpoint = useCallback(async () => {
    const ep = await createEndpoint();
    setEndpoint(ep);
    setRequests(await fetchRequests(ep.slug));
    setReplayResult(null);
    setDiffResult(null);
    setDiffIds([]);
  }, []);

  useEffect(() => {
    if (!endpoint) return;
    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE}/ws`),
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/topic/endpoints/${endpoint.slug}`, (msg) => {
          setRequests((prev) => [JSON.parse(msg.body), ...prev]);
        });
      },
      onDisconnect: () => setConnected(false),
    });
    client.activate();
    clientRef.current = client;
    return () => client.deactivate();
  }, [endpoint]);

  const onReplay = async (requestId) => {
    try {
      const result = await replayRequest(requestId, replayTarget);
      setReplayResult({ requestId, ...result });
    } catch (e) {
      setReplayResult({ requestId, error: e.message });
    }
  };

  const toggleDiff = (id) => {
    setDiffIds((prev) =>
      prev.includes(id)
        ? prev.filter((x) => x !== id)
        : prev.length >= 2
        ? [prev[1], id]
        : [...prev, id]
    );
    setDiffResult(null);
  };

  const onDiff = async () => {
    if (diffIds.length !== 2) return;
    try {
      const result = await diffRequests(diffIds[0], diffIds[1]);
      setDiffResult(result);
    } catch (e) {
      setDiffResult({ error: e.message });
    }
  };

  const relayUrl = endpoint ? `${API_BASE}/relay/${endpoint.slug}` : "";

  return (
    <main style={{ fontFamily: "monospace", maxWidth: 900, margin: "2rem auto", padding: "0 1rem" }}>
      <h1>WebhookRelay</h1>
      {!endpoint ? (
        <button onClick={newEndpoint}>Create inspection endpoint</button>
      ) : (
        <>
          <p>
            Send requests to: <code>{relayUrl}</code>{" "}
            <span style={{ color: connected ? "green" : "gray" }}>
              {connected ? "● live" : "○ connecting"}
            </span>
          </p>
          <button onClick={newEndpoint}>New endpoint</button>

          <h2>Captured requests ({requests.length})</h2>

          <p>
            Replay target URL:{" "}
            <input
              value={replayTarget}
              onChange={(e) => setReplayTarget(e.target.value)}
              style={{ width: 320 }}
            />
          </p>

          {requests.map((r) => (
            <div key={r.id} style={{ border: "1px solid #ccc", padding: "0.75rem", margin: "0.5rem 0" }}>
              <label style={{ float: "right" }}>
                <input
                  type="checkbox"
                  checked={diffIds.includes(r.id)}
                  onChange={() => toggleDiff(r.id)}
                />{" "}
                compare
              </label>
              <strong>{r.method}</strong> · {r.sourceIp} · {new Date(r.receivedAt).toLocaleTimeString()}
              {r.bodyTruncated && <em> (truncated)</em>}
              <pre style={{ whiteSpace: "pre-wrap", overflowX: "auto" }}>{r.body}</pre>
              <button onClick={() => onReplay(r.id)}>Replay</button>
              {replayResult && replayResult.requestId === r.id && (
                <pre style={{ background: "#f6f6f6" }}>
                  {replayResult.error ? replayResult.error : JSON.stringify(replayResult, null, 2)}
                </pre>
              )}
            </div>
          ))}

          <button onClick={onDiff} disabled={diffIds.length !== 2}>
            Diff selected ({diffIds.length}/2)
          </button>
          {diffResult && (
            <pre style={{ border: "1px solid #ccc", padding: "0.75rem", background: "#f6f6f6" }}>
              {diffResult.error ? diffResult.error : JSON.stringify(diffResult, null, 2)}
            </pre>
          )}
        </>
      )}
    </main>
  );
}
