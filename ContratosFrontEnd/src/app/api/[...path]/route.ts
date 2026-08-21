import { proxyFetch } from "@/lib/proxy-fetch";

type Context = { params: Promise<{ path: string[] }> };

async function handler(req: Request, context: Context) {
  const { path } = await context.params;
  return proxyFetch(req, path);
}

export const GET = handler;
export const POST = handler;
export const PUT = handler;
export const DELETE = handler;
