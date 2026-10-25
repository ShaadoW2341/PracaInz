import { afterEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { App } from '../app/App';

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

function show(path = '/app') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[path]}><App /></MemoryRouter></QueryClientProvider>);
}

function server() {
  let authenticated = false;
  const user = { id: 'test-user-id', username: 'LesnyStudent', email: 'student@example.com' };
  const mock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === '/api/auth/me') return Response.json(authenticated ? user : { code: 'UNAUTHENTICATED' }, { status: authenticated ? 200 : 401 });
    if (url === '/api/auth/csrf') return Response.json({ token: 'test-csrf', headerName: 'X-CSRF-TOKEN' });
    if (url === '/api/health') return Response.json({ status: 'UP', application: 'MycoMap', stage: 1 });
    if (url === '/api/auth/login') {
      expect(new Headers(init?.headers).get('X-CSRF-TOKEN')).toBe('test-csrf');
      if (JSON.parse(init?.body as string).password === 'bad-password') {
        return Response.json({ code: 'INVALID_CREDENTIALS', message: 'Nieprawidłowa nazwa użytkownika lub hasło.' }, { status: 401 });
      }
      authenticated = true;
      return Response.json(user);
    }
    if (url === '/api/auth/logout') {
      expect(new Headers(init?.headers).get('X-CSRF-TOKEN')).toBe('test-csrf');
      authenticated = false;
      return new Response(null, { status: 204 });
    }
    throw new Error('Nieoczekiwane żądanie testowe: ' + url);
  });
  vi.stubGlobal('fetch', mock);
  return mock;
}

it('przekierowuje niezalogowanego użytkownika z pulpitu do logowania', async () => {
  server(); show();
  expect(await screen.findByRole('heading', { name: 'Wróć na leśną ścieżkę' })).toBeInTheDocument();
  expect(screen.queryByText('Twoje leśne ścieżki.')).not.toBeInTheDocument();
});

it('pokazuje błąd hasła, loguje do pulpitu i usuwa dane konta po wylogowaniu', async () => {
  const mock = server(); show('/login');
  fireEvent.change(await screen.findByLabelText('Nazwa użytkownika'), { target: { value: 'LesnyStudent' } });
  fireEvent.change(screen.getByLabelText('Hasło'), { target: { value: 'bad-password' } });
  fireEvent.click(screen.getByRole('button', { name: 'Zaloguj się' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Nieprawidłowa nazwa użytkownika lub hasło.');
  await waitFor(() => expect(screen.getByRole('button', { name: 'Zaloguj się' })).toBeEnabled());
  fireEvent.change(screen.getByLabelText('Hasło'), { target: { value: 'Lesna-wyprawa-2026' } });
  fireEvent.click(screen.getByRole('button', { name: 'Zaloguj się' }));
  expect(await screen.findByText('LesnyStudent')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Wyloguj się' }));
  expect(await screen.findByRole('heading', { name: 'Wróć na leśną ścieżkę' })).toBeInTheDocument();
  expect(screen.queryByText('LesnyStudent')).not.toBeInTheDocument();
  expect(mock.mock.calls.some(([url]) => url === '/api/auth/logout')).toBe(true);
});

it('nie wysyła rejestracji, jeżeli potwierdzenie hasła jest inne', async () => {
  const mock = server(); show('/register');
  fireEvent.change(await screen.findByLabelText('Nazwa użytkownika'), { target: { value: 'NowyStudent' } });
  fireEvent.change(screen.getByLabelText('Adres e-mail'), { target: { value: 'new@example.com' } });
  fireEvent.change(screen.getByLabelText('Hasło'), { target: { value: 'Lesna-wyprawa-2026' } });
  fireEvent.change(screen.getByLabelText('Powtórz hasło'), { target: { value: 'Inne-haslo-2026' } });
  fireEvent.submit(screen.getByRole('button', { name: 'Utwórz konto' }).closest('form')!);
  expect(await screen.findByText('Hasła muszą być takie same.')).toBeInTheDocument();
  expect(mock.mock.calls.some(([url]) => url === '/api/auth/register')).toBe(false);
});

it('potwierdza adres dopiero po kliknięciu użytkownika i wysyła token w treści POST', async () => {
  const mock = server();
  const original = mock.getMockImplementation()!;
  const token = 'A'.repeat(43);
  mock.mockImplementation(async (url, init) => {
    if (url === '/api/auth/verify-email') {
      expect(JSON.parse(init?.body as string)).toEqual({ token });
      expect(new Headers(init?.headers).get('X-CSRF-TOKEN')).toBe('test-csrf');
      return Response.json({ message: 'Adres e-mail został potwierdzony.' });
    }
    return original(url, init);
  });
  show('/verify-email#token=' + token);
  const confirm = await screen.findByRole('button', { name: 'Potwierdź adres' });
  expect(mock.mock.calls.some(([url]) => url === '/api/auth/verify-email')).toBe(false);
  fireEvent.click(confirm);
  expect(await screen.findByRole('heading', { name: 'Adres potwierdzony' })).toBeInTheDocument();
});

it('po wygaśnięciu linku pozwala poprosić o nowy', async () => {
  const mock = server();
  const original = mock.getMockImplementation()!;
  mock.mockImplementation(async (url, init) => {
    if (url === '/api/auth/verify-email') return Response.json({ code: 'VERIFICATION_INVALID', message: 'Link wygasł. Poproś o nowy.' }, { status: 400 });
    if (url === '/api/auth/resend-verification') return Response.json({ message: 'Jeśli konto wymaga potwierdzenia, wysłaliśmy link.' });
    return original(url, init);
  });
  show('/verify-email#token=' + 'A'.repeat(43));
  fireEvent.click(await screen.findByRole('button', { name: 'Potwierdź adres' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Link wygasł.');
  await waitFor(() => expect(screen.getByRole('button', { name: 'Wyślij nowy link' })).toBeEnabled());
  fireEvent.change(screen.getByLabelText('Adres e-mail konta'), { target: { value: 'student@example.com' } });
  fireEvent.submit(screen.getByRole('button', { name: 'Wyślij nowy link' }).closest('form')!);
  expect(await screen.findByRole('status')).toHaveTextContent('wysłaliśmy link');
});
