import { mutate, request } from '../../api/http';

export type MushroomMap = { id: string; name: string; description: string; isDefault: boolean; observationCount: number };
export type Species = { id: number; polishName: string; latinName: string | null };
export type Observation = { id: string; mapId: string; speciesId: number; speciesName: string; latinName: string | null; quantity: number; observedOn: string; description: string; latitude: number; longitude: number };
export type ObservationInput = Pick<Observation, 'speciesId' | 'quantity' | 'observedOn' | 'description' | 'latitude' | 'longitude'>;
export type Page<T> = { items: T[]; total: number; page: number; size: number };
export type Filters = { speciesId: string; from: string; to: string };
export type Features = { type: 'FeatureCollection'; features: { type: 'Feature'; id: string; geometry: { type: 'Point'; coordinates: [number, number] }; properties: { id: string; speciesName: string; quantity: number; observedOn: string } }[]; truncated: boolean; limit: number };

function query(filters: Filters, extra: Record<string,string> = {}) {
  const params = new URLSearchParams(extra);
  for (const [key,value] of Object.entries(filters)) if (value) params.set(key,value);
  return params.toString();
}
export const maps = (page: number) => request<Page<MushroomMap>>(`/api/maps?page=${page}&size=50`);
export const getMap = (id: string) => request<MushroomMap>(`/api/maps/${id}`);
export const species = () => request<Species[]>('/api/species');
export const saveMap = (body: { name: string; description: string }, id?: string) => mutate<MushroomMap>(id ? `/api/maps/${id}` : '/api/maps', body, id ? 'PATCH' : 'POST');
export const deleteMap = (id: string) => mutate<void>(`/api/maps/${id}`, undefined, 'DELETE');
export const observations = (id: string, filters: Filters, page: number) => request<Page<Observation>>(`/api/maps/${id}/observations?${query(filters,{page:String(page),size:'20'})}`);
export const getObservation = (id: string) => request<Observation>(`/api/observations/${id}`);
export const geoJson = (id: string, filters: Filters, bbox: string) => request<Features>(`/api/maps/${id}/observations/geojson?${query(filters,{bbox})}`);
export const saveObservation = (mapId: string, body: ObservationInput, id?: string) => mutate<Observation>(id ? `/api/observations/${id}` : `/api/maps/${mapId}/observations`, body, id ? 'PATCH' : 'POST');
export const deleteObservation = (id: string) => mutate<void>(`/api/observations/${id}`, undefined, 'DELETE');
