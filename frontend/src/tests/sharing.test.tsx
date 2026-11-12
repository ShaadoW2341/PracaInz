import { afterEach, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { ReactNode } from 'react';
import { MapsWorkspace } from '../features/maps/MapsWorkspace';
import { SharingDialog } from '../features/sharing/SharingDialog';
import { ApiError } from '../api/http';
import * as maps from '../features/maps/api';
import * as sharing from '../features/sharing/api';
import * as photos from '../features/photos/api';

vi.mock('../features/auth/AuthProvider', () => ({ useAuth: () => ({user:{id:'reader',username:'Odbiorca',role:'USER'},logout:async()=>{}}) }));
vi.mock('../features/maps/InteractiveMap', () => ({ InteractiveMap: ({readOnly}:{readOnly:boolean}) => <div data-testid="map" data-readonly={String(readOnly)} /> }));
vi.mock('../components/EditorDialog', () => ({ EditorDialog: ({label,children}:{label:string;children:ReactNode}) => <div role="dialog" aria-label={label}>{children}</div> }));
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

const shared:maps.MushroomMap={id:'shared-map',name:'Las właściciela',description:'',isDefault:false,observationCount:1,canEdit:false,ownerUsername:'Wlasciciel',precision:'COARSE'};
const observation:maps.Observation={id:'finding',mapId:'shared-map',speciesId:2,speciesName:'Borowik',latinName:null,quantity:2,observedOn:'2026-10-01',description:'',latitude:52.705,longitude:23.865};
function showWorkspace() {
  const client=new QueryClient({defaultOptions:{queries:{retry:false,gcTime:0},mutations:{retry:false}}});
  vi.spyOn(maps,'maps').mockResolvedValue({items:[],total:0,page:0,size:50});
  vi.spyOn(maps,'species').mockResolvedValue([{id:2,polishName:'Borowik',latinName:null}]);
  vi.spyOn(maps,'observations').mockResolvedValue({items:[observation],total:1,page:0,size:20});
  vi.spyOn(maps,'getObservation').mockResolvedValue(observation);
  render(<QueryClientProvider client={client}><MemoryRouter initialEntries={['/app/maps/shared-map']}><Routes><Route path="/app/maps/:id" element={<MapsWorkspace />} /></Routes></MemoryRouter></QueryClientProvider>);
  return client;
}

it('odbiorca nie dostaje kontrolek edycji ani zapytań o prywatne zdjęcia',async()=>{
  vi.spyOn(maps,'getMap').mockResolvedValue(shared);
  const images=vi.spyOn(photos,'photos').mockResolvedValue([]);
  showWorkspace();
  expect(await screen.findByText('Mapa od: Wlasciciel · tylko odczyt')).toBeInTheDocument();
  expect(screen.getByTestId('map')).toHaveAttribute('data-readonly','true');
  for(const name of ['Nowa mapa','Dodaj znalezisko','Ustawienia mapy','Udostępnij'])expect(screen.queryByRole('button',{name})).not.toBeInTheDocument();
  fireEvent.click(await screen.findByRole('button',{name:/Borowik/}));
  expect(await screen.findByText('Notatki są prywatne w trybie przybliżonym.')).toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'Edytuj'})).not.toBeInTheDocument();
  expect(images).not.toHaveBeenCalled();
});

it('po cofnięciu dostępu usuwa widok danych także z pamięci zapytań',async()=>{
  let revoked=false;
  vi.spyOn(maps,'getMap').mockImplementation(async()=>{if(revoked)throw new ApiError(404,'NOT_FOUND','Cofnięto dostęp.');return shared;});
  const client=showWorkspace();
  expect(await screen.findByRole('button',{name:/Borowik/})).toBeInTheDocument();
  revoked=true;
  await act(async()=>{await client.invalidateQueries({queryKey:['maps','detail','shared-map']});});
  expect(await screen.findByRole('heading',{name:'Mapa jest niedostępna'})).toBeInTheDocument();
  expect(screen.queryByRole('button',{name:/Borowik/})).not.toBeInTheDocument();
  expect(client.getQueryData(['maps','shared-map','observations','COARSE',{speciesId:'',from:'',to:''},0])).toBeUndefined();
});

it('dokładne udostępnienie wymaga wybrania odbiorcy i świadomego potwierdzenia',async()=>{
  vi.spyOn(sharing,'shares').mockResolvedValue({items:[],total:0,page:0,size:20});
  vi.spyOn(sharing,'searchUsers').mockResolvedValue([{id:'recipient',username:'demo-reader'}]);
  const create=vi.spyOn(sharing,'createShare').mockResolvedValue({id:'share',username:'demo-reader',precision:'EXACT',createdAt:'2026-11-12T10:00:00Z'});
  const client=new QueryClient({defaultOptions:{queries:{retry:false,gcTime:0},mutations:{retry:false}}});
  render(<QueryClientProvider client={client}><SharingDialog map={{...shared,canEdit:true,precision:'EXACT'}} onClose={()=>{}} /></QueryClientProvider>);
  fireEvent.change(screen.getByLabelText('Nazwa użytkownika'),{target:{value:'demo'}});
  fireEvent.click(await screen.findByRole('button',{name:'demo-reader'}));
  fireEvent.change(screen.getByLabelText('Dokładność lokalizacji'),{target:{value:'EXACT'}});
  const submit=screen.getByRole('button',{name:'Udostępnij wybranemu użytkownikowi'});
  expect(submit).toBeDisabled();expect(create).not.toHaveBeenCalled();
  fireEvent.click(screen.getByLabelText('Świadomie udostępniam dokładne współrzędne i notatki.'));
  expect(submit).toBeEnabled();fireEvent.click(submit);
  await waitFor(()=>expect(create).toHaveBeenCalledWith('shared-map','demo-reader','EXACT',true));
});
