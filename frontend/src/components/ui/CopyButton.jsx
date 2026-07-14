import { useState } from "react";

export default function CopyButton({ value, label = "Copy", className = "" }) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 1400);
    } catch {
      setCopied(false);
    }
  };

  return (
    <button
      type="button"
      onClick={copy}
      className={`font-mono text-xs px-2.5 py-1 rounded border border-brass/40 text-brass hover:bg-brass/10 transition-colors ${className}`}
    >
      {copied ? "Copied" : label}
    </button>
  );
}
