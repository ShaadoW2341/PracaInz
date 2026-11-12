import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Icon } from '../../components/Icon';
import { sharedMaps } from '../maps/api';

export function SharedMaps() {
  const [page, setPage] = useState(0);
  const maps = useQuery({ queryKey: ['maps','shared',page], queryFn: () => sharedMaps(page), refetchInterval: 15000, retry: false });
  return <main className="admin-page"><header className="admin-header"><Link to="/app" className="brand admin-brand"><Icon name="mushroom" size={30} />MycoMap</Link><Link className="secondary-button" to="/app">Moje mapy</Link></header>
    <section className="page-heading"><div><p className="eyebrow">WSPÓLNE LEŚNE ŚCIEŻKI</p><h1>Udostępnione mi<span>.</span></h1><p>Mapy, które inni użytkownicy udostępnili Ci do przeglądania.</p></div></section>
    {maps.isPending && <p role="status">Wczytywanie udostępnionych map…</p>}
    {maps.error && <div className="form-alert" role="alert">{maps.error.message} <button className="text-button" onClick={() => void maps.refetch()}>Spróbuj ponownie</button></div>}
    {maps.data?.total === 0 && <section className="card shared-empty"><Icon name="share" size={34} /><h2>Jeszcze nikt nie udostępnił Ci mapy</h2><p>Właściciel może dodać Cię po Twojej nazwie użytkownika.</p><Link className="secondary-button" to="/app">Wróć do swoich map</Link></section>}
    <div className="shared-map-grid">{!maps.isError && maps.data?.items.map(map => <Link className="card shared-map-card" key={map.id} to={`/app/maps/${map.id}`}>
      <span className="shared-map-icon"><Icon name="map" size={30} /></span><h2>{map.name}</h2><p>Od: <strong>{map.ownerUsername}</strong></p>
      <div className="shared-map-meta"><span>{map.observationCount} znalezisk</span><span>Tylko odczyt</span></div>
      <p className="shared-precision">{map.precision === 'COARSE' ? 'Lokalizacje przybliżone · około 1 km' : 'Dokładne współrzędne'}</p>
      <span className="shared-map-open">Otwórz mapę <Icon name="arrow" size={17} /></span>
    </Link>)}</div>
    {maps.data && maps.data.total > 50 && <div className="pagination"><button disabled={page === 0} onClick={() => setPage(value => value - 1)}>Poprzednia</button><span>Strona {page + 1}</span><button disabled={(page + 1) * 50 >= maps.data.total} onClick={() => setPage(value => value + 1)}>Następna</button></div>}
  </main>;
}
