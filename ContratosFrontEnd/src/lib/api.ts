export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

type ApiErrorPayload = {
  message?: string;
  fields?: Record<string, string>;
};

function errorMessage(payload: ApiErrorPayload | null, fallback: string) {
  const fieldError = payload?.fields && Object.values(payload.fields).find(Boolean);
  return fieldError ?? payload?.message ?? fallback;
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, {
    ...init,
    credentials: "include",
    headers: { ...(init?.body ? { "Content-Type": "application/json" } : {}), ...init?.headers },
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => null) as ApiErrorPayload | null;
    throw new ApiError(response.status, errorMessage(payload, `Erro ${response.status}`));
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export async function downloadFile(path: string, fallbackFileName: string): Promise<void> {
  const response = await fetch(`/api${path}`, { credentials: "include" });

  if (!response.ok) {
    const payload = await response.json().catch(() => null) as ApiErrorPayload | null;
    throw new ApiError(response.status, errorMessage(payload, `Erro ${response.status}`));
  }

  const blob = await response.blob();
  const url = URL.createObjectURL(blob);

  const link = document.createElement("a");
  link.href = url;
  link.download = fallbackFileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);

  URL.revokeObjectURL(url);
}

export const getJson = <T,>(path: string) => apiRequest<T>(path, { cache: "no-store" });
export const postJson = <T,>(path: string, body?: unknown) => apiRequest<T>(path, { method: "POST", body: body === undefined ? undefined : JSON.stringify(body) });
export const putJson = <T,>(path: string, body: unknown) => apiRequest<T>(path, { method: "PUT", body: JSON.stringify(body) });
export const deleteJson = (path: string) => apiRequest<void>(path, { method: "DELETE" });
