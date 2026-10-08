import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  // Permite que o dev server aceite requisições (assets e HMR) vindas de
  // outras máquinas da rede interna, acessando pelo IP deste computador.
  // Sem isso, o Next 16 bloqueia com 403 tudo que não vier de localhost.
  allowedDevOrigins: ["192.168.1.10", "*.local"],
};

export default nextConfig;
