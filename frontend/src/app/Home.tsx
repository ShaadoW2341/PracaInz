import { useQuery } from '@tanstack/react-query';
import { fetchHealth } from '../api/health';
import { MapPreview } from '../components/MapPreview';
import { Icon } from '../components/Icon';
import { Forest } from '../components/Forest';
import { MushroomIllustration } from '../components/MushroomIllustration';
import { useState } from 'react';
import { useAuth } from '../features/auth/AuthProvider';

export function Home() {
  const auth = useAuth();
  const [loggingOut, setLoggingOut] = useState(false);
  const [logoutError, setLogoutError] = useState('');
  const health = useQuery({ queryKey: ['health'], queryFn: fetchHealth, retry: false, refetchInterval: 30000 });

  async function logout() {
    setLoggingOut(true);
    setLogoutError('');
    try { await auth.logout(); }
    catch (cause) { setLogoutError(cause instanceof Error ? cause.message : 'Nie udało się wylogować. Spróbuj ponownie.'); }
    finally { setLoggingOut(false); }
  }

  return <div className="dashboard-shell">
    <aside className="sidebar">
      <a className="brand" href="/" aria-label="MycoMap — strona główna">
        <span className="brand-icon"><Icon name="mushroom" size={35} /></span>
        <span>MycoMap<span className="brand-period">.</span><small>Odkrywaj. Mapuj. Pamiętaj.</small></span>
      </a>
      <p className="nav-caption">TWÓJ LEŚNY DZIENNIK</p>
      <nav className="side-nav" aria-label="Nawigacja główna">
        <a className="nav-active" href="#panel" aria-current="page"><Icon name="grid" />Panel<span className="nav-indicator" /></a>
        <a href="#mapa"><Icon name="map" />Mapa znalezisk</a>
        <a href="#statystyki"><Icon name="chart" />Statystyki<span className="nav-soon">Wkrótce</span></a>
        <a href="#prywatnosc"><Icon name="share" />Udostępnienia</a>
      </nav>
      <div className="sidebar-bottom">
        <Forest />
        <blockquote>„Każde znalezisko<br />ma swoją historię.<br /><em>Zachowaj ją na mapie.</em>”</blockquote>
        <div className="sidebar-foot"><Icon name="leaf" size={15} /><span>Z myślą o lesie i Twoich miejscach</span></div>
      </div>
    </aside>

    <div className="workspace" id="panel">
      <header className="topbar">
        <div className="breadcrumb">Mój dziennik <span>/</span> <strong>Panel</strong></div>
        <div className="topbar-right">
          <span className="preview-badge">Podgląd interfejsu</span><span className="topbar-divider" />
          <span className="profile-avatar"><Icon name="leaf" size={18} /></span>
          <span className="profile-name">{auth.user?.username}<small>Twoja przestrzeń</small></span>
          <button className="logout-button" onClick={() => void logout()} disabled={loggingOut}>{loggingOut ? 'Wylogowywanie…' : 'Wyloguj się'}</button>
        </div>
      </header>

      <main className="dashboard-main">
        {logoutError && <p className="auth-error" role="alert">{logoutError}</p>}
        <section className="page-heading">
          <div><p className="eyebrow">BLISKO NATURY, BLISKO WSPOMNIEŃ</p>
            <h1>Twoje leśne ścieżki<span>.</span></h1>
            <p>Każda wyprawa to nowa historia. Tutaj znajdą swoje miejsce.</p>
          </div>
          <span className="private-pill"><Icon name="lock" size={14} /> Prywatny dziennik</span>
        </section>

        <div className="dashboard-columns">
          <div className="main-column">
            <section className="map-section" id="mapa" aria-label="Mapa znalezisk">
              <div className="map-toolbar">
                <div className="map-title"><Icon name="map" size={19} /><h2>Mapa znalezisk</h2></div>
                <span className="subtle-label">Twoja perspektywa na las</span>
              </div>
              <div className="filter-bar">
                <span className="filter-chip"><Icon name="mushroom" size={15} />Wszystkie gatunki</span>
                <span className="filter-chip"><Icon name="calendar" size={15} />Cały sezon</span>
                <span className="filter-note">Filtry pojawią się wraz z obserwacjami</span>
              </div>
              <MapPreview />
              <div className="map-footer">
                <span><span className="tiny-dot" />Miejsce na Twoje przyszłe odkrycia</span>
                <span><Icon name="lock" size={12} /> Dokładne lokalizacje tylko dla Ciebie</span>
              </div>
            </section>

            <section className="stats-section" id="statystyki" aria-label="Statystyki obserwacji">
              <div className="section-title"><h2>Twój sezon w liczbach</h2><span>Analiza zapisanych obserwacji</span></div>
              <div className="stats-grid">
                <article className="card metric-card">
                  <span className="metric-icon amber"><Icon name="basket" size={23} /></span>
                  <p>Zapisane obserwacje</p><strong className="metric-value">—</strong>
                  <span className="metric-caption">Twój dziennik czeka na pierwszy wpis</span>
                  <div className="metric-decoration"><Icon name="mushroom" size={65} /></div>
                </article>
                <article className="card species-card">
                  <div className="card-heading"><h3>Najczęstsze gatunki</h3><Icon name="leaf" size={17} /></div>
                  <div className="species-empty">
                    <span className="outline-icon"><Icon name="mushroom" size={28} /></span>
                    <p>Co odkryjesz w tym sezonie?</p>
                    <span>Ranking powstanie na podstawie<br />Twoich zapisanych obserwacji.</span>
                  </div>
                </article>
                <article className="card history-card">
                  <div className="card-heading"><h3>Obserwacje w czasie</h3><span className="mini-label">Miesięcznie</span></div>
                  <div className="chart-empty">
                    <div className="chart-grid" />
                    <div className="chart-empty-message"><Icon name="chart" size={23} /><p>Każdy sezon ma swój rytm</p><span>Tu zobaczysz historię swoich obserwacji.</span></div>
                  </div>
                  <div className="chart-months">{['Sty', 'Lut', 'Mar', 'Kwi', 'Maj', 'Cze', 'Lip', 'Sie', 'Wrz', 'Paź', 'Lis', 'Gru'].map(month => <span key={month}>{month}</span>)}</div>
                </article>
              </div>
              <p className="stage-note"><Icon name="info" size={14} />To projekt w budowie. Zapisywanie obserwacji i analizy będą dostępne w kolejnych etapach.</p>
            </section>
          </div>

          <aside className="detail-column">
            <section className="card observation-card">
              <div className="card-heading"><h2>Szczegóły znaleziska</h2><Icon name="pin" size={18} /></div>
              <MushroomIllustration />
              <div className="observation-empty">
                <span className="outlined-pin"><Icon name="pin" size={20} /></span>
                <h3>Tu zaczyna się historia</h3>
                <p>Po dodaniu obserwacji wybierzesz punkt na mapie, aby zobaczyć gatunek, zdjęcia i notatki z wyprawy.</p>
                <span className="planned-label">Dostępne w kolejnych etapach</span>
              </div>
            </section>
            <section className="card activity-card">
              <div className="card-heading"><h3>Ostatnia aktywność</h3><Icon name="arrow" size={17} /></div>
              <div className="activity-empty">
                <span className="activity-line" /><span className="activity-dot"><Icon name="leaf" size={15} /></span>
                <div><strong>Przed Tobą nowe odkrycia</strong><p>Tutaj pojawią się zapisane wyprawy<br />i ostatnie obserwacje.</p></div>
              </div>
            </section>
            <section className="privacy-card" id="prywatnosc">
              <span className="privacy-icon"><Icon name="lock" size={19} /></span>
              <div><h3>Twoje miejsca. Twoje zasady.</h3><p>Docelowo mapy będą prywatne. Przy udostępnianiu lokalizacja będzie domyślnie przybliżona.</p></div>
            </section>
          </aside>
        </div>

        <footer className="dashboard-footer">
          <div className="system-status" aria-live="polite">
            <span className={`status-dot ${health.isSuccess ? 'online' : ''}`} />
            <span>{health.isPending ? 'Sprawdzanie połączenia…' : health.isError ? 'Backend niedostępny' : 'API i baza danych gotowe'}</span>
            <button className="refresh-button" aria-label="Sprawdź połączenie ponownie" onClick={() => void health.refetch()} disabled={health.isFetching}><Icon name="refresh" size={13} /></button>
          </div>
          <span>MycoMap · Projekt inżynierski <span className="footer-separator">/</span> Etap 1</span>
        </footer>
      </main>
    </div>
  </div>;
}
