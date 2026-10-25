import { useEffect, useRef, useState } from 'react';
import type { Map as MapLibreMap } from 'maplibre-gl';
import { Icon } from './Icon';

export function MapPreview() {
  const container = useRef<HTMLDivElement>(null);
  const [error, setError] = useState(false);
  const styleUrl = import.meta.env.VITE_MAP_STYLE_URL?.trim();

  useEffect(() => {
    if (!container.current || !styleUrl) return;
    let map: MapLibreMap | undefined;
    let disposed = false;
    void Promise.all([import('maplibre-gl'), import('maplibre-gl/dist/maplibre-gl.css')])
      .then(([maplibregl]) => {
        if (disposed || !container.current) return;
        map = new maplibregl.Map({
          container: container.current, style: styleUrl,
          center: [19.15, 52.05], zoom: 5.4, attributionControl: false,
        });
        map.addControl(new maplibregl.NavigationControl(), 'top-right');
        map.addControl(new maplibregl.AttributionControl({ compact: false }));
        map.on('error', () => setError(true));
        map.on('idle', () => setError(false));
      }).catch(() => { if (!disposed) setError(true); });
    return () => { disposed = true; map?.remove(); };
  }, [styleUrl]);

  return <section className="map-card" aria-label="Podgląd podkładu mapowego">
    <div className="map-canvas" ref={container} />
    {!styleUrl && <div className="map-empty">
      <span className="map-symbol"><Icon name="pin" size={29} /></span>
      <h3>Tu zacznie się Twoja mapa</h3>
      <p>Podkład nie jest skonfigurowany. Uzupełnij <code>VITE_MAP_STYLE_URL</code> w pliku .env i uruchom ponownie frontend.</p>
    </div>}
    {error && <p className="map-error" role="alert">Nie udało się wczytać podkładu. Sprawdź URL stylu, klucz dostawcy i obsługę WebGL.</p>}
    <span className="map-label"><Icon name="layers" size={12} /> PODGLĄD MAPY · BEZ OBSERWACJI</span>
  </section>;
}
