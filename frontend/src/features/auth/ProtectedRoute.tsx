import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './AuthProvider';

export function ProtectedRoute() {
  const auth = useAuth();
  const location = useLocation();
  if (auth.isPending) return <main className="session-screen" role="status">Sprawdzanie sesji…</main>;
  if (auth.isError) return <main className="session-screen"><h1>Nie udało się sprawdzić sesji</h1><p role="alert">Sprawdź, czy backend jest uruchomiony, i spróbuj ponownie.</p><button className="auth-submit" onClick={auth.refresh}>Spróbuj ponownie</button></main>;
  if (!auth.user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <Outlet />;
}
