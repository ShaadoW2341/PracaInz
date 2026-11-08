import { afterEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ObservationForm } from '../features/maps/ObservationForm';

afterEach(()=>{cleanup();vi.unstubAllGlobals();});
function form() {
  const client=new QueryClient({defaultOptions:{mutations:{retry:false}}});
  render(<QueryClientProvider client={client}><ObservationForm mapId="test-map" species={[{id:1,polishName:'Nieznany',latinName:null}]} onClose={()=>{}} onSaved={()=>{}} /></QueryClientProvider>);
}
it('wyjaśnia odmowę lokalizacji i kieruje do mapy, a współrzędne są tylko do odczytu',async()=>{
  vi.stubGlobal('navigator',{geolocation:{getCurrentPosition:vi.fn((_ok,error)=>error({code:1}))}});
  form();fireEvent.click(screen.getByRole('button',{name:'Użyj mojej lokalizacji GPS'}));
  expect(await screen.findByRole('alert')).toHaveTextContent('Odmówiono dostępu');
  expect(screen.getByRole('alert')).toHaveTextContent('wybierz miejsce na mapie');
  expect(screen.getByLabelText('Szerokość GPS')).toHaveAttribute('readonly');
  expect(screen.getByLabelText('Długość GPS')).toHaveAttribute('readonly');
});
it('nie wysyła znaleziska z pustymi lub nieprawidłowymi współrzędnymi',()=>{
  const fetch=vi.fn();vi.stubGlobal('fetch',fetch);form();
  fireEvent.submit(screen.getByRole('button',{name:'Zapisz znalezisko'}).closest('form')!);
  expect(screen.getByRole('alert')).toHaveTextContent('Wybierz lokalizację');
  vi.stubGlobal('navigator',{geolocation:{getCurrentPosition:vi.fn(ok=>ok({coords:{latitude:91,longitude:23}}))}});
  fireEvent.click(screen.getByRole('button',{name:'Użyj mojej lokalizacji GPS'}));
  fireEvent.submit(screen.getByRole('button',{name:'Zapisz znalezisko'}).closest('form')!);
  expect(fetch).not.toHaveBeenCalled();
});
