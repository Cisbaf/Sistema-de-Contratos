export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, {
    ...init,
    credentials: "include",
    headers: { ...(init?.body ? { "Content-Type": "application/json" } : {}), ...init?.headers },
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => null) as { message?: string } | null;
    throw new ApiError(response.status, payload?.message ?? `Erro ${response.status}`);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const getJson = <T,>(path: string) => apiRequest<T>(path, { cache: "no-store" });
export const postJson = <T,>(path: string, body?: unknown) => apiRequest<T>(path, { method: "POST", body: body === undefined ? undefined : JSON.stringify(body) });
export const putJson = <T,>(path: string, body: unknown) => apiRequest<T>(path, { method: "PUT", body: JSON.stringify(body) });
export const deleteJson = (path: string) => apiRequest<void>(path, { method: "DELETE" });
