import { useReducedMotion } from 'motion/react'

interface Node { id: string; x: number; y: number; w: number; title: string; sub: string; tone?: 'ext' | 'bus' | 'store' }
const H = 60
const NODES: Node[] = [
  { id: 'ui', x: 10, y: 150, w: 140, title: 'Customer app', sub: 'React · Leaflet · ops', tone: 'ext' },
  { id: 'gw', x: 190, y: 150, w: 130, title: 'API gateway', sub: 'JWT · rate limits' },
  { id: 'order', x: 370, y: 40, w: 150, title: 'order-service', sub: 'saga · payments · SSE' },
  { id: 'inv', x: 370, y: 150, w: 150, title: 'inventory-service', sub: 'Lua stock · picking' },
  { id: 'dsp', x: 370, y: 260, w: 150, title: 'dispatch-service', sub: 'GEO · batching' },
  { id: 'kafka', x: 575, y: 150, w: 125, title: 'Kafka', sub: '3 event topics', tone: 'bus' },
  { id: 'redis', x: 745, y: 90, w: 130, title: 'Redis', sub: 'stock · GEO · SSE', tone: 'store' },
  { id: 'pg', x: 745, y: 210, w: 130, title: 'PostgreSQL', sub: 'orders · ledger', tone: 'store' },
]
const c = (id: string) => { const n = NODES.find(x => x.id === id)!; return { n, x: n.x + n.w / 2, y: n.y + H / 2 } }
const right = (id: string) => { const { n, y } = c(id); return [n.x + n.w, y] as const }
const left = (id: string) => { const { n, y } = c(id); return [n.x, y] as const }

const EDGES: string[] = [
  `M ${right('ui')} L ${left('gw')}`,
  `M ${right('gw')[0]} ${right('gw')[1]} C 345 180, 345 70, ${left('order')}`,
  `M ${right('gw')} L ${left('inv')}`,
  `M ${right('gw')[0]} ${right('gw')[1]} C 345 180, 345 290, ${left('dsp')}`,
  `M ${right('order')[0]} ${right('order')[1]} C 550 70, 550 180, ${left('kafka')}`,
  `M ${right('inv')} L ${left('kafka')}`,
  `M ${right('dsp')[0]} ${right('dsp')[1]} C 550 290, 550 180, ${left('kafka')}`,
  `M ${right('kafka')[0]} ${right('kafka')[1]} C 725 180, 725 120, ${left('redis')}`,
  `M ${right('kafka')[0]} ${right('kafka')[1]} C 725 180, 725 240, ${left('pg')}`,
]

export default function Architecture() {
  const reduce = useReducedMotion()
  return (
    <div className="overflow-x-auto">
      <svg viewBox="0 0 880 340" className="mx-auto min-w-[760px] max-w-full" role="img"
        aria-label="SwiftKart architecture: the app calls the gateway, which routes to order, inventory and dispatch services; they exchange events over Kafka and keep state in Redis and PostgreSQL.">
        {EDGES.map((d, i) => (
          <g key={i}>
            <path d={d} fill="none" stroke="#ddd8cb" strokeWidth="1.5" />
            {!reduce && <circle r="3.5" fill="#0b6b3a"><animateMotion dur={`${2.4 + (i % 4) * 0.4}s`} begin={`${i * 0.3}s`} repeatCount="indefinite" path={d} /></circle>}
          </g>
        ))}
        {NODES.map(n => (
          <g key={n.id} transform={`translate(${n.x} ${n.y})`}>
            <rect width={n.w} height={H} rx="14" fill={n.tone === 'bus' ? '#0b6b3a' : n.tone === 'ext' ? '#faf8f3' : n.tone === 'store' ? '#fff4dc' : '#fff'}
              stroke={n.tone === 'bus' ? '#0b6b3a' : n.tone === 'store' ? '#f3d9a0' : '#ddd8cb'} strokeDasharray={n.tone === 'ext' ? '4 4' : undefined} />
            <text x="14" y="26" fontSize="14" fontWeight="600" fill={n.tone === 'bus' ? '#fff' : '#1a1d1a'}>{n.title}</text>
            <text x="14" y="44" fontSize="11" fill={n.tone === 'bus' ? '#ffffffb0' : '#75796f'}>{n.sub}</text>
          </g>
        ))}
      </svg>
    </div>
  )
}
