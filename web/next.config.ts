import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Sortie autonome : embarque uniquement les fichiers nécessaires au runtime
  // (server.js + node_modules élagués), condition de l'image Docker minimale
  // exigée par la spec §10.6.
  output: "standalone",
};

export default nextConfig;
