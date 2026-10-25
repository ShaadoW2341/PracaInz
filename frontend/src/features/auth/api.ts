import { ApiError, mutate, request } from '../../api/http';

export type User = { id: string; username: string; email: string };
export type Credentials = { username: string; password: string };
export type Registration = Credentials & { email: string };

export async function currentUser(): Promise<User | null> {
  try { return await request<User>('/api/auth/me'); }
  catch (error) {
    if (error instanceof ApiError && error.status === 401) return null;
    throw error;
  }
}
export const login = (credentials: Credentials) => mutate<User>('/api/auth/login', credentials);
export const register = (data: Registration) => mutate<User>('/api/auth/register', data);
export const logout = () => mutate<void>('/api/auth/logout');
export const verifyEmail = (token: string) => mutate<{ message: string }>('/api/auth/verify-email', { token });
export const resendVerification = (email: string) => mutate<{ message: string }>('/api/auth/resend-verification', { email });
