import { mutate, request } from '../../api/http';

export type Photo = { id: string; width: number; height: number; sizeBytes: number };
export const photos = (observationId: string) => request<Photo[]>(`/api/observations/${observationId}/photos`);
export const photoUrl = (id: string, thumbnail = false) => `/api/photos/${id}${thumbnail ? '?thumbnail=true' : ''}`;
export const deletePhoto = (id: string) => mutate<void>(`/api/photos/${id}`, undefined, 'DELETE');

export async function uploadPhotos(observationId: string, files: File[]): Promise<Photo[]> {
  const csrf = await request<{ token: string; headerName: string }>('/api/auth/csrf');
  const data = new FormData();
  files.forEach(file => data.append('files', file));
  return request<Photo[]>(`/api/observations/${observationId}/photos`, {
    method: 'POST', headers: { [csrf.headerName]: csrf.token }, body: data,
    signal: AbortSignal.timeout(60000),
  });
}
