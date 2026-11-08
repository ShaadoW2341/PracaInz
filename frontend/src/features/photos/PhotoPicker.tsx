import { useEffect, useId, useRef, useState } from 'react';

export function PhotoPicker({ files, onChange, remaining = 5, disabled = false }: {
  files: File[]; onChange: (files: File[]) => void; remaining?: number; disabled?: boolean;
}) {
  const id = useId();
  const input = useRef<HTMLInputElement>(null);
  const [error, setError] = useState('');
  const [previews, setPreviews] = useState<{ url: string; name: string }[]>([]);
  useEffect(() => {
    const images = files.map(file => ({ url: URL.createObjectURL(file), name: file.name }));
    setPreviews(images);
    return () => images.forEach(image => URL.revokeObjectURL(image.url));
  }, [files]);

  function choose(selected: File[]) {
    let message = '';
    if (selected.length > remaining) message = `Możesz wybrać jeszcze ${remaining} zdjęć (maksymalnie 5 na znalezisko).`;
    else if (selected.some(file => !['image/jpeg', 'image/png'].includes(file.type))) message = 'Wybierz wyłącznie zdjęcia JPG lub PNG.';
    else if (selected.some(file => file.size === 0 || file.size > 5 * 1024 * 1024)) message = 'Każde zdjęcie musi mieć od 1 bajta do 5 MB.';
    setError(message);
    onChange(message ? [] : selected);
    if (input.current) input.current.value = '';
  }

  return <div className="photo-picker">
    <label htmlFor={id}>Zdjęcia (opcjonalnie)</label>
    <p className="photo-help" id={`${id}-help`}>JPG lub PNG, do 5 MB każde. Maksymalnie 5 zdjęć na znalezisko. Dostępne miejsca: {remaining}.</p>
    <input id={id} ref={input} type="file" accept="image/jpeg,image/png" multiple disabled={disabled || remaining === 0}
      aria-describedby={`${id}-help`} onChange={event => choose(Array.from(event.target.files ?? []))} />
    {error && <p className="form-alert" role="alert">{error}</p>}
    {previews.length > 0 && <ul className="photo-previews">{previews.map((image, index) => <li key={image.url}>
      <img src={image.url} alt={`Wybrane zdjęcie: ${image.name}`} />
      <span title={image.name}>{image.name}</span>
      <button type="button" className="text-button danger" disabled={disabled} aria-label={`Usuń z wyboru ${image.name}`}
        onClick={() => { setError(''); onChange(files.filter((_, n) => n !== index)); }}>Usuń z wyboru</button>
    </li>)}</ul>}
  </div>;
}
