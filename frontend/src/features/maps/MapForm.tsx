import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import * as api from './api';

export function MapForm({ initial, onClose, onSaved, onPendingChange }: { initial?: api.MushroomMap; onClose:()=>void; onSaved:(map:api.MushroomMap)=>void; onPendingChange?:(pending:boolean)=>void }) {
  const client=useQueryClient();
  const [name,setName]=useState(initial?.name ?? '');
  const [description,setDescription]=useState(initial?.description ?? '');
  const save=useMutation({mutationFn:()=>api.saveMap({name:name.trim(),description:description.trim()},initial?.id),onSuccess:result=>{void client.invalidateQueries({queryKey:['maps']});onSaved(result);}});
  useEffect(() => { onPendingChange?.(save.isPending); }, [save.isPending,onPendingChange]);
  function submit(event:FormEvent){event.preventDefault();if(name.trim())save.mutate();}
  return <section className="card editor-card"><div className="card-heading"><h2>{initial ? 'Ustawienia mapy' : 'Nowa mapa'}</h2></div><p className="editor-intro">Nadaj mapie nazwę i zbieraj w niej swoje leśne odkrycia.</p><form className="map-form" onSubmit={submit}>
    <label>Nazwa mapy<input required maxLength={100} value={name} onChange={e=>setName(e.target.value)} /></label>
    <label>Opis<textarea maxLength={1000} rows={3} value={description} onChange={e=>setDescription(e.target.value)} /></label>
    {save.error && <p className="form-alert" role="alert">{save.error.message}</p>}
    <div className="editor-actions"><button type="button" className="secondary-button" disabled={save.isPending} onClick={onClose}>Anuluj</button><button className="auth-submit" disabled={save.isPending || !name.trim()}>{save.isPending ? 'Zapisywanie…' : 'Zapisz mapę'}</button></div>
  </form></section>;
}
