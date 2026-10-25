export type Health = { status: 'UP' | 'DOWN'; application: 'MycoMap'; stage: number };

export async function fetchHealth(): Promise<Health> {
  const response = await fetch('/api/health', { credentials: 'same-origin', signal: AbortSignal.timeout(5000) });
  if (!response.ok) throw new Error('Nie udało się połączyć z API i bazą danych.');
  const body: unknown = await response.json();
  if (typeof body !== 'object' || body === null || !('status' in body) || body.status !== 'UP'
      || !('application' in body) || body.application !== 'MycoMap' || !('stage' in body) || body.stage !== 0) {
    throw new Error('Nieoczekiwana odpowiedź serwera.');
  }
  return body as Health;
}
