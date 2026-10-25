import { useEffect, useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Icon } from '../../components/Icon';
import { resendVerification, verifyEmail } from './api';

export function EmailVerificationPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const [token] = useState(() => new URLSearchParams(location.hash.slice(1)).get('token') ?? '');
  const [email, setEmail] = useState('');
  const [pending, setPending] = useState(false);
  const [verified, setVerified] = useState(false);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    // Fragment nie trafia do serwera; po odczytaniu usuwamy go również z bieżącego adresu.
    if (location.hash) navigate('/verify-email', { replace: true });
  }, [location.hash, navigate]);

  async function confirm() {
    setPending(true); setError(''); setNotice('');
    try { setNotice((await verifyEmail(token)).message); setVerified(true); }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Nie udało się potwierdzić adresu.'); }
    finally { setPending(false); }
  }
  async function resend(event: FormEvent) {
    event.preventDefault();
    setPending(true); setError(''); setNotice('');
    try { setNotice((await resendVerification(email.trim())).message); }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Nie udało się wysłać wiadomości.'); }
    finally { setPending(false); }
  }

  return <main className="session-screen verification-page"><section className="auth-card">
    <span className="auth-leaf"><Icon name="leaf" size={25} /></span>
    <p className="eyebrow">MYCOMAP · TWOJE KONTO</p>
    <h1>{verified ? 'Adres potwierdzony' : 'Potwierdź adres e-mail'}</h1>
    <p className="auth-intro">{verified ? 'Możesz już zalogować się do swojego dziennika.' : 'Otwórz link z wiadomości i zatwierdź poniżej. Link jest ważny przez 24 godziny.'}</p>
    {notice && <p className="auth-success" role="status">{notice}</p>}
    {error && <p className="auth-error" role="alert">{error}</p>}
    {token && !verified && <button className="auth-submit" disabled={pending} onClick={() => void confirm()}>{pending ? 'Chwileczkę…' : 'Potwierdź adres'}</button>}
    {!verified && <form onSubmit={resend} aria-busy={pending}>
      <h2>Potrzebujesz nowego linku?</h2>
      <p className="auth-intro">Sprawdź także folder ze spamem. Kolejną wiadomość można wysłać po 60 sekundach.</p>
      <label htmlFor="verification-email">Adres e-mail konta</label>
      <input id="verification-email" type="email" value={email} onChange={event => setEmail(event.target.value)} required maxLength={254} autoComplete="email" disabled={pending} />
      <button className="auth-submit" disabled={pending}>{pending ? 'Chwileczkę…' : 'Wyślij nowy link'}</button>
    </form>}
    <p className="auth-switch"><Link to="/login">Przejdź do logowania</Link></p>
  </section></main>;
}
