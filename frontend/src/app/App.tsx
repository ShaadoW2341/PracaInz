import { Link, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../features/auth/AuthProvider';
import { AuthPage } from '../features/auth/AuthPage';
import { ProtectedRoute } from '../features/auth/ProtectedRoute';
import { Home } from './Home';
import { EmailVerificationPage } from '../features/auth/EmailVerificationPage';

export function App() {
  return <AuthProvider><Routes>
    <Route path="/" element={<Navigate to="/app" replace />} />
    <Route path="/login" element={<AuthPage key="login" mode="login" />} />
    <Route path="/register" element={<AuthPage key="register" mode="register" />} />
    <Route path="/verify-email" element={<EmailVerificationPage />} />
    <Route element={<ProtectedRoute />}>
      <Route path="/app" element={<Home />} />
    </Route>
    <Route path="*" element={<main className="not-found"><p className="eyebrow">404 / POZA SZLAKIEM</p><h1>Tego miejsca nie ma na mapie.</h1><Link to="/app">Wróć na początek →</Link></main>} />
  </Routes></AuthProvider>;
}
