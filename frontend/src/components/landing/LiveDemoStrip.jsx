import { useState } from "react";
import Eyebrow from "../ui/Eyebrow.jsx";
import CopyButton from "../ui/CopyButton.jsx";
import Stamp from "../ui/Stamp.jsx";

const DEMO_URL = "https://relay.webhookrelay.dev/demo";
const CURL = `curl -X POST ${DEMO_URL} \\
  -H "Content-Type: application/json" \\
  -d '{"event":"ping","ok":true}'`;

const FAKE_RESPONSE = `{
  "received": true,
  "method": "POST",
  "echo": { "event": "ping", "ok": true },
  "stamped_at": "2026-07-14T12:04:33Z"
}`;

export default function LiveDemoStrip() {
  const [sent, setSent] = useState(false);
  const [loading, setLoading] = useState(false);

  // No live backend wired for the demo: this is a convincing mock of the relay response.
  const send = () => {
    setLoading(true);
    setSent(false);
    setTimeout(() => {
      setLoading(false);
      setSent(true);
    }, 650);
  };

  return (
    <section className="border-y border-white/5 bg-panel/40">
      <div className="max-w-6xl mx-auto px-5 py-12">
        <Eyebrow>Try it</Eyebrow>
        <h2 className="font-display font-semibold text-2xl text-paper mt-2">
          Fire a request at the demo endpoint
        </h2>
        <p className="text-muted mt-2 max-w-lg text-sm">
          The demo relay captures and echoes your request back — exactly what you'd
          see in your own live feed.
        </p>

        <div className="mt-6 grid lg:grid-cols-2 gap-5">
          <div className="bg-ink rounded-lg border border-white/10 p-4">
            <div className="flex items-center justify-between mb-2">
              <span className="font-display uppercase tracking-[0.2em] text-[0.62rem] font-semibold text-muted/70">
                Send
              </span>
              <CopyButton value={CURL} label="Copy curl" />
            </div>
            <pre className="font-mono text-[0.78rem] text-paper/90 whitespace-pre-wrap break-words">
{CURL}
            </pre>
            <button
              onClick={send}
              disabled={loading}
              className="mt-4 bg-brass text-ink font-semibold text-sm px-4 py-2 rounded-md hover:brightness-110 transition disabled:opacity-50"
            >
              {loading ? "Sending…" : "Send request"}
            </button>
          </div>

          <div className="bg-paper text-ink rounded-lg border border-black/10 p-4 min-h-[180px]">
            <div className="flex items-center justify-between mb-2">
              <span className="font-display uppercase tracking-[0.2em] text-[0.62rem] font-semibold text-ink/50">
                Response
              </span>
              {sent && <Stamp code={200} label="200 OK" />}
            </div>
            {sent ? (
              <pre
                key={sent}
                className="font-mono text-[0.78rem] text-ink/80 whitespace-pre-wrap break-words"
              >
{FAKE_RESPONSE}
              </pre>
            ) : (
              <p className="font-mono text-sm text-ink/40">Awaiting request…</p>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
