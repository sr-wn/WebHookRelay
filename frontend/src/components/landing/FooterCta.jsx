export default function FooterCta({ onCreate }) {
  return (
    <footer className="border-t border-white/5 mt-8">
      <div className="max-w-6xl mx-auto px-5 py-16 flex flex-col items-center text-center">
        <h2 className="font-display font-semibold text-3xl text-paper">
          Generate your first endpoint
        </h2>
        <p className="text-muted mt-3 max-w-md text-sm">
          It takes one click and expires on its own. Send a request and read it
          the moment it lands.
        </p>
        <button
          onClick={onCreate}
          className="mt-6 bg-brass text-ink font-semibold px-6 py-3 rounded-md hover:brightness-110 transition"
        >
          Create inspection endpoint
        </button>
      </div>
    </footer>
  );
}
