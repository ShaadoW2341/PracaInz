import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../api/http';
import { PhotoPicker } from '../photos/PhotoPicker';
import { uploadPhotos } from '../photos/api';
import * as api from './api';

export type Coordinates = { latitude: number; longitude: number };
export function ObservationForm({ mapId, species, initial, coordinates, onClose, onSaved, onPendingChange }: {
  mapId: string; species: api.Species[]; initial?: api.Observation; coordinates?: Coordinates;
  onClose: () => void; onSaved: (observation: api.Observation) => void;
  onPendingChange?: (pending: boolean) => void;
}) {
  const client = useQueryClient();
  const now = new Date();
  const today = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`;
  const [draft,setDraft] = useState({ speciesId: String(initial?.speciesId ?? 1), quantity: String(initial?.quantity ?? 1), observedOn: initial?.observedOn ?? today, description: initial?.description ?? '', latitude: String(initial?.latitude ?? coordinates?.latitude ?? ''), longitude: String(initial?.longitude ?? coordinates?.longitude ?? '') });
  const [gpsError,setGpsError] = useState('');
  const [gpsPending,setGpsPending] = useState(false);
  const [validation,setValidation] = useState('');
  const [files,setFiles] = useState<File[]>([]);
  const saved = useRef<api.Observation | undefined>(initial);
  const [photoError,setPhotoError] = useState(false);
  const save = useMutation({ mutationFn: async (body: api.ObservationInput) => {
    const result = await api.saveObservation(mapId,body,saved.current?.id);
    saved.current = result;
    void client.invalidateQueries({queryKey:['maps']});
    if (files.length > 0) {
      try { await uploadPhotos(result.id,files); }
      catch (error) { setPhotoError(true); throw error; }
      void client.invalidateQueries({queryKey:['photos',result.id]});
    }
    setPhotoError(false);
    return result;
  }, onSuccess: onSaved });
  useEffect(() => { onPendingChange?.(save.isPending); }, [save.isPending,onPendingChange]);
  function locate() {
    setGpsError('');
    if (!navigator.geolocation) { setGpsError('Przeglądarka nie obsługuje geolokalizacji. Zamknij formularz i wybierz miejsce na mapie.'); return; }
    setGpsPending(true);
    navigator.geolocation.getCurrentPosition(position => {
      setDraft(value => ({...value,latitude:position.coords.latitude.toFixed(6),longitude:position.coords.longitude.toFixed(6)})); setGpsPending(false);
    }, error => {
      setGpsError(error.code === 1 ? 'Odmówiono dostępu do lokalizacji. Zamknij formularz i wybierz miejsce na mapie.' : 'Nie udało się odczytać GPS. Spróbuj ponownie lub wybierz miejsce na mapie.'); setGpsPending(false);
    }, {enableHighAccuracy:true,timeout:10000,maximumAge:30000});
  }
  function submit(event: FormEvent) {
    event.preventDefault(); setValidation('');
    const latitude = Number(draft.latitude), longitude = Number(draft.longitude), quantity = Number(draft.quantity);
    if (!draft.latitude.trim() || !draft.longitude.trim() || !Number.isFinite(latitude) || !Number.isFinite(longitude) || Math.abs(latitude)>90 || Math.abs(longitude)>180) { setValidation('Wybierz lokalizację na mapie lub użyj przycisku GPS.'); return; }
    if (!Number.isInteger(quantity) || quantity<1 || quantity>100000 || draft.observedOn>today) { setValidation('Sprawdź datę i dodatnią, całkowitą liczbę sztuk.'); return; }
    save.mutate({...draft,speciesId:Number(draft.speciesId),quantity,latitude,longitude});
  }
  return <section className="card editor-card"><div className="card-heading"><h2>{initial ? 'Edytuj znalezisko' : 'Nowe znalezisko'}</h2></div>
    <p className="editor-intro">{initial ? 'Zmień szczegóły swojego znaleziska.' : coordinates ? 'Miejsce z mapy jest już wybrane. Uzupełnij szczegóły swojego znaleziska.' : 'Zapisz szczegóły znaleziska i pobierz lokalizację z GPS.'}</p>
    <form className="map-form" onSubmit={submit}>
      <label>Gatunek<select value={draft.speciesId} onChange={e=>setDraft({...draft,speciesId:e.target.value})} disabled={save.isPending}>
        {initial && !species.some(item=>item.id===initial.speciesId) && <option value={initial.speciesId}>{initial.speciesName} (nieaktywny)</option>}
        {species.map(s=><option key={s.id} value={s.id}>{s.polishName}</option>)}
      </select></label>
      <div className="form-pair"><label>Data znalezienia<input type="date" required max={today} value={draft.observedOn} onChange={e=>setDraft({...draft,observedOn:e.target.value})} /></label><label>Liczba sztuk<input type="number" required min="1" max="100000" step="1" value={draft.quantity} onChange={e=>setDraft({...draft,quantity:e.target.value})} /></label></div>
      <button className="secondary-button" type="button" onClick={locate} disabled={gpsPending || save.isPending}>{gpsPending ? 'Ustalanie lokalizacji…' : 'Użyj mojej lokalizacji GPS'}</button>
      {gpsError && <p className="form-alert" role="alert">{gpsError}</p>}
      <div className="form-pair"><label>Szerokość GPS<input readOnly value={draft.latitude} placeholder="Wybierz lokalizację" /></label><label>Długość GPS<input readOnly value={draft.longitude} placeholder="Wybierz lokalizację" /></label></div>
      <p className="photo-help">Współrzędne są uzupełniane z wybranego punktu na mapie lub z GPS i służą tylko do podglądu.</p>
      <label>Notatka<textarea maxLength={2000} rows={3} value={draft.description} onChange={e=>setDraft({...draft,description:e.target.value})} /></label>
      {!initial && <PhotoPicker files={files} onChange={setFiles} disabled={save.isPending} />}
      {initial && <p className="photo-help">Zdjęcia możesz dodawać i usuwać w szczegółach znaleziska.</p>}
      {photoError && <p className="form-alert" role="status">Znalezisko jest zapisane, ale wysyłanie zdjęć nie zostało potwierdzone. Możesz ponowić zapis lub zamknąć formularz i sprawdzić zdjęcia w szczegółach.</p>}
      {(validation || save.error) && <p className="form-alert" role="alert">{validation || (save.error instanceof ApiError ? [save.error.message,...Object.values(save.error.fieldErrors)].join(' ') : save.error?.message)}</p>}
      <div className="editor-actions"><button type="button" className="secondary-button" disabled={save.isPending} onClick={onClose}>Anuluj</button><button className="auth-submit" disabled={save.isPending || species.length===0}>{save.isPending ? 'Zapisywanie znaleziska i zdjęć…' : 'Zapisz znalezisko'}</button></div>
    </form></section>;
}
