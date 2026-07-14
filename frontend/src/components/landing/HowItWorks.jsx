import Eyebrow from "../ui/Eyebrow.jsx";

const STEPS = [
  {
    n: "01",
    title: "Generate an endpoint",
    body: "One click mints a disposable URL. No signup, no token to manage.",
  },
  {
    n: "02",
    title: "Send any request to it",
    body: "Point Stripe, GitHub, or your own service at the URL. Any method, any payload.",
  },
  {
    n: "03",
    title: "Inspect it live",
    body: "Watch headers, body, and method arrive in real time. Replay or diff on the spot.",
  },
];

export default function HowItWorks() {
  return (
    <section id="how" className="max-w-6xl mx-auto px-5 py-16">
      <Eyebrow>How it works</Eyebrow>
      <h2 className="font-display font-semibold text-2xl text-paper mt-2">
        Three steps, then you're reading payloads
      </h2>
      <ol className="mt-8 grid md:grid-cols-3 gap-5">
        {STEPS.map((s) => (
          <li
            key={s.n}
            className="bg-panel rounded-xl border border-white/5 p-5"
          >
            <span className="font-display font-bold text-3xl text-brass/70">
              {s.n}
            </span>
            <h3 className="font-display font-semibold text-lg text-paper mt-3">
              {s.title}
            </h3>
            <p className="text-muted mt-2 text-sm leading-relaxed">{s.body}</p>
          </li>
        ))}
      </ol>
    </section>
  );
}
