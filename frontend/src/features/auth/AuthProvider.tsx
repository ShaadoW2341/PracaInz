import { createContext, useContext, useEffect, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import * as api from './api';

type Auth = {
  user: api.User | null | undefined;
  isPending: boolean;
  isError: boolean;
  refresh: () => void;
  login: (credentials: api.Credentials) => Promise<void>;
  logout: () => Promise<void>;
};
const Context = createContext<Auth | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const client = useQueryClient();
  const session = useQuery({ queryKey: ['me'], queryFn: api.currentUser, retry: false, staleTime: 0 });
  useEffect(() => {
    const expired = () => {
      void client.cancelQueries({ predicate: query => query.queryKey[0] !== 'me' }).then(() => {
        client.removeQueries({ predicate: query => query.queryKey[0] !== 'me' });
        client.setQueryData(['me'], null);
      });
    };
    window.addEventListener('mycomap:session-expired', expired);
    return () => window.removeEventListener('mycomap:session-expired', expired);
  }, [client]);
  async function setUser(user: api.User | null) {
    await client.cancelQueries();
    client.removeQueries({ predicate: query => query.queryKey[0] !== 'me' });
    client.setQueryData(['me'], user);
  }
  return <Context.Provider value={{
    user: session.data, isPending: session.isPending, isError: session.isError,
    refresh: () => { void session.refetch(); },
    login: async credentials => { await setUser(await api.login(credentials)); },
    logout: async () => { await api.logout(); await setUser(null); },
  }}>{children}</Context.Provider>;
}

export function useAuth() {
  const auth = useContext(Context);
  if (!auth) throw new Error('Brak AuthProvider.');
  return auth;
}
