import { mutate, request } from '../../api/http';
import type { Page } from '../maps/api';

export type Precision = 'COARSE' | 'EXACT';
export type UserMatch = { id: string; username: string };
export type Share = { id: string; username: string; precision: Precision; createdAt: string };
export const searchUsers = (query: string) => request<UserMatch[]>(`/api/users/search?${new URLSearchParams({query})}`);
export const shares = (map: string, page: number) => request<Page<Share>>(`/api/maps/${map}/shares?page=${page}&size=20`);
export const createShare = (map: string, username: string, precision: Precision, exactConfirmed: boolean) => mutate<Share>(`/api/maps/${map}/shares`, {username,precision,exactConfirmed});
export const changePrecision = (map: string, id: string, precision: Precision, exactConfirmed: boolean) => mutate<Share>(`/api/maps/${map}/shares/${id}`, {precision,exactConfirmed}, 'PATCH');
export const revokeShare = (map: string, id: string) => mutate<void>(`/api/maps/${map}/shares/${id}`, undefined, 'DELETE');
