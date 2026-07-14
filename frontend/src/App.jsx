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
import Landing from "./components/landing/Landing.jsx";
import Dashboard from "./components/dashboard/Dashboard.jsx";

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

  return endpoint ? (
    <Dashboard
      endpoint={endpoint}
      connected={connected}
      requests={requests}
      relayUrl={relayUrl}
      replayTarget={replayTarget}
      setReplayTarget={setReplayTarget}
      replayResult={replayResult}
      diffIds={diffIds}
      diffResult={diffResult}
      onNew={newEndpoint}
      onReplay={onReplay}
      onToggleDiff={toggleDiff}
      onDiff={onDiff}
    />
  ) : (
    <Landing onCreate={newEndpoint} />
  );
}
