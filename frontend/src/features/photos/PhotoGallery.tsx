import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { PhotoPicker } from './PhotoPicker';
import * as api from './api';

function PhotoPreview({ photo, onClose }: { photo: api.Photo; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [error, setError] = useState(false);
  useEffect(() => { dialog.current?.showModal(); }, []);
  return <dialog className="photo-dialog" ref={dialog} onCancel={onClose} aria-label="Podgląd zdjęcia znaleziska">
    <button className="secondary-button" autoFocus onClick={onClose}>Zamknij podgląd</button>
    {error ? <p role="alert">Nie udało się pobrać zdjęcia. Zamknij podgląd i spróbuj ponownie.</p>
      : <img src={api.photoUrl(photo.id)} alt="Zdjęcie znaleziska w pełnym rozmiarze" onError={() => setError(true)} />}
  </dialog>;
}

export function PhotoGallery({ observationId }: { observationId: string }) {
  const client = useQueryClient();
  const list = useQuery({ queryKey: ['photos', observationId], queryFn: () => api.photos(observationId) });
  const [files, setFiles] = useState<File[]>([]);
  const [preview, setPreview] = useState<api.Photo>();
  const [removal, setRemoval] = useState<string>();
  const [imageError, setImageError] = useState(false);
  const upload = useMutation({ mutationFn: () => api.uploadPhotos(observationId, files), onSuccess: () => {
    setFiles([]); void client.invalidateQueries({ queryKey: ['photos', observationId] });
  } });
  const deletion = useMutation({ mutationFn: api.deletePhoto, onSuccess: () => {
    setRemoval(undefined); setPreview(undefined); void client.invalidateQueries({ queryKey: ['photos', observationId] });
  } });
  const pending = upload.isPending || deletion.isPending;
  const error = list.error || upload.error || deletion.error;
  const remaining = 5 - (list.data?.length ?? 0);

  return <section className="photo-gallery" aria-label="Zdjęcia znaleziska">
    <h4>Zdjęcia {list.data ? `(${list.data.length}/5)` : ''}</h4>
    {list.isPending && <p role="status">Wczytywanie zdjęć…</p>}
    {error && <p className="form-alert" role="alert">{error.message}</p>}
    {list.isError && <button className="text-button" onClick={() => void list.refetch()}>Ponów pobieranie zdjęć</button>}
    {imageError && <p className="form-alert" role="alert">Nie udało się wczytać miniatury. Sprawdź połączenie lub zaloguj się ponownie.</p>}
    {list.data?.length === 0 && <p className="photo-help">To znalezisko nie ma jeszcze zdjęć.</p>}
    <ul className="photo-grid">{list.data?.map((photo, index) => <li key={photo.id}>
      <button type="button" className="photo-thumbnail" onClick={() => setPreview(photo)} aria-label={`Powiększ zdjęcie ${index + 1}`}>
        <img src={api.photoUrl(photo.id, true)} alt={`Zdjęcie znaleziska ${index + 1}`} loading="lazy" onError={() => setImageError(true)} />
      </button>
      <button type="button" className="text-button danger" disabled={pending} aria-label={`Usuń zdjęcie ${index + 1}`}
        onClick={() => { setRemoval(photo.id); deletion.reset(); }}>Usuń</button>
    </li>)}</ul>
    {removal && <div className="photo-confirmation"><p>Usunąć to zdjęcie? Tej operacji nie można cofnąć.</p>
      <div className="action-row"><button type="button" className="danger-button" disabled={pending} onClick={() => deletion.mutate(removal)}>Usuń zdjęcie</button>
        <button type="button" className="text-button" disabled={pending} onClick={() => setRemoval(undefined)}>Anuluj</button></div>
    </div>}
    {list.isSuccess && remaining > 0 && <><PhotoPicker files={files} remaining={remaining} disabled={pending}
      onChange={value => { setFiles(value); upload.reset(); }} />
      {files.length > 0 && <button type="button" className="secondary-button" disabled={pending} onClick={() => upload.mutate()}>
        {upload.isPending ? 'Wysyłanie zdjęć…' : 'Dodaj wybrane zdjęcia'}
      </button>}</>}
    {preview && <PhotoPreview photo={preview} onClose={() => setPreview(undefined)} />}
  </section>;
}
