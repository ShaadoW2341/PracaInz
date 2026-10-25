export function Forest() {
  return <svg className="forest-art" viewBox="0 0 260 350" fill="none" aria-hidden="true">
    <path d="M0 120Q60 70 130 130T260 100V350H0Z" fill="#35513b" opacity=".3" />
    {[15, 50, 81, 108, 148, 184, 215, 245].map((x, i) => <g key={x} transform={`translate(${x} ${95 + (i % 3) * 32}) scale(${.65 + (i % 4) * .12})`} fill={i % 2 ? '#0d241c' : '#294737'} opacity=".8"><path d="M0 0-22 45h11l-25 40h15l-27 47h31l-4 68h12l-4-68h31L-9 85H6L-19 45h11Z" /></g>)}
    <path d="M0 276q50-26 110 3t150-10v81H0Z" fill="#0d211a" />
  </svg>;
}
