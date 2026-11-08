import { useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { EditorDialog } from '../../components/EditorDialog';
import * as api from './api';

export function SpeciesEditor({ initial, onClose }: { initial?: api.AdminSpecies; onClose: () => void }) {
  const client = useQueryClient();
  const [polishName, setPolishName] = useState(initial?.polishName ?? '');
  const [latinName, setLatinName] = useState(initial?.latinName ?? '');
  const [isActive, setIsActive] = useState(initial?.isActive ?? true);
  const save = useMutation({ mutationFn: () => api.saveSpecies({ polishName: polishName.trim(), latinName: latinName.trim() || null, isActive }, initial?.id), onSuccess: () => {
    void client.invalidateQueries({ queryKey: ['admin', 'species'] });
    void client.invalidateQueries({ queryKey: ['species'] });
    void client.invalidateQueries({ queryKey: ['maps'] });
    onClose();
  } });
  function submit(event: FormEvent) { event.preventDefault(); if (polishName.trim()) save.mutate(); }
  return <EditorDialog label={initial ? 'Edytuj gatunek' : 'Nowy gatunek'} busy={save.isPending} onClose={onClose}>
    <section className="card editor-card"><h2>{initial ? 'Edytuj gatunek' : 'Nowy gatunek'}</h2>
      <form className="map-form" onSubmit={submit}>
        <label>Nazwa polska<input required maxLength={100} value={polishName} onChange={event => setPolishName(event.target.value)} disabled={save.isPending} /></label>
        <label>Nazwa łacińska (opcjonalnie)<input maxLength={120} value={latinName} onChange={event => setLatinName(event.target.value)} disabled={save.isPending} /></label>
        <label className="admin-checkbox"><input type="checkbox" checked={isActive} onChange={event => setIsActive(event.target.checked)} disabled={save.isPending} />Aktywny w słowniku</label>
        <p className="admin-notice">Nieaktywny gatunek pozostaje przy starszych znaleziskach, ale nie można wybrać go w nowych wpisach.</p>
        {save.error && <p className="form-alert" role="alert">{save.error.message}</p>}
        <div className="editor-actions"><button type="button" className="secondary-button" disabled={save.isPending} onClick={onClose}>Anuluj</button>
          <button className="auth-submit" disabled={save.isPending || !polishName.trim()}>{save.isPending ? 'Zapisywanie…' : 'Zapisz gatunek'}</button></div>
      </form>
    </section>
  </EditorDialog>;
}
