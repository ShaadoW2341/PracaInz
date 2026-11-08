import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Icon } from '../../components/Icon';
import { useAuth } from '../auth/AuthProvider';
import { UserEditor } from './UserEditor';
import { SpeciesEditor } from './SpeciesEditor';
import * as api from './api';

export function AdminPage() {
  const auth = useAuth();
  const [tab, setTab] = useState<'users' | 'species'>('users');
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [editingUser, setEditingUser] = useState<api.AdminUser>();
  const [speciesEditor, setSpeciesEditor] = useState<{ initial?: api.AdminSpecies }>();
  const logout = useMutation({ mutationFn: auth.logout });
  useEffect(() => {
    const timer = window.setTimeout(() => { setQuery(search.trim()); setPage(0); }, 250);
    return () => window.clearTimeout(timer);
  }, [search]);
  const users = useQuery({ queryKey: ['admin', 'users', query, page], queryFn: () => api.users(query, page), enabled: tab === 'users' });
  const species = useQuery({ queryKey: ['admin', 'species', query, page], queryFn: () => api.species(query, page), enabled: tab === 'species' });
  const current = tab === 'users' ? users : species;
  const error = current.error || logout.error;
  function switchTab(value: 'users' | 'species') { setTab(value); setSearch(''); setQuery(''); setPage(0); }

  return <main className="admin-page">
    <header className="admin-header"><Link to="/app" className="brand admin-brand"><Icon name="mushroom" size={30} />MycoMap</Link>
      <div className="admin-header-actions"><Link className="secondary-button" to="/app">Wróć do map</Link><span>{auth.user?.username}</span>
        <button className="logout-button" disabled={logout.isPending} onClick={() => logout.mutate()}>Wyloguj się</button></div>
    </header>
    <section className="page-heading"><div><p className="eyebrow">ZARZĄDZANIE APLIKACJĄ</p><h1>Administracja<span>.</span></h1><p>Konta użytkowników i słownik gatunków w jednym miejscu.</p></div><span className="admin-role-badge">Administrator</span></section>
    <p className="admin-notice"><Icon name="lock" size={16} />Rola administratora nie daje dostępu do prywatnych map ani zdjęć innych użytkowników.</p>
    <div className="admin-tabs" role="group" aria-label="Sekcje administracji"><button className={tab === 'users' ? 'active' : ''} aria-pressed={tab === 'users'} onClick={() => switchTab('users')}>Użytkownicy</button><button className={tab === 'species' ? 'active' : ''} aria-pressed={tab === 'species'} onClick={() => switchTab('species')}>Słownik gatunków</button></div>
    <section className="card admin-content"><div className="admin-toolbar"><label>{tab === 'users' ? 'Szukaj konta' : 'Szukaj gatunku'}<input type="search" maxLength={100} value={search} onChange={event => setSearch(event.target.value)} placeholder={tab === 'users' ? 'Nazwa użytkownika lub e-mail' : 'Nazwa polska lub łacińska'} /></label>
      {tab === 'species' && <button className="auth-submit compact-button" onClick={() => setSpeciesEditor({})}>Dodaj gatunek</button>}</div>
      {error && <p className="form-alert" role="alert">{error.message}</p>}
      {current.isError && <button className="text-button" onClick={() => void current.refetch()}>Spróbuj ponownie</button>}
      {current.isFetching && <p role="status">Wczytywanie…</p>}
      {current.data?.total === 0 && <p className="admin-empty">Brak wyników. Zmień wyszukiwaną frazę.</p>}
      {tab === 'users' && users.data && users.data.items.length > 0 && <div className="admin-table-scroll"><table className="admin-table"><caption>Konta użytkowników</caption><thead><tr><th>Użytkownik</th><th>Rola</th><th>Status konta</th><th>E-mail</th><th>Akcje</th></tr></thead><tbody>
        {users.data.items.map(user => <tr key={user.id}><td><strong>{user.username}</strong>{user.id === auth.user?.id && <small>Twoje konto</small>}</td><td><span className={`admin-badge ${user.role === 'ADMIN' ? 'admin-role-badge' : ''}`}>{user.role === 'ADMIN' ? 'Administrator' : 'Użytkownik'}</span></td><td><span className={`admin-badge ${user.enabled ? '' : 'admin-blocked'}`}>{user.enabled ? 'Odblokowane' : 'Zablokowane'}</span><small>{user.emailVerified ? 'E-mail potwierdzony' : 'E-mail niepotwierdzony'}</small></td><td>{user.email}</td><td><button className="secondary-button" onClick={() => setEditingUser(user)}>Uprawnienia</button></td></tr>)}
      </tbody></table></div>}
      {tab === 'species' && species.data && species.data.items.length > 0 && <div className="admin-table-scroll"><table className="admin-table"><caption>Słownik gatunków</caption><thead><tr><th>Nazwa polska</th><th>Nazwa łacińska</th><th>Status</th><th>Akcje</th></tr></thead><tbody>
        {species.data.items.map(item => <tr key={item.id}><td><strong>{item.polishName}</strong>{item.id === 1 && <small>Wpis systemowy</small>}</td><td><em>{item.latinName || '—'}</em></td><td><span className={`admin-badge ${item.isActive ? '' : 'admin-blocked'}`}>{item.isActive ? 'Aktywny' : 'Nieaktywny'}</span></td><td><button className="secondary-button" disabled={item.id === 1} onClick={() => setSpeciesEditor({ initial: item })}>Edytuj</button></td></tr>)}
      </tbody></table></div>}
      {current.data && <div className="pagination admin-pagination"><span>Wyników: {current.data.total}</span><button disabled={page === 0} onClick={() => setPage(value => value - 1)}>Poprzednia</button><span>Strona {page + 1} z {Math.max(1, Math.ceil(current.data.total / 20))}</span><button disabled={(page + 1) * 20 >= current.data.total} onClick={() => setPage(value => value + 1)}>Następna</button></div>}
    </section>
    {editingUser && <UserEditor user={editingUser} onClose={() => setEditingUser(undefined)} />}
    {speciesEditor && <SpeciesEditor initial={speciesEditor.initial} onClose={() => setSpeciesEditor(undefined)} />}
  </main>;
}
