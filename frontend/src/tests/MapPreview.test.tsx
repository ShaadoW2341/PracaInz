import { render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { MapPreview } from '../components/MapPreview';

it('wyjaśnia brak konfiguracji i nie pobiera domyślnych kafelków', () => {
  vi.stubEnv('VITE_MAP_STYLE_URL', '');
  render(<MapPreview />);
  expect(screen.getByText(/Podkład nie jest skonfigurowany/)).toBeInTheDocument();
  vi.unstubAllEnvs();
});
