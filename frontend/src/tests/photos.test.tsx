import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PhotoPicker } from '../features/photos/PhotoPicker';
import { PhotoGallery } from '../features/photos/PhotoGallery';
import { ObservationForm } from '../features/maps/ObservationForm';
import * as photos from '../features/photos/api';
import * as maps from '../features/maps/api';
import type { ReactNode } from 'react';

beforeEach(() => {
  vi.stubGlobal('URL', class extends URL {
    static createObjectURL = vi.fn(() => 'blob:test-photo');
    static revokeObjectURL = vi.fn();
  });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });
function show(children: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } } });
  render(<QueryClientProvider client={client}>{children}</QueryClientProvider>);
}

it('odrzuca nieobsługiwany format, za duży plik i przekroczenie liczby zdjęć', () => {
  const change = vi.fn();
  show(<PhotoPicker files={[]} onChange={change} remaining={1} />);
  const input = screen.getByLabelText('Zdjęcia (opcjonalnie)');
  fireEvent.change(input, { target: { files: [new File(['<svg/>'], 'obraz.svg', { type: 'image/svg+xml' })] } });
  expect(screen.getByRole('alert')).toHaveTextContent('JPG lub PNG');
  fireEvent.change(input, { target: { files: [new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'duzy.jpg', { type: 'image/jpeg' })] } });
  expect(screen.getByRole('alert')).toHaveTextContent('5 MB');
  const png = new File(['photo'], 'grzyb.png', { type: 'image/png' });
  fireEvent.change(input, { target: { files: [png, png] } });
  expect(screen.getByRole('alert')).toHaveTextContent('jeszcze 1 zdjęć');
  expect(change).toHaveBeenLastCalledWith([]);
});

it('wysyła multipart z tokenem CSRF bez narzucania Content-Type', async () => {
  const fetch = vi.fn().mockResolvedValueOnce(Response.json({ token: 'csrf-test', headerName: 'X-CSRF-TOKEN' }))
    .mockResolvedValueOnce(Response.json([]));
  vi.stubGlobal('fetch', fetch);
  await photos.uploadPhotos('observation-id', [new File(['photo'], 'grzyb.png', { type: 'image/png' })]);
  const [url, options] = fetch.mock.calls[1];
  expect(url).toBe('/api/observations/observation-id/photos');
  expect(new Headers(options.headers).get('X-CSRF-TOKEN')).toBe('csrf-test');
  expect(new Headers(options.headers).has('Content-Type')).toBe(false);
  expect(options.body.getAll('files')).toHaveLength(1);
});

it('po błędzie zdjęcia ponawia zapis tego samego znaleziska zamiast tworzyć duplikat', async () => {
  const observation: maps.Observation = { id: 'saved-id', mapId: 'map-id', speciesId: 1, speciesName: 'Nieznany', latinName: null,
    observedOn: '2026-10-01', quantity: 1, description: '', latitude: 52, longitude: 23 };
  const save = vi.spyOn(maps, 'saveObservation').mockResolvedValue(observation);
  vi.spyOn(photos, 'uploadPhotos').mockRejectedValueOnce(new Error('Błąd wysyłania')).mockResolvedValueOnce([]);
  const done = vi.fn();
  show(<ObservationForm mapId="map-id" species={[{ id: 1, polishName: 'Nieznany', latinName: null }]}
    coordinates={{ latitude: 52, longitude: 23 }} onClose={() => {}} onSaved={done} />);
  fireEvent.change(screen.getByLabelText('Zdjęcia (opcjonalnie)'), { target: { files: [new File(['photo'], 'grzyb.png', { type: 'image/png' })] } });
  const form = screen.getByRole('button', { name: 'Zapisz znalezisko' }).closest('form')!;
  fireEvent.submit(form);
  expect(await screen.findByRole('status')).toHaveTextContent('Znalezisko jest zapisane');
  await waitFor(() => expect(screen.getByRole('button', { name: 'Zapisz znalezisko' })).toBeEnabled());
  fireEvent.submit(form);
  await waitFor(() => expect(done).toHaveBeenCalledOnce());
  expect(save.mock.calls[0][2]).toBeUndefined();
  expect(save.mock.calls[1][2]).toBe('saved-id');
});

it('odświeża galerię po dodaniu i usuwa zdjęcie dopiero po potwierdzeniu', async () => {
  let saved: photos.Photo[] = [];
  vi.spyOn(photos, 'photos').mockImplementation(async () => [...saved]);
  vi.spyOn(photos, 'uploadPhotos').mockImplementation(async () => {
    saved = [{ id: 'photo-id', width: 800, height: 400, sizeBytes: 5000 }]; return saved;
  });
  const remove = vi.spyOn(photos, 'deletePhoto').mockImplementation(async () => { saved = []; });
  show(<PhotoGallery observationId="observation-id" />);
  expect(await screen.findByText('To znalezisko nie ma jeszcze zdjęć.')).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Zdjęcia (opcjonalnie)'), { target: { files: [new File(['photo'], 'grzyb.png', { type: 'image/png' })] } });
  fireEvent.click(screen.getByRole('button', { name: 'Dodaj wybrane zdjęcia' }));
  expect(await screen.findByRole('button', { name: 'Powiększ zdjęcie 1' })).toBeInTheDocument();
  expect(screen.getByAltText('Zdjęcie znaleziska 1')).toHaveAttribute('src', '/api/photos/photo-id?thumbnail=true');
  fireEvent.click(screen.getByRole('button', { name: 'Usuń zdjęcie 1' }));
  expect(remove).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Usuń zdjęcie' }));
  await waitFor(() => expect(remove).toHaveBeenCalledWith('photo-id', expect.anything()));
  expect(await screen.findByText('To znalezisko nie ma jeszcze zdjęć.')).toBeInTheDocument();
});
