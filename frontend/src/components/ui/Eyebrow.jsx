export default function Eyebrow({ children, className = "" }) {
  return (
    <span
      className={`font-display uppercase tracking-[0.25em] text-[0.7rem] font-semibold text-brass/80 ${className}`}
    >
      {children}
    </span>
  );
}
