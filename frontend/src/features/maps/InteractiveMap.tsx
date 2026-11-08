import { useEffect, useRef, useState } from 'react';
import type { GeoJSONSource, Map as LibreMap, Popup } from 'maplibre-gl';
import type { Features } from './api';
import type { Coordinates } from './ObservationForm';

const empty = { type: 'FeatureCollection' as const, features: [] };
export function InteractiveMap({ data, selectedId, focus, mapId, onPick, onSelect, onBounds }: {
  data?: Features; selectedId?: string; focus?: Coordinates; mapId?: string;
  onPick: (point: Coordinates) => void; onSelect: (id: string) => void; onBounds: (bbox: string) => void;
}) {
  const container = useRef<HTMLDivElement>(null);
  const map = useRef<LibreMap | null>(null);
  const popup = useRef<Popup | null>(null);
  const callbacks = useRef({onPick,onSelect,onBounds}); callbacks.current={onPick,onSelect,onBounds};
  const currentData = useRef(data); currentData.current=data;
  const [ready,setReady] = useState(false);
  const [error,setError] = useState('');
  const styleUrl = import.meta.env.VITE_MAP_STYLE_URL?.trim();

  useEffect(() => {
    if (!styleUrl || !container.current) return;
    let disposed=false;
    void Promise.all([import('maplibre-gl'),import('maplibre-gl/dist/maplibre-gl.css'),import('maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url')]).then(([libre,,worker])=>{
      if (disposed || !container.current) return;
      libre.setWorkerUrl(worker.default);
      const view = new libre.Map({container:container.current,style:styleUrl,center:[19.1,52.05],zoom:5.5,attributionControl:false,renderWorldCopies:false});
      map.current=view;
      view.addControl(new libre.NavigationControl(),'top-right');
      view.addControl(new libre.AttributionControl({compact:false}));
      const bounds=()=>{
        const b=view.getBounds();
        const west=Math.max(-180,b.getWest()),east=Math.min(180,b.getEast()),south=Math.max(-90,b.getSouth()),north=Math.min(90,b.getNorth());
        callbacks.current.onBounds(east-west<=120 && north-south<=80 ? [west,south,east,north].map(v=>v.toFixed(6)).join(',') : '');
      };
      view.on('load',()=>{
        view.addSource('findings',{type:'geojson',data:currentData.current ?? empty,cluster:true,clusterMaxZoom:13,clusterRadius:45});
        view.addLayer({id:'finding-clusters',type:'circle',source:'findings',filter:['has','point_count'],paint:{'circle-color':'#345a3e','circle-radius':22,'circle-stroke-color':'#eef0dd','circle-stroke-width':3}});
        view.addLayer({id:'finding-count',type:'symbol',source:'findings',filter:['has','point_count'],layout:{'text-field':['get','point_count_abbreviated'],'text-size':13},paint:{'text-color':'#ffffff'}});
        view.addLayer({id:'finding-points',type:'circle',source:'findings',filter:['!', ['has','point_count']],paint:{'circle-color':'#64834c','circle-radius':9,'circle-stroke-color':'#fff','circle-stroke-width':3}});
        setReady(true); setError(''); bounds();
      });
      view.on('moveend',bounds);
      view.on('click',event=>{
        if (!view.getLayer('finding-points')) return;
        popup.current?.remove(); popup.current=null;
        const hit=view.queryRenderedFeatures(event.point,{layers:['finding-clusters','finding-points']})[0];
        if (hit?.properties?.cluster_id != null && hit.geometry.type==='Point') {
          const center=hit.geometry.coordinates as [number,number];
          void (view.getSource('findings') as GeoJSONSource).getClusterExpansionZoom(Number(hit.properties.cluster_id))
            .then(zoom=>{if(!disposed)view.easeTo({center,zoom});}).catch(()=>setError('Nie udało się rozwinąć klastra. Przybliż mapę ręcznie.'));
        } else if (hit?.properties?.id) callbacks.current.onSelect(String(hit.properties.id));
        else {
          const location=event.lngLat.wrap();
          const point={latitude:Number(location.lat.toFixed(6)),longitude:Number(location.lng.toFixed(6))};
          const content=document.createElement('div');
          const heading=document.createElement('strong'); heading.textContent='Wybrane miejsce';
          const coordinates=document.createElement('p'); coordinates.textContent=`${point.latitude.toFixed(6)}, ${point.longitude.toFixed(6)}`;
          const button=document.createElement('button'); button.type='button'; button.className='map-add-finding'; button.textContent='Dodaj znalezisko';
          button.addEventListener('click',click=>{
            click.stopPropagation();
            popup.current?.remove(); popup.current=null;
            callbacks.current.onPick(point);
          });
          content.append(heading,coordinates,button);
          popup.current=new libre.Popup({closeOnClick:false,offset:12,className:'finding-picker-popup',maxWidth:'260px'})
            .setLngLat([point.longitude,point.latitude]).setDOMContent(content).addTo(view);
          const close=popup.current.getElement().querySelector('button.maplibregl-popup-close-button');
          close?.setAttribute('aria-label','Zamknij wybór miejsca'); close?.setAttribute('title','Zamknij');
        }
      });
      view.on('error',()=>setError('Nie udało się wczytać części mapy. Sprawdź połączenie lub użyj lokalizacji GPS w formularzu.'));
    }).catch(()=>{if(!disposed)setError('Mapa jest niedostępna. Przeglądarka musi obsługiwać WebGL. Możesz użyć lokalizacji GPS w formularzu.');});
    return ()=>{disposed=true;popup.current?.remove();popup.current=null;map.current?.remove();map.current=null;};
  },[styleUrl]);
  useEffect(()=>{popup.current?.remove();popup.current=null;},[mapId,selectedId]);
  useEffect(()=>{if(ready && map.current?.getSource('findings'))(map.current.getSource('findings') as GeoJSONSource).setData(data ?? empty);},[data,ready]);
  useEffect(()=>{if(ready && map.current?.getLayer('finding-points'))map.current.setPaintProperty('finding-points','circle-color',['case',['==',['get','id'],selectedId ?? ''],'#b7803d','#64834c']);},[selectedId,ready]);
  useEffect(()=>{if(ready && focus)map.current?.easeTo({center:[focus.longitude,focus.latitude],zoom:Math.max(map.current.getZoom(),10)});},[focus?.latitude,focus?.longitude,ready]);
  return <section className="map-card interactive-map" aria-label="Mapa Twoich znalezisk"><div className="map-canvas" ref={container} />
    {!styleUrl && <div className="map-empty"><h3>Mapa jest niedostępna</h3><p>Możesz nadal dodać znalezisko, korzystając z lokalizacji GPS w formularzu.</p></div>}
    {styleUrl && !ready && !error && <span className="map-label" role="status">Wczytywanie mapy…</span>}
    {error && <p className="map-error" role="alert">{error}</p>}
    {ready && <span className="map-label">Kliknij mapę, a następnie „Dodaj znalezisko”</span>}
  </section>;
}
