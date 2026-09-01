import { cookies } from "next/headers";
import { NextResponse } from "next/server";

const backendUrl = process.env.BACKEND_INTERNAL_URL ?? "http://localhost:8080/api";

export async function proxyFetch(req: Request, segments: string[]) {
  try {
    const cookieStore = await cookies();
    const token = cookieStore.get("auth_token")?.value;
    const incomingUrl = new URL(req.url);
    const path = segments.map(encodeURIComponent).join("/");
    const headers = new Headers();
    const contentType = req.headers.get("content-type");
    if (contentType) headers.set("Content-Type", contentType);
    if (token) headers.set("Authorization", `Bearer ${token}`);

    const hasBody = !["GET", "HEAD"].includes(req.method);
    const response = await fetch(`${backendUrl}/${path}${incomingUrl.search}`, {
      method: req.method,
      headers,
      body: hasBody ? await req.arrayBuffer() : undefined,
      cache: "no-store",
    });

    const body = response.status === 204 ? null : await response.arrayBuffer();
    const nextResponse = new NextResponse(body, {
      status: response.status,
      headers: { "Content-Type": response.headers.get("content-type") ?? "application/json" },
    });
    const setCookie = response.headers.get("set-cookie");
    if (setCookie) nextResponse.headers.set("set-cookie", setCookie);
    return nextResponse;
  } catch (error) {
    return NextResponse.json({ message: "Backend indisponível", detail: String(error) }, { status: 502 });
  }
}
