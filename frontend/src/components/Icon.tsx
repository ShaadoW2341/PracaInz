const paths = {
  grid: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  map: 'm3 5 6-2 6 2 6-2v16l-6 2-6-2-6 2z M9 3v16 M15 5v16',
  pin: 'M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0Z M15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
  chart: 'M4 20V10 M10 20V4 M16 20v-7 M22 20H2',
  share: 'm8 11 8-5 M8 13l8 5 M8 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z M22 4a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z M22 20a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
  leaf: 'M20 3C9 2 2 7 5 15c3 8 15 4 15-12Z M4 22 16 8',
  arrow: 'M5 12h14 m-5-5 5 5-5 5',
  calendar: 'M5 5h14a2 2 0 0 1 2 2v13H3V7a2 2 0 0 1 2-2Z M7 2v6 M17 2v6 M3 11h18',
  lock: 'M5 11h14v10H5z M8 11V7a4 4 0 0 1 8 0v4',
  mushroom: 'M3 12a9 9 0 0 1 18 0H3Z M10 12l-1 9h6l-1-9 M8 8h.01 M15 7h.01',
  layers: 'm12 2 10 6-10 6L2 8Z M2 12l10 6 10-6 M2 16l10 6 10-6',
  refresh: 'M20 7v5h-5 M4 17v-5h5 M5 7a8 8 0 0 1 13-2l2 2 M19 17a8 8 0 0 1-13 2l-2-2',
  info: 'M12 11v6 M12 7h.01 M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z',
  basket: 'm3 9 2 11h14l2-11H3Z M7 9l4-7 M17 9l-4-7 M9 13v3 M15 13v3',
};

export function Icon({ name, size = 20 }: { name: keyof typeof paths; size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name]} /></svg>;
}
