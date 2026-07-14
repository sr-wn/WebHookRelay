import Eyebrow from "../ui/Eyebrow.jsx";

const FEATURES = [
  {
    title: "Real-time push",
    body: "Requests land in your feed the instant they arrive — pushed over a live connection, not polled on a timer.",
  },
  {
    title: "Request replay",
    body: "Resend any captured request to a target URL with one click. Re-test a flaky integration without regenerating the payload.",
  },
  {
    title: "Side-by-side diff",
    body: "Select two requests and compare method, headers, and body. Spot exactly what changed between deliveries.",
  },
  {
    title: "Auto-expiry",
    body: "Endpoints expire on their own. No orphaned listeners, no cleanup — generate a fresh one whenever you need.",
  },
];

export default function FeatureGrid() {
  return (
    <section className="max-w-6xl mx-auto px-5 py-8">
      <Eyebrow>What you can do</Eyebrow>
      <h2 className="font-display font-semibold text-2xl text-paper mt-2">
        Built for reading what actually arrived
      </h2>
      <div className="mt-8 grid sm:grid-cols-2 gap-4">
        {FEATURES.map((f) => (
          <div
            key={f.title}
            className="bg-panel rounded-xl border border-white/5 p-5"
          >
            <h3 className="font-display font-semibold text-lg text-paper">
              {f.title}
            </h3>
            <p className="text-muted mt-2 text-sm leading-relaxed">{f.body}</p>
          </div>
        ))}
      </div>
    </section>
  );
}
