import { useEffect } from 'react'
import { MapContainer, Marker, Polyline, TileLayer, Tooltip, useMap } from 'react-leaflet'
import L from 'leaflet'

export type PinKind = 'store' | 'home' | 'rider'
export interface Pin { id: string; lat: number; lon: number; kind: PinKind; label?: string; color?: string; size?: number; pulse?: boolean }

const GLYPH: Record<PinKind, string> = {
  // Simple line glyphs (24×24 grid), drawn in white on the pin.
  store: '<path d="M4 10 5.5 5h13L20 10M4 10v9h16v-9M4 10h16M10 19v-5h4v5" />',
  home: '<path d="M4 11 12 4l8 7M6 9.5V20h12V9.5" />',
  rider: '<circle cx="6" cy="17" r="3"/><circle cx="18" cy="17" r="3"/><path d="M6 17h5l3-7h3M14 10l-2-3H9" />',
}
const DEFAULT_COLOR: Record<PinKind, string> = { store: '#0b6b3a', home: '#ffb020', rider: '#1a1d1a' }

const icon = (p: Pin) => {
  const size = p.size ?? (p.kind === 'store' ? 34 : p.kind === 'rider' ? 30 : 24)
  const color = p.color ?? DEFAULT_COLOR[p.kind]
  const radius = p.kind === 'store' ? '10px' : '9999px'
  const stroke = p.kind === 'home' ? '#1a1d1a' : '#ffffff'
  return L.divIcon({
    className: '',
    html: `<div class="${p.pulse ? 'pin-pulse' : ''}" style="position:relative;color:${color};width:${size}px;height:${size}px">
      <div style="position:relative;z-index:1;width:100%;height:100%;border-radius:${radius};background:${color};display:flex;align-items:center;justify-content:center;box-shadow:0 4px 10px -2px rgba(26,29,26,.35);border:2px solid #fff">
        <svg viewBox="0 0 24 24" width="${Math.round(size * 0.55)}" height="${Math.round(size * 0.55)}" fill="none" stroke="${stroke}" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">${GLYPH[p.kind]}</svg>
      </div></div>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
  })
}

function Fit({ pins, fitKey }: { pins: Pin[]; fitKey: string }) {
  const map = useMap()
  useEffect(() => {
    if (pins.length === 0) return
    if (pins.length === 1) map.setView([pins[0].lat, pins[0].lon], 14)
    else map.fitBounds(L.latLngBounds(pins.map(p => [p.lat, p.lon] as [number, number])), { padding: [48, 48], maxZoom: 15 })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fitKey, map])
  return null
}

/** OpenStreetMap, muted with CSS (see index.css) so the markers carry the information. `fitKey` controls when the view re-fits. */
export default function MapView({ pins, lines = [], fitKey, height = 320, className }: {
  pins: Pin[]; lines?: [number, number][][]; fitKey: string; height?: number; className?: string
}) {
  return (
    <MapContainer center={[pins[0]?.lat ?? 12.97, pins[0]?.lon ?? 77.59]} zoom={13} style={{ height, width: '100%' }}
      className={className ?? 'rounded-xl'} scrollWheelZoom={false}>
      <TileLayer attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://tile.openstreetmap.org/{z}/{x}/{y}.png" />
      <Fit pins={pins} fitKey={fitKey} />
      {lines.map((l, i) => <Polyline key={i} positions={l} pathOptions={{ color: '#0b6b3a', weight: 3, dashArray: '2 7', lineCap: 'round' }} />)}
      {pins.map(p => (
        <Marker key={p.id} position={[p.lat, p.lon]} icon={icon(p)}>
          {p.label && <Tooltip direction="top" offset={[0, -16]}>{p.label}</Tooltip>}
        </Marker>
      ))}
    </MapContainer>
  )
}
