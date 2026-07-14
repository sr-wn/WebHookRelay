import { useEffect, useState } from "react";
import Eyebrow from "../ui/Eyebrow.jsx";
import Stamp from "../ui/Stamp.jsx";

const SAMPLES = [
  { method: "POST", path: "/hooks/stripe", code: 200, label: "200 OK", received: "12:04:11" },
  { method: "GET", path: "/hooks/health", code: 200, label: "200 OK", received: "12:04:19" },
  { method: "POST", path: "/hooks/shopify", code: 401, label: "401", received: "12:04:27" },
  { method: "PUT", path: "/hooks/github", code: 202, label: "202", received: "12:04:33" },
];

export default function Hero({ onCreate }) {
  const [idx, setIdx] = useState(0);

  useEffect(() => {
    const t = setInterval(() => setIdx((i) => (i + 1) % SAMPLES.length), 3500);
    return () => clearInterval(t);
  }, []);

  const sample = SAMPLES[idx];

  return (
    <section className="max-w-6xl mx-auto px-5 pt-10 pb-16 grid md:grid-cols-2 gap-10 items-center">
      <div>
        <Eyebrow>Real-time webhook inspection</Eyebrow>
        <h1 className="font-display font-bold text-5xl md:text-6xl leading-[0.95] mt-4 text-paper">
          Send anything.
          <br />
          Watch it land.
        </h1>
        <p className="text-muted mt-5 max-w-md text-[0.98rem] leading-relaxed">
          WebhookRelay gives you a disposable endpoint that captures every request —
          headers, body, method — the moment it arrives. Replay it, or diff two side
          by side. No account, no setup.
        </p>
        <div className="mt-7 flex items-center gap-3">
          <button
            onClick={onCreate}
            className="bg-brass text-ink font-semibold font-body px-5 py-2.5 rounded-md hover:brightness-110 transition"
          >
            Create inspection endpoint
          </button>
          <a
            href="#how"
            className="text-muted font-body text-sm hover:text-paper transition"
          >
            See how it works ↓
          </a>
        </div>
      </div>

      {/* Animated manifest ticket */}
      <div className="relative">
        <div className="absolute -inset-3 rounded-2xl bg-brass/5 blur-xl" aria-hidden />
        <div
          key={idx}
          className="row-arrive relative bg-paper text-ink rounded-xl shadow-2xl border border-black/10 p-5"
        >
          <div className="flex items-center justify-between border-b border-black/10 pb-3">
            <span className="font-mono text-xs text-ink/60">relay.webhookrelay.dev/r/8fK2</span>
            <Stamp code={sample.code} label={sample.label} />
          </div>
          <dl className="grid grid-cols-2 gap-x-4 gap-y-3 mt-4">
            <div>
              <dt className="font-display uppercase tracking-[0.2em] text-[0.6rem] font-semibold text-ink/50">
                Method
              </dt>
              <dd className="font-mono text-sm mt-0.5">{sample.method}</dd>
            </div>
            <div>
              <dt className="font-display uppercase tracking-[0.2em] text-[0.6rem] font-semibold text-ink/50">
                Received
              </dt>
              <dd className="font-mono text-sm mt-0.5">{sample.received}</dd>
            </div>
            <div className="col-span-2">
              <dt className="font-display uppercase tracking-[0.2em] text-[0.6rem] font-semibold text-ink/50">
                Path
              </dt>
              <dd className="font-mono text-sm mt-0.5 break-all">{sample.path}</dd>
            </div>
          </dl>
          <div className="mt-4 bg-ink/5 rounded-md p-3">
            <pre className="font-mono text-[0.72rem] text-ink/80 whitespace-pre-wrap break-words">
{`{
  "event": "charge.succeeded",
  "amount": 4200,
  "currency": "usd"
}`}
            </pre>
          </div>
        </div>
      </div>
    </section>
  );
}
