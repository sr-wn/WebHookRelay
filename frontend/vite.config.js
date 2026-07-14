import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  // Where to forward API/WS/relay traffic in dev. Defaults to a locally running
  // Spring Boot backend; override with VITE_DEV_PROXY (e.g. your Render URL).
  const proxyTarget = env.VITE_DEV_PROXY || "http://localhost:8080";

  return {
    plugins: [react(), tailwindcss()],
    define: {
      global: "globalThis",
    },
    server: {
      port: 5173,
      proxy: {
        "/api": { target: proxyTarget, changeOrigin: true },
        "/ws": { target: proxyTarget, ws: true, changeOrigin: true },
        "/relay": { target: proxyTarget, changeOrigin: true },
      },
    },
  };
});
