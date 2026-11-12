import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { EditorDialog } from '../../components/EditorDialog';
import type { MushroomMap } from '../maps/api';
import * as api from './api';

export function SharingDialog({ map, onClose }: { map: MushroomMap; onClose: () => void }) {
  const client = useQueryClient();
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [recipient, setRecipient] = useState<api.UserMatch>();
  const [precision, setPrecision] = useState<api.Precision>('COARSE');
  const [confirmed, setConfirmed] = useState(false);
  const [page, setPage] = useState(0);
  const [notice, setNotice] = useState('');
  const [action, setAction] = useState<{ share: api.Share; type: 'exact' | 'revoke' }>();
  const [actionConfirmed, setActionConfirmed] = useState(false);
  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(search.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [search]);
  const matches = useQuery({ queryKey: ['sharing-users', query], queryFn: () => api.searchUsers(query), enabled: query.length >= 3 && !recipient, retry: false });
  const list = useQuery({ queryKey: ['shares', map.id, page], queryFn: () => api.shares(map.id, page), retry: false });
  function refresh() { void client.invalidateQueries({ queryKey: ['shares', map.id] }); }
  const create = useMutation({ mutationFn: () => api.createShare(map.id, recipient!.username, precision, confirmed), onSuccess: () => {
    setRecipient(undefined); setSearch(''); setPrecision('COARSE'); setConfirmed(false); setPage(0); setNotice('Mapa została udostępniona.'); refresh();
  } });
  const change = useMutation({ mutationFn: async (value: { share: api.Share; type: 'coarse' | 'exact' | 'revoke' }) => {
    if (value.type === 'revoke') await api.revokeShare(map.id, value.share.id);
    else await api.changePrecision(map.id, value.share.id, value.type === 'exact' ? 'EXACT' : 'COARSE', value.type === 'exact' && actionConfirmed);
  }, onSuccess: (_, value) => {
    setAction(undefined); setActionConfirmed(false); setPage(0); setNotice(value.type === 'revoke' ? 'Dostęp został cofnięty.' : 'Dokładność została zmieniona.'); refresh();
  } });
  const busy = create.isPending || change.isPending;
  const error = list.error || create.error || change.error || matches.error;
  function submit(event: FormEvent) { event.preventDefault(); if (recipient && (precision !== 'EXACT' || confirmed)) create.mutate(); }

  return <EditorDialog label="Udostępnij mapę" busy={busy} onClose={onClose}>
    <section className="card editor-card sharing-editor"><h2>Udostępnij mapę</h2><p className="editor-intro">{map.name}</p>
      <p className="sharing-note">Odbiorca otrzyma dostęp tylko do odczytu. Zdjęcia pozostają prywatne. W trybie przybliżonym notatki i opis mapy również pozostają prywatne.</p>
      <form className="map-form" onSubmit={submit}>
        <label>Nazwa użytkownika<input value={search} maxLength={32} autoComplete="off" disabled={busy} placeholder="Wpisz co najmniej 3 znaki"
          onChange={event => { setSearch(event.target.value); setRecipient(undefined); create.reset(); setNotice(''); }} /></label>
        {!recipient && query.length >= 3 && <div className="share-search-results" aria-label="Znalezieni użytkownicy">
          {matches.isFetching && <p role="status">Wyszukiwanie użytkowników…</p>}
          {matches.data?.length === 0 && <p className="photo-help">Nie znaleziono użytkownika. Konto odbiorcy musi być aktywne i mieć potwierdzony e-mail.</p>}
          {matches.data?.map(user => <button key={user.id} type="button" disabled={busy} onClick={() => { setRecipient(user); setSearch(user.username); }}>{user.username}</button>)}
        </div>}
        {recipient && <p className="share-recipient">Wybrany odbiorca: <strong>{recipient.username}</strong></p>}
        <label>Dokładność lokalizacji<select value={precision} disabled={busy} onChange={event => { setPrecision(event.target.value as api.Precision); setConfirmed(false); }}>
          <option value="COARSE">Przybliżone — siatka około 1 km</option><option value="EXACT">Dokładne współrzędne</option>
        </select></label>
        {precision === 'EXACT' && <div className="sharing-warning"><p>Odbiorca pozna dokładne miejsca i notatki. Cofnięcie dostępu nie usunie danych, które już zapisał.</p>
          <label className="admin-checkbox"><input type="checkbox" checked={confirmed} disabled={busy} onChange={event => setConfirmed(event.target.checked)} />Świadomie udostępniam dokładne współrzędne i notatki.</label></div>}
        <button className="auth-submit" disabled={busy || !recipient || (precision === 'EXACT' && !confirmed)}>{create.isPending ? 'Udostępnianie…' : 'Udostępnij wybranemu użytkownikowi'}</button>
      </form>
      {error && <p className="form-alert" role="alert">{error.message}</p>}
      {notice && <p className="sharing-success" role="status">{notice}</p>}
      <div className="shares-heading"><h3>Osoby z dostępem {list.data ? `(${list.data.total})` : ''}</h3></div>
      {list.isPending && <p role="status">Wczytywanie odbiorców…</p>}
      {list.isError && <button className="text-button" onClick={() => void list.refetch()}>Ponów pobieranie odbiorców</button>}
      {list.data?.total === 0 && <p className="photo-help">Ta mapa nie została jeszcze nikomu udostępniona.</p>}
      <ul className="share-list">{list.data?.items.map(share => <li key={share.id}>
        <div><strong>{share.username}</strong><small>{share.precision === 'COARSE' ? 'Przybliżone · około 1 km' : 'Dokładne współrzędne'} · tylko odczyt</small></div>
        <div className="share-row-actions">
          <button className="text-button" disabled={busy} onClick={() => {
            change.reset(); setNotice('');
            if (share.precision === 'EXACT') change.mutate({share,type:'coarse'});
            else { setAction({share,type:'exact'}); setActionConfirmed(false); }
          }}>{share.precision === 'EXACT' ? 'Przybliż lokalizacje' : 'Udostępnij dokładne'}</button>
          <button className="text-button danger" disabled={busy} onClick={() => { change.reset(); setAction({share,type:'revoke'}); setActionConfirmed(false); setNotice(''); }}>Cofnij dostęp</button>
        </div>
      </li>)}</ul>
      {action && <div className={action.type === 'exact' ? 'sharing-warning' : 'photo-confirmation'}>
        <p>{action.type === 'exact' ? `Udostępnić dokładne miejsca i notatki użytkownikowi ${action.share.username}? Może on zapisać te dane.` : `Cofnąć dostęp użytkownikowi ${action.share.username}?`}</p>
        {action.type === 'exact' && <label className="admin-checkbox"><input type="checkbox" checked={actionConfirmed} disabled={busy} onChange={event => setActionConfirmed(event.target.checked)} />Potwierdzam udostępnienie dokładnych współrzędnych.</label>}
        <div className="action-row"><button className={action.type === 'revoke' ? 'danger-button' : 'secondary-button'} disabled={busy || (action.type === 'exact' && !actionConfirmed)}
          onClick={() => change.mutate(action)}>{action.type === 'exact' ? 'Potwierdź dokładny dostęp' : 'Potwierdź cofnięcie dostępu'}</button>
          <button className="text-button" disabled={busy} onClick={() => setAction(undefined)}>Anuluj</button></div>
      </div>}
      {list.data && list.data.total > 20 && <div className="pagination"><button disabled={busy || page === 0} onClick={() => setPage(value => value - 1)}>Poprzedni odbiorcy</button>
        <span>Strona {page + 1}</span><button disabled={busy || (page + 1) * 20 >= list.data.total} onClick={() => setPage(value => value + 1)}>Kolejni odbiorcy</button></div>}
      <div className="editor-actions"><button className="secondary-button" disabled={busy} onClick={onClose}>Zamknij</button></div>
    </section>
  </EditorDialog>;
}
