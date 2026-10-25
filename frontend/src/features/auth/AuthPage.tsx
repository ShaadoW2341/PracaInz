import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/http';
import { Forest } from '../../components/Forest';
import { Icon } from '../../components/Icon';
import { register } from './api';
import { useAuth } from './AuthProvider';

export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const auth = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const isRegister = mode === 'register';
  const state = location.state as { from?: string; registered?: boolean; username?: string } | null;
  const [username, setUsername] = useState(state?.username ?? '');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [fields, setFields] = useState<Record<string, string>>({});
  const destination = state?.from === '/app' || state?.from?.startsWith('/app/') ? state.from : '/app';
  if (auth.user && !auth.isError) return <Navigate to={destination} replace />;

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (pending) return;
    setError(''); setFields({});
    if (isRegister && password !== confirmation) { setFields({ confirmation: 'Hasła muszą być takie same.' }); return; }
    if (new TextEncoder().encode(password).length > 72) { setFields({ password: 'Hasło może zajmować maksymalnie 72 bajty UTF-8. Użyj mniej znaków.' }); return; }
    setPending(true);
    try {
      if (isRegister) {
        await register({ username: username.trim(), email: email.trim(), password });
        setPassword(''); setConfirmation('');
        navigate('/login', { replace: true, state: { registered: true, username: username.trim() } });
      } else {
        await auth.login({ username: username.trim(), password });
        setPassword('');
        navigate(destination, { replace: true });
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Wystąpił błąd. Spróbuj ponownie.');
      if (cause instanceof ApiError) setFields(cause.fieldErrors);
    } finally { setPending(false); }
  }

  return <div className="auth-layout">
    <aside className="auth-story">
      <Link className="brand" to="/login"><span className="brand-icon"><Icon name="mushroom" size={37} /></span><span>MycoMap<span className="brand-period">.</span><small>Odkrywaj. Mapuj. Pamiętaj.</small></span></Link>
      <div className="auth-story-copy"><p className="eyebrow">TWÓJ LEŚNY DZIENNIK</p><h1>Każde znalezisko<br />ma swoją historię.</h1><p>Zachowaj wspomnienia z lasu.<br />Twoje miejsca i odkrycia — w jednej przestrzeni.</p></div>
      <Forest /><p className="auth-story-footer"><Icon name="lock" size={15} /> Z myślą o Twojej prywatności</p>
    </aside>
    <main className="auth-main">
      <div className="auth-card">
        <span className="auth-leaf"><Icon name="leaf" size={25} /></span>
        <p className="eyebrow">{isRegister ? 'POCZĄTEK NOWEJ ŚCIEŻKI' : 'DOBRZE CIĘ WIDZIEĆ'}</p>
        <h1>{isRegister ? 'Załóż swój dziennik' : 'Wróć na leśną ścieżkę'}</h1>
        <p className="auth-intro">{isRegister ? 'Utwórz konto i przygotuj miejsce na swoje odkrycia.' : 'Zaloguj się do swojej przestrzeni MycoMap.'}</p>
        {!isRegister && state?.registered && <p className="auth-success" role="status">Konto zostało utworzone. Sprawdź pocztę i potwierdź adres e-mail, zanim się zalogujesz.</p>}
        {auth.isError && <p className="auth-notice">Serwer jest chwilowo niedostępny. Możesz ponowić próbę logowania po jego uruchomieniu.</p>}
        <form onSubmit={submit} aria-busy={pending}>
          <label htmlFor="username">Nazwa użytkownika</label>
          <input id="username" name="username" value={username} onChange={event => setUsername(event.target.value)} required minLength={isRegister ? 3 : undefined} maxLength={32} pattern={isRegister ? '[A-Za-z0-9_.\\-]{3,32}' : undefined} autoComplete="username" autoCapitalize="none" spellCheck={false} aria-invalid={!!fields.username} aria-describedby={fields.username ? 'username-error' : isRegister ? 'username-hint' : undefined} disabled={pending} />
          {isRegister && !fields.username && <small id="username-hint">3–32 znaki: litery bez polskich znaków, cyfry, kropka, myślnik, podkreślenie.</small>}
          {fields.username && <small className="field-error" id="username-error">{fields.username}</small>}
          {isRegister && <><label htmlFor="email">Adres e-mail</label><input id="email" name="email" type="email" value={email} onChange={event => setEmail(event.target.value)} required maxLength={254} autoComplete="email" autoCapitalize="none" aria-invalid={!!fields.email} aria-describedby={fields.email ? 'email-error' : undefined} disabled={pending} />{fields.email && <small className="field-error" id="email-error">{fields.email}</small>}</>}
          <label htmlFor="password">Hasło</label>
          <input id="password" name="password" type="password" value={password} onChange={event => setPassword(event.target.value)} required minLength={isRegister ? 12 : undefined} maxLength={72} autoComplete={isRegister ? 'new-password' : 'current-password'} aria-invalid={!!fields.password} aria-describedby={fields.password ? 'password-error' : isRegister ? 'password-hint' : undefined} disabled={pending} />
          {isRegister && !fields.password && <small id="password-hint">Minimum 12 znaków. Maksymalnie 72 bajty UTF-8; polskie znaki zajmują więcej niż jeden bajt.</small>}
          {fields.password && <small className="field-error" id="password-error">{fields.password}</small>}
          {isRegister && <><label htmlFor="confirmation">Powtórz hasło</label><input id="confirmation" name="confirmation" type="password" value={confirmation} onChange={event => setConfirmation(event.target.value)} required autoComplete="new-password" maxLength={72} aria-invalid={!!fields.confirmation} aria-describedby={fields.confirmation ? 'confirmation-error' : undefined} disabled={pending} />{fields.confirmation && <small className="field-error" id="confirmation-error">{fields.confirmation}</small>}</>}
          {error && <p className="auth-error" role="alert">{error}</p>}
          <button className="auth-submit" type="submit" disabled={pending}>{pending ? 'Chwileczkę…' : isRegister ? 'Utwórz konto' : 'Zaloguj się'}{!pending && <Icon name="arrow" size={17} />}</button>
        </form>
        <p className="auth-switch">{isRegister ? 'Masz już konto? ' : 'Nie masz jeszcze konta? '}<Link to={isRegister ? '/login' : '/register'}>{isRegister ? 'Zaloguj się' : 'Zarejestruj się'}</Link></p>
        {!isRegister && <p className="auth-switch"><Link to="/verify-email">Nie dotarł link potwierdzający?</Link></p>}
        <p className="auth-footnote">MycoMap · Projekt inżynierski</p>
      </div>
    </main>
  </div>;
}
