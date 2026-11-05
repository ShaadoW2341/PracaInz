import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Icon } from '../../components/Icon';
import { Forest } from '../../components/Forest';
import { MushroomIllustration } from '../../components/MushroomIllustration';
import { useAuth } from '../auth/AuthProvider';
import { ApiError } from '../../api/http';
import { InteractiveMap } from './InteractiveMap';
import { MapForm } from './MapForm';
import { ObservationForm, type Coordinates } from './ObservationForm';
import * as api from './api';

const emptyFilters:api.Filters={speciesId:'',from:'',to:''};
export function MapsWorkspace() {
  const auth=useAuth(), client=useQueryClient(), navigate=useNavigate();
  const {id:routeId}=useParams();
  const [mapPage,setMapPage]=useState(0);
  const maps=useQuery({queryKey:['maps','list',mapPage],queryFn:()=>api.maps(mapPage)});
  const id=routeId ?? maps.data?.items[0]?.id;
  const selectedMap=useQuery({queryKey:['maps','detail',id],queryFn:()=>api.getMap(id!),enabled:!!id});
  const species=useQuery({queryKey:['species'],queryFn:api.species});
  const [filters,setFilters]=useState<api.Filters>(emptyFilters);
  const [page,setPage]=useState(0);
  const [bbox,setBbox]=useState('');
  const list=useQuery({queryKey:['maps',id,'observations',filters,page],queryFn:()=>api.observations(id!,filters,page),enabled:!!id && selectedMap.isSuccess});
  const features=useQuery({queryKey:['maps',id,'geojson',filters,bbox],queryFn:()=>api.geoJson(id!,filters,bbox),enabled:!!id && !!bbox && selectedMap.isSuccess});
  const [selectedId,setSelectedId]=useState<string>();
  const selected=useQuery({queryKey:['maps','observation',selectedId],queryFn:()=>api.getObservation(selectedId!),enabled:!!selectedId});
  const [editor,setEditor]=useState<'observation'|'map'|null>(null);
  const [editingObservation,setEditingObservation]=useState<api.Observation>();
  const [editingMap,setEditingMap]=useState<api.MushroomMap>();
  const [coordinates,setCoordinates]=useState<Coordinates>();
  const [draftKey,setDraftKey]=useState(0);
  const [confirmation,setConfirmation]=useState<'map'|'observation'|null>(null);
  const [logoutError,setLogoutError]=useState('');
  useEffect(() => {
    setSelectedId(undefined);
    setEditor(null);
    setConfirmation(null);
    setPage(0);
    setFilters(emptyFilters);
  }, [id]);
  const logout=useMutation({mutationFn:auth.logout,onError:error=>setLogoutError(error.message)});
  const deletion=useMutation({mutationFn:async()=>{
    if(confirmation==='map' && id){await api.deleteMap(id);navigate('/app/maps',{replace:true});}
    else if(confirmation==='observation' && selectedId)await api.deleteObservation(selectedId);
  },onSuccess:()=>{setSelectedId(undefined);setConfirmation(null);setEditor(null);setPage(0);setMapPage(0);void client.invalidateQueries({queryKey:['maps']});}});

  function switchMap(value:string){navigate('/app/maps/'+value);setSelectedId(undefined);setEditor(null);setConfirmation(null);setPage(0);setFilters(emptyFilters);}
  function pick(point?:Coordinates){setCoordinates(point);setEditingObservation(undefined);setEditor('observation');setSelectedId(undefined);setDraftKey(v=>v+1);}
  function select(value:string){setSelectedId(value);setEditor(null);setConfirmation(null);}
  function filter(value:Partial<api.Filters>){setFilters({...filters,...value});setPage(0);}
  const failed=[maps.error,selectedMap.error,species.error,list.error,features.error,selected.error].find(Boolean);
  const detail=selected.data?.mapId===id ? selected.data : undefined;

  return <div className="dashboard-shell"><aside className="sidebar">
    <Link className="brand" to="/app"><span className="brand-icon"><Icon name="mushroom" size={35} /></span><span>MycoMap<span className="brand-period">.</span><small>Odkrywaj. Mapuj. Pamiętaj.</small></span></Link>
    <p className="nav-caption">TWÓJ LEŚNY DZIENNIK</p><nav className="side-nav" aria-label="Nawigacja główna"><Link className="nav-active" to="/app/maps"><Icon name="map" />Moje mapy<span className="nav-indicator" /></Link><a href="#findings"><Icon name="pin" />Znaleziska</a><a href="#privacy"><Icon name="lock" />Prywatność</a></nav>
    <div className="sidebar-bottom"><Forest /><blockquote>„Każde znalezisko<br />ma swoją historię.<br /><em>Zachowaj ją na mapie.</em>”</blockquote><div className="sidebar-foot"><Icon name="leaf" size={15} />Twoje miejsca zostają Twoje</div></div>
  </aside><div className="workspace"><header className="topbar"><div className="breadcrumb">Mój dziennik <span>/</span><strong>Moje mapy</strong></div><div className="topbar-right"><span className="profile-avatar"><Icon name="leaf" size={18} /></span><span className="profile-name">{auth.user?.username}<small>Twoja przestrzeń</small></span><button className="logout-button" onClick={()=>logout.mutate()} disabled={logout.isPending}>Wyloguj się</button></div></header>
  <main className="dashboard-main">
    <section className="page-heading"><div><p className="eyebrow">TWÓJ LEŚNY DZIENNIK</p><h1>Twoje leśne ścieżki<span>.</span></h1><p>Zaznacz miejsce. Zapisz znalezisko. Wróć do wspomnień.</p></div><button className="auth-submit compact-button" onClick={()=>{setEditingMap(undefined);setEditor('map');setConfirmation(null);}}>Nowa mapa</button></section>
    {(failed || logoutError) && <p className="auth-error" role="alert">{logoutError || failed?.message}{failed instanceof ApiError && failed.status===401 && <Link to="/login"> Zaloguj się ponownie.</Link>}</p>}
    {maps.isPending && <p role="status">Wczytywanie map…</p>}
    <div className="map-picker"><label>Twoja mapa<select aria-label="Twoja mapa" value={id ?? ''} onChange={e=>switchMap(e.target.value)}>{routeId && !maps.data?.items.some(m=>m.id===routeId) && selectedMap.data && <option value={routeId}>{selectedMap.data.name}</option>}{maps.data?.items.map(m=><option key={m.id} value={m.id}>{m.name}{m.isDefault?' · domyślna':''}</option>)}</select></label>
      {selectedMap.data && <><button className="secondary-button" onClick={()=>{setEditingMap(selectedMap.data);setEditor('map');setConfirmation(null);}}>Ustawienia mapy</button>{!selectedMap.data.isDefault && <button className="text-button danger" onClick={()=>{setConfirmation('map');deletion.reset();}}>Usuń mapę</button>}</>}
      {maps.data && maps.data.total>50 && <div className="pagination"><button disabled={mapPage===0} onClick={()=>setMapPage(v=>v-1)}>Poprzednie mapy</button><button disabled={(mapPage+1)*50>=maps.data.total} onClick={()=>setMapPage(v=>v+1)}>Kolejne mapy</button></div>}
    </div>
    <div className="dashboard-columns maps-columns"><div className="main-column">
      <section className="map-section"><div className="map-toolbar"><div className="map-title"><Icon name="map" /><h2>{selectedMap.data?.name ?? 'Twoja mapa'}</h2></div><button className="auth-submit compact-button" disabled={!selectedMap.data || species.isPending} onClick={()=>pick()}>Dodaj znalezisko</button></div>
        {selectedMap.data?.description && <p className="map-description">{selectedMap.data.description}</p>}
        <div className="filter-bar live-filters"><label>Gatunek<select value={filters.speciesId} onChange={e=>filter({speciesId:e.target.value})}><option value="">Wszystkie gatunki</option>{species.data?.map(s=><option key={s.id} value={s.id}>{s.polishName}</option>)}</select></label><label>Od<input type="date" value={filters.from} onChange={e=>filter({from:e.target.value})} /></label><label>Do<input type="date" value={filters.to} onChange={e=>filter({to:e.target.value})} /></label><button className="text-button" onClick={()=>{setFilters(emptyFilters);setPage(0);}}>Wyczyść</button></div>
        <InteractiveMap data={features.data} selectedId={selectedId} focus={detail} onPick={point=>{if(selectedMap.data)pick(point);}} onSelect={select} onBounds={setBbox} />
        <div className="map-footer"><span>{features.isFetching ? 'Wczytywanie punktów…' : `${features.data?.features.length ?? 0} punktów w widocznym obszarze`}</span><span><Icon name="lock" size={12} /> Tylko dla Ciebie</span></div>
        {(!bbox || features.data?.truncated) && <p className="stage-note">Przybliż mapę, aby zobaczyć wszystkie punkty. Jednorazowo wyświetlamy maksymalnie 1000 obserwacji.</p>}
      </section>
      <section className="card findings-card" id="findings"><div className="section-title"><h2>Znaleziska {list.data ? `(${list.data.total})` : ''}</h2><span>Według daty znalezienia</span></div>
        {list.isFetching && <p role="status">Wczytywanie znalezisk…</p>}
        {list.data?.items.length===0 && <div className="list-empty"><Icon name="mushroom" size={31} /><h3>Jeszcze pusto na tej ścieżce</h3><p>Dodaj pierwsze znalezisko lub zmień filtry.</p></div>}
        <div className="findings-list">{list.data?.items.map(o=><button key={o.id} className={`finding-row ${selectedId===o.id?'selected':''}`} onClick={()=>select(o.id)}><span className="finding-icon"><Icon name="mushroom" /></span><span><strong>{o.speciesName}</strong><small>{o.observedOn} · {o.quantity} szt.</small></span><Icon name="arrow" size={17} /></button>)}</div>
        {list.data && list.data.total>20 && <div className="pagination"><button disabled={page===0} onClick={()=>setPage(v=>v-1)}>Poprzednia</button><span>Strona {page+1} z {Math.ceil(list.data.total/20)}</span><button disabled={(page+1)*20>=list.data.total} onClick={()=>setPage(v=>v+1)}>Następna</button></div>}
      </section>
    </div><aside className="detail-column">
      {confirmation && <section className="card editor-card"><h2>{confirmation==='map'?'Usunąć mapę i wszystkie jej znaleziska?':'Usunąć to znalezisko?'}</h2><p className="map-description">Tej operacji nie można cofnąć.</p>{deletion.error && <p className="form-alert" role="alert">{deletion.error.message}</p>}<div className="action-row"><button className="danger-button" disabled={deletion.isPending} onClick={()=>deletion.mutate()}>{deletion.isPending?'Usuwanie…':'Potwierdź usunięcie'}</button><button className="text-button" disabled={deletion.isPending} onClick={()=>setConfirmation(null)}>Anuluj</button></div></section>}
      {editor==='map' && <MapForm key={editingMap?.id ?? 'new-map'} initial={editingMap} onClose={()=>setEditor(null)} onSaved={result=>{switchMap(result.id);setMapPage(0);}} />}
      {editor==='observation' && id && <ObservationForm key={draftKey} mapId={id} species={species.data ?? []} initial={editingObservation} coordinates={coordinates} onClose={()=>setEditor(null)} onSaved={result=>{setEditor(null);setSelectedId(result.id);void client.invalidateQueries({queryKey:['maps']});}} />}
      {!editor && detail && <section className="card observation-card"><div className="card-heading"><h2>Szczegóły znaleziska</h2><button className="text-button" onClick={()=>setSelectedId(undefined)}>Zamknij</button></div><span className="detail-mushroom"><Icon name="mushroom" size={36} /></span><h3 className="observation-name">{detail.speciesName}</h3>{detail.latinName && <p className="latin-name">{detail.latinName}</p>}<dl className="observation-data"><dt>Data znalezienia</dt><dd>{detail.observedOn}</dd><dt>Liczba sztuk</dt><dd>{detail.quantity}</dd><dt>Współrzędne GPS</dt><dd>{detail.latitude.toFixed(6)}, {detail.longitude.toFixed(6)}</dd><dt>Notatka</dt><dd className="observation-note">{detail.description || 'Brak notatki'}</dd></dl><div className="action-row"><button className="secondary-button" onClick={()=>{setEditingObservation(detail);setEditor('observation');setDraftKey(v=>v+1);}}>Edytuj</button><button className="text-button danger" onClick={()=>{setConfirmation('observation');deletion.reset();}}>Usuń</button></div></section>}
      {!editor && !detail && <section className="card observation-card"><div className="card-heading"><h2>Szczegóły znaleziska</h2><Icon name="pin" size={18} /></div><MushroomIllustration /><div className="observation-empty"><h3>Każde odkrycie ma historię</h3><p>Wybierz znacznik lub wpis na liście, aby zobaczyć szczegóły. Kliknij puste miejsce na mapie, aby dodać nowe znalezisko.</p></div></section>}
      <section className="privacy-card" id="privacy"><Icon name="lock" /><div><h3>Twoje miejsca. Twoje zasady.</h3><p>Te mapy i dokładne współrzędne są dostępne wyłącznie na Twoim koncie. Słownik gatunków nie służy ocenie jadalności.</p></div></section>
    </aside></div>
    <footer className="dashboard-footer"><span>MycoMap · Twój leśny dziennik</span><span>Zachowaj swoje odkrycia na mapie.</span></footer>
  </main></div></div>;
}
