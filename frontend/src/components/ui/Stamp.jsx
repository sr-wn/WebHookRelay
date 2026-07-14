function toneFor(code) {
  const n = Number(code);
  if (!code) return "text-brass border-brass";
  if (n >= 200 && n < 300) return "text-signal border-signal";
  if (n >= 400) return "text-alert border-alert";
  return "text-brass border-brass";
}

/**
 * The signature element: an ink stamp that thuds onto a surface once on mount.
 * Rotated, outlined, mono — like a customs stamp hitting a manifest.
 */
export default function Stamp({ code, label, className = "" }) {
  const tone = toneFor(code);
  const text = label || (code ? `${code}` : "OK");

  return (
    <span
      className={`stamp inline-block select-none border-2 rounded-md px-2 py-0.5 font-mono font-semibold text-sm leading-none ${tone} ${className}`}
      style={{ transform: "rotate(-10deg)" }}
      aria-label={`status ${text}`}
    >
      {text}
    </span>
  );
}
