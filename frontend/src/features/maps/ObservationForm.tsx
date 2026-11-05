import { useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../api/http';
import * as api from './api';

export type Coordinates = { latitude: number; longitude: number };
export function ObservationForm({ mapId, species, initial, coordinates, onClose, onSaved }: {
  mapId: string; species: api.Species[]; initial?: api.Observation; coordinates?: Coordinates;
  onClose: () => void; onSaved: (observation: api.Observation) => void;
}) {
  const client = useQueryClient();
  const now = new Date();
  const today = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`;
  const [draft,setDraft] = useState({ speciesId: String(initial?.speciesId ?? 1), quantity: String(initial?.quantity ?? 1), observedOn: initial?.observedOn ?? today, description: initial?.description ?? '', latitude: String(initial?.latitude ?? coordinates?.latitude ?? ''), longitude: String(initial?.longitude ?? coordinates?.longitude ?? '') });
  const [gpsError,setGpsError] = useState('');
  const [gpsPending,setGpsPending] = useState(false);
  const [validation,setValidation] = useState('');
  const save = useMutation({ mutationFn: (body: api.ObservationInput) => api.saveObservation(mapId,body,initial?.id), onSuccess: result => { void client.invalidateQueries({queryKey:['maps']}); onSaved(result); } });
  function locate() {
    setGpsError('');
    if (!navigator.geolocation) { setGpsError('Przeglądarka nie obsługuje geolokalizacji. Wpisz współrzędne lub wybierz punkt na mapie.'); return; }
    setGpsPending(true);
    navigator.geolocation.getCurrentPosition(position => {
      setDraft(value => ({...value,latitude:position.coords.latitude.toFixed(6),longitude:position.coords.longitude.toFixed(6)})); setGpsPending(false);
    }, error => {
      setGpsError(error.code === 1 ? 'Odmówiono dostępu do lokalizacji. Wpisz współrzędne lub wybierz punkt na mapie.' : 'Nie udało się odczytać GPS. Wpisz współrzędne lub spróbuj ponownie.'); setGpsPending(false);
    }, {enableHighAccuracy:true,timeout:10000,maximumAge:30000});
  }
  function submit(event: FormEvent) {
    event.preventDefault(); setValidation('');
    const latitude = Number(draft.latitude), longitude = Number(draft.longitude), quantity = Number(draft.quantity);
    if (!draft.latitude.trim() || !draft.longitude.trim() || !Number.isFinite(latitude) || !Number.isFinite(longitude) || Math.abs(latitude)>90 || Math.abs(longitude)>180) { setValidation('Wybierz lokalizację: szerokość od −90 do 90 i długość od −180 do 180.'); return; }
    if (!Number.isInteger(quantity) || quantity<1 || quantity>100000 || draft.observedOn>today) { setValidation('Sprawdź datę i dodatnią, całkowitą liczbę sztuk.'); return; }
    save.mutate({...draft,speciesId:Number(draft.speciesId),quantity,latitude,longitude});
  }
  return <section className="card editor-card"><div className="card-heading"><h2>{initial ? 'Edytuj znalezisko' : 'Nowe znalezisko'}</h2><button className="text-button" onClick={onClose}>Anuluj</button></div>
    <form className="map-form" onSubmit={submit}>
      <label>Gatunek<select value={draft.speciesId} onChange={e=>setDraft({...draft,speciesId:e.target.value})} disabled={save.isPending}>{species.map(s=><option key={s.id} value={s.id}>{s.polishName}</option>)}</select></label>
      <div className="form-pair"><label>Data znalezienia<input type="date" required max={today} value={draft.observedOn} onChange={e=>setDraft({...draft,observedOn:e.target.value})} /></label><label>Liczba sztuk<input type="number" required min="1" max="100000" step="1" value={draft.quantity} onChange={e=>setDraft({...draft,quantity:e.target.value})} /></label></div>
      <button className="secondary-button" type="button" onClick={locate} disabled={gpsPending || save.isPending}>{gpsPending ? 'Ustalanie lokalizacji…' : 'Użyj mojej lokalizacji GPS'}</button>
      {gpsError && <p className="form-alert" role="alert">{gpsError}</p>}
      <div className="form-pair"><label>Szerokość GPS<input type="number" required min="-90" max="90" step="any" value={draft.latitude} onChange={e=>setDraft({...draft,latitude:e.target.value})} /></label><label>Długość GPS<input type="number" required min="-180" max="180" step="any" value={draft.longitude} onChange={e=>setDraft({...draft,longitude:e.target.value})} /></label></div>
      <label>Notatka<textarea maxLength={2000} rows={3} value={draft.description} onChange={e=>setDraft({...draft,description:e.target.value})} /></label>
      {(validation || save.error) && <p className="form-alert" role="alert">{validation || (save.error instanceof ApiError ? [save.error.message,...Object.values(save.error.fieldErrors)].join(' ') : save.error?.message)}</p>}
      <button className="auth-submit" disabled={save.isPending || species.length===0}>{save.isPending ? 'Zapisywanie…' : 'Zapisz znalezisko'}</button>
    </form></section>;
}
