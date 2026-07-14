import Eyebrow from "../ui/Eyebrow.jsx";
import CopyButton from "../ui/CopyButton.jsx";

const CURL = `# Capture a request
curl -X POST https://relay.webhookrelay.dev/r/8fK2 \\
  -H "Content-Type: application/json" \\
  -d '{"order_id": 8842, "total": 19.99}'`;

const JS = `// Listen for live arrivals
const res = await fetch("/api/endpoints", { method: "POST" });
const ep = await res.json();

const ws = new WebSocket(` + "`wss://relay.webhookrelay.dev/ws`" + `);
ws.onmessage = (e) => {
  const req = JSON.parse(e.data);
  console.log(req.method, req.body);
};`;

export default function CodeSamplePanel() {
  return (
    <section className="max-w-6xl mx-auto px-5 py-16">
      <Eyebrow>For developers</Eyebrow>
      <h2 className="font-display font-semibold text-2xl text-paper mt-2">
        Copy the commands you'll actually use
      </h2>
      <div className="mt-8 grid lg:grid-cols-2 gap-5">
        <div className="bg-ink rounded-lg border border-white/10 p-4">
          <div className="flex items-center justify-between mb-3">
            <span className="font-display uppercase tracking-[0.2em] text-[0.62rem] font-semibold text-muted/70">
              curl
            </span>
            <CopyButton value={CURL} />
          </div>
          <pre className="font-mono text-[0.78rem] text-paper/90 whitespace-pre-wrap break-words">
{CURL}
          </pre>
        </div>
        <div className="bg-ink rounded-lg border border-white/10 p-4">
          <div className="flex items-center justify-between mb-3">
            <span className="font-display uppercase tracking-[0.2em] text-[0.62rem] font-semibold text-muted/70">
              javascript
            </span>
            <CopyButton value={JS} />
          </div>
          <pre className="font-mono text-[0.78rem] text-paper/90 whitespace-pre-wrap break-words">
{JS}
          </pre>
        </div>
      </div>
    </section>
  );
}
