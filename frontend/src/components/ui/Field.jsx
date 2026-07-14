/**
 * A manifest "field" — a labeled data cell (METHOD / PATH / STATUS / RECEIVED).
 * Used wherever the product displays captured request data as a form, not decoration.
 */
export default function Field({ label, children, className = "" }) {
  return (
    <div className={`flex flex-col gap-1 ${className}`}>
      <span className="font-display uppercase tracking-[0.2em] text-[0.62rem] font-semibold text-muted/70">
        {label}
      </span>
      <span className="font-mono text-sm text-ink break-words">{children}</span>
    </div>
  );
}
