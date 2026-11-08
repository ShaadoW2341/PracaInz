import { mutate, request } from '../../api/http';
import type { Page } from '../maps/api';

export type Role = 'USER' | 'ADMIN';
export type AdminUser = { id: string; username: string; email: string; role: Role; enabled: boolean; emailVerified: boolean; createdAt: string };
export type AdminSpecies = { id: number; polishName: string; latinName: string | null; isActive: boolean };
export type SpeciesInput = Omit<AdminSpecies, 'id'>;

function params(search: string, page: number) { return new URLSearchParams({ search, page: String(page), size: '20' }); }
export const users = (search: string, page: number) => request<Page<AdminUser>>(`/api/admin/users?${params(search, page)}`);
export const updateUser = (id: string, body: { role: Role; enabled: boolean }) => mutate<AdminUser>(`/api/admin/users/${id}`, body, 'PATCH');
export const species = (search: string, page: number) => request<Page<AdminSpecies>>(`/api/admin/species?${params(search, page)}`);
export const saveSpecies = (body: SpeciesInput, id?: number) => mutate<AdminSpecies>(id ? `/api/admin/species/${id}` : '/api/admin/species', body, id ? 'PATCH' : 'POST');
