import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';

export function AdminRoute() {
  const auth = useAuth();
  return auth.user?.role === 'ADMIN' ? <Outlet /> : <Navigate to="/app" replace />;
}
