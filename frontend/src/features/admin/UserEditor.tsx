import { useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { EditorDialog } from '../../components/EditorDialog';
import { useAuth } from '../auth/AuthProvider';
import * as api from './api';

export function UserEditor({ user, onClose }: { user: api.AdminUser; onClose: () => void }) {
  const auth = useAuth();
  const client = useQueryClient();
  const [role, setRole] = useState(user.role);
  const [enabled, setEnabled] = useState(user.enabled);
  const save = useMutation({ mutationFn: () => api.updateUser(user.id, { role, enabled }), onSuccess: () => {
    void client.invalidateQueries({ queryKey: ['admin', 'users'] });
    if (user.id === auth.user?.id) auth.refresh();
    onClose();
  } });
  function submit(event: FormEvent) { event.preventDefault(); save.mutate(); }
  return <EditorDialog label={`Uprawnienia: ${user.username}`} busy={save.isPending} onClose={onClose}>
    <section className="card editor-card"><h2>Uprawnienia konta</h2><p className="editor-intro">{user.username} · {user.email}</p>
      <form className="map-form" onSubmit={submit}>
        <label>Rola<select value={role} onChange={event => setRole(event.target.value as api.Role)} disabled={save.isPending}>
          <option value="USER">Użytkownik</option><option value="ADMIN">Administrator</option>
        </select></label>
        <label className="admin-checkbox"><input type="checkbox" checked={enabled} onChange={event => setEnabled(event.target.checked)} disabled={save.isPending} />Konto odblokowane</label>
        <p className="admin-notice">Zmiana roli lub blokada wyloguje to konto ze wszystkich sesji. Zablokowanie konta zachowuje jego mapy i zdjęcia.</p>
        {user.id === auth.user?.id && <p className="admin-notice">Edytujesz własne konto. Po zmianie uprawnień konieczne będzie ponowne logowanie.</p>}
        {save.error && <p className="form-alert" role="alert">{save.error.message}</p>}
        <div className="editor-actions"><button type="button" className="secondary-button" onClick={onClose} disabled={save.isPending}>Anuluj</button>
          <button className="auth-submit" disabled={save.isPending || (role === user.role && enabled === user.enabled)}>{save.isPending ? 'Zapisywanie…' : 'Zapisz uprawnienia'}</button></div>
      </form>
    </section>
  </EditorDialog>;
}
