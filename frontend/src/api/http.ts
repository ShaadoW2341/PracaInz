export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public fieldErrors: Record<string, string> = {}) {
    super(message);
  }
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, { ...options, credentials: 'same-origin', signal: options.signal ?? AbortSignal.timeout(10000) });
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'Nie można połączyć się z serwerem. Sprawdź połączenie i spróbuj ponownie.');
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(response.status, body?.code ?? 'HTTP_ERROR', body?.message ?? 'Nie udało się wykonać żądania.', body?.fieldErrors ?? {});
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export async function mutate<T>(path: string, body?: unknown): Promise<T> {
  // Token jest pobierany przed każdą mutacją, również po zmianie sesji i po CSRF_INVALID.
  // Nie ponawiamy automatycznie POST — operacja mogła zostać już wykonana przed błędem sieci.
  const csrf = await request<{ token: string; headerName: string }>('/api/auth/csrf');
  return request<T>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}
