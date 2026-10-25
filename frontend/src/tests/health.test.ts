import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchHealth } from '../api/health';

afterEach(() => vi.unstubAllGlobals());

describe('healthcheck API', () => {
  it('odczytuje stan backendu i bazy', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 'UP', application: 'MycoMap', stage: 1 }))));
    await expect(fetchHealth()).resolves.toEqual({ status: 'UP', application: 'MycoMap', stage: 1 });
  });
  it('nie traktuje błędu HTTP jako działającego API', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 503 })));
    await expect(fetchHealth()).rejects.toThrow('Nie udało się połączyć');
  });
  it('odrzuca niezgodny kontrakt odpowiedzi', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 'UP' }))));
    await expect(fetchHealth()).rejects.toThrow('Nieoczekiwana odpowiedź');
  });
});
