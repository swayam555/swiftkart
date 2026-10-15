import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { AnimatePresence, motion } from 'motion/react'
import { ArrowLeft, Boxes, CheckCircle2, Clock3, FlaskConical, LogOut, Map as MapIcon, Pause, Play, Plus, RefreshCw } from 'lucide-react'
import clsx from 'clsx'
import { api, inr, post, sessions, STATUS_LABEL, type Order, type Rider, type Session, type Store } from '../api'
import MapView, { type Pin } from '../components/MapView'
import ProductArt from '../components/ProductArt'
import { Button, Card, ErrorNote, Logo, Pill, Skeleton, Stat, StatGrid, STATUS_TONE } from '../components/ui'

interface Overview {
  placed: number; delivered: number; avgDeliveryMinutes: number; avgPromisedMinutes: number; onTimeRate: number | null
  gmv: number; paymentFailures: number; cancelled: number; expired: number; byStatus: Record<string, number>
  simulator: { running: boolean; ordersPerMinute: number; placed: number; failed: number }
}
interface DispatchStats {
  riders: number; utilization: number; ordersWaitingForRider: number; oldestWaitMinutes: number; deliveriesTotal: number
  batchedTrips: number; ridersByStatus: Record<string, number>
}

const RIDER_COLOR: Record<string, string> = {
  TO_STORE: '#2563a8', AT_STORE: '#ffb020', DELIVERING: '#0b6b3a', AT_DROP: '#6aa84f', RETURNING: '#75796f',
}
const RIDER_LABEL: Record<string, string> = { TO_STORE: 'to store', AT_STORE: 'at store', DELIVERING: 'delivering', AT_DROP: 'at drop', RETURNING: 'returning' }

type Tab = 'live' | 'flash' | 'stock'
const TABS: { id: Tab; label: string; icon: typeof MapIcon }[] = [
  { id: 'live', label: 'Live operations', icon: MapIcon },
  { id: 'flash', label: 'Flash-sale lab', icon: FlaskConical },
  { id: 'stock', label: 'Stock & reconciliation', icon: Boxes },
]

export default function OpsConsole() {
  const [session, setSession] = useState<Session | null>(sessions.get('OPS'))
  if (!session) return <OpsLogin onLogin={setSession} />
  return <Console onLogout={() => { sessions.clear('OPS'); setSession(null) }} />
}

function OpsLogin({ onLogin }: { onLogin: (s: Session) => void }) {
  const [username, setUsername] = useState('ops')
  const [password, setPassword] = useState('swiftkart')
  const [error, setError] = useState<string | null>(null)
  const submit = async (e: FormEvent) => {
    e.preventDefault()
    try { const s = await post<Session>('/api/auth/ops', { username, password }, { role: 'OPS' }); sessions.set(s); onLogin(s) }
    catch (err) { setError((err as Error).message) }
  }
  return (
    <div className="flex min-h-screen flex-col bg-ink px-6 py-8">
      <Link to="/" className="inline-flex w-fit items-center gap-1.5 text-sm text-white/50 hover:text-white"><ArrowLeft className="h-4 w-4" /> Back to overview</Link>
      <motion.form onSubmit={submit} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="m-auto w-full max-w-sm rounded-[22px] bg-surface p-7 shadow-2xl">
        <Logo />
        <h1 className="display mt-6 text-2xl font-bold tracking-tight text-ink">Control room</h1>
        <p className="mt-1 text-sm text-muted">Live operations, flash sales and stock. Staff only.</p>
        <div className="mt-6 space-y-4">
          <label className="label">Username<input className="input" value={username} onChange={e => setUsername(e.target.value)} autoComplete="username" /></label>
          <label className="label">Password<input type="password" className="input" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" /></label>
          <ErrorNote error={error} />
          <Button type="submit" size="lg" className="w-full">Sign in</Button>
          <p className="text-center text-xs text-muted">Demo credentials are pre-filled: ops / swiftkart</p>
        </div>
      </motion.form>
    </div>
  )
}

function Console({ onLogout }: { onLogout: () => void }) {
  const [tab, setTab] = useState<Tab>('live')
  const [overview, setOverview] = useState<Overview | null>(null)
  const [dispatch, setDispatch] = useState<DispatchStats | null>(null)
  const [stores, setStores] = useState<Store[]>([])

  const refresh = useCallback(() => {
    api<Overview>('/api/ops/overview', { role: 'OPS' }).then(setOverview).catch(() => {})
    api<DispatchStats>('/api/dispatch/stats', { role: 'OPS' }).then(setDispatch).catch(() => {})
  }, [])
  useEffect(() => {
    api<Store[]>('/api/stores').then(setStores)
    refresh()
    const t = setInterval(refresh, 3000)
    return () => clearInterval(t)
  }, [refresh])

  const waiting = dispatch?.ordersWaitingForRider ?? 0
  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[240px_1fr] lg:bg-[linear-gradient(to_right,#1a1d1a_240px,transparent_240px)]">
      <aside className="sticky top-0 hidden h-screen flex-col bg-ink text-white lg:flex">
        <div className="flex h-[68px] items-center px-5"><Logo dark /></div>
        <nav className="flex-1 space-y-0.5 px-3">
          {TABS.map(t => (
            <button key={t.id} onClick={() => setTab(t.id)}
              className={clsx('relative flex w-full items-center gap-2.5 rounded-xl px-3 py-2.5 text-sm font-medium transition', tab === t.id ? 'text-white' : 'text-white/55 hover:text-white')}>
              {tab === t.id && <motion.span layoutId="ops-tab" className="absolute inset-0 rounded-xl bg-white/10" transition={{ type: 'spring', stiffness: 500, damping: 40 }} />}
              <t.icon className="relative h-4 w-4" /><span className="relative">{t.label}</span>
            </button>
          ))}
        </nav>
        <div className="mx-3 mb-3 rounded-xl bg-white/[0.06] p-3.5 text-xs text-white/60">
          <div className="flex items-center gap-1.5 font-medium text-sun"><Clock3 className="h-3.5 w-3.5" /> Demo clock 6×</div>
          <div className="mt-1">Simulated minutes pass six times faster than real ones.</div>
        </div>
        <div className="flex items-center justify-between border-t border-white/10 px-5 py-4 text-sm">
          <span className="text-white/55">Operations</span>
          <button onClick={onLogout} title="Sign out" className="rounded-md p-1.5 text-white/55 hover:bg-white/10 hover:text-white"><LogOut className="h-4 w-4" /></button>
        </div>
      </aside>

      <div className="min-w-0">
        <div className="flex items-center gap-3 overflow-x-auto bg-ink px-4 py-3 lg:hidden">
          <Logo dark />
          {TABS.map(t => <button key={t.id} onClick={() => setTab(t.id)} className={clsx('whitespace-nowrap rounded-lg px-3 py-1.5 text-sm', tab === t.id ? 'bg-white/15 text-white' : 'text-white/60')}>{t.label}</button>)}
        </div>
        <main className="mx-auto max-w-[1320px] space-y-5 p-5 lg:p-8">
          <div className="flex flex-wrap items-end justify-between gap-3">
            <div>
              <h1 className="display text-[30px] font-bold tracking-[-0.03em] text-ink">{TABS.find(t => t.id === tab)!.label}</h1>
              <p className="mt-1 text-sm text-muted">Last 15 simulated minutes · refreshes every 3 seconds</p>
            </div>
            {waiting > 20 && <Pill tone="red" dot>{waiting} orders waiting for a rider. Add riders below.</Pill>}
          </div>
          <StatGrid className="grid-cols-2 md:grid-cols-4 xl:grid-cols-7">
            <Stat label="Orders" value={overview?.placed ?? '…'} hint={`${overview?.delivered ?? 0} delivered`} />
            <Stat label="Avg delivery" value={overview ? `${overview.avgDeliveryMinutes} min` : '…'} hint={`promised ${overview?.avgPromisedMinutes ?? 0} min`} />
            <Stat label="On time" value={overview?.onTimeRate == null ? '-' : `${overview.onTimeRate}%`} hint="by the promise"
              tone={overview?.onTimeRate != null && overview.onTimeRate < 80 ? 'amber' : 'green'} />
            <Stat label="GMV" value={overview ? inr(overview.gmv) : '…'} hint="paid, not cancelled" />
            <Stat label="Rider utilisation" value={dispatch ? `${dispatch.utilization}%` : '…'} hint={`${dispatch?.riders ?? 0} riders on shift`} />
            <Stat label="Waiting for rider" value={dispatch ? waiting : '…'} hint={`oldest ${dispatch?.oldestWaitMinutes ?? 0} min`} tone={waiting > 5 ? 'red' : 'slate'} />
            <Stat label="Batched trips" value={dispatch?.batchedTrips ?? '…'} hint={`${dispatch?.deliveriesTotal ?? 0} deliveries`} />
          </StatGrid>
          <AnimatePresence mode="wait">
            <motion.div key={tab} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} transition={{ duration: 0.2 }}>
              {tab === 'live' && <LiveOps stores={stores} overview={overview} onChange={refresh} />}
              {tab === 'flash' && <FlashLab stores={stores} />}
              {tab === 'stock' && <StockBoard stores={stores} />}
            </motion.div>
          </AnimatePresence>
        </main>
      </div>
    </div>
  )
}

function LiveOps({ stores, overview, onChange }: { stores: Store[]; overview: Overview | null; onChange: () => void }) {
  const cities = useMemo(() => [...new Set(stores.map(s => s.city))], [stores])
  const [city, setCity] = useState('Bengaluru')
  const [riders, setRiders] = useState<Rider[]>([])
  const [orders, setOrders] = useState<Order[]>([])
  const [rate, setRate] = useState(20)
  const [surgeStore, setSurgeStore] = useState('')
  const [msg, setMsg] = useState<string | null>(null)

  useEffect(() => {
    const load = () => {
      api<Rider[]>('/api/dispatch/riders', { role: 'OPS' }).then(setRiders).catch(() => {})
      api<Order[]>('/api/ops/orders?activeOnly=true&limit=150', { role: 'OPS' }).then(setOrders).catch(() => {})
    }
    load()
    const t = setInterval(load, 1500)
    return () => clearInterval(t)
  }, [])

  const cityStores = stores.filter(s => s.city === city)
  const ids = new Set(cityStores.map(s => s.id))
  const pins: Pin[] = [
    ...cityStores.map(s => ({ id: s.id, lat: s.lat, lon: s.lon, kind: 'store' as const, label: s.name })),
    ...orders.filter(o => ids.has(o.storeId) && o.status !== 'PAYMENT_PENDING').map(o => ({ id: o.id, lat: o.customerLat, lon: o.customerLon, kind: 'home' as const, label: `${o.id} · ${STATUS_LABEL[o.status]}`, size: 18 })),
    ...riders.filter(r => ids.has(r.storeId) && r.status !== 'IDLE').map(r => ({ id: r.id, lat: r.lat, lon: r.lon, kind: 'rider' as const, label: `${r.name} · ${RIDER_LABEL[r.status] ?? r.status}${r.orders.length > 1 ? ' · batched' : ''}`, color: RIDER_COLOR[r.status], size: 24 })),
  ]
  const sim = overview?.simulator
  const start = () => post('/api/ops/simulator/start', { ordersPerMinute: rate }, { role: 'OPS' }).then(onChange)

  return (
    <div className="grid gap-5 xl:grid-cols-[1.75fr_1fr]">
      <Card padded={false} title="Live city map" subtitle="Stores, riders on a job and customers waiting"
        action={<div className="flex rounded-xl bg-ink/[0.05] p-0.5">{cities.map(c => (
          <button key={c} onClick={() => setCity(c)} className={clsx('rounded-lg px-3 py-1.5 text-xs font-medium transition', city === c ? 'bg-surface text-ink shadow-sm' : 'text-muted hover:text-ink')}>{c}</button>))}</div>}>
        <div className="px-5 pb-5">
          <div className="overflow-hidden rounded-xl border border-line"><MapView pins={pins} fitKey={city + cityStores.length} height={480} className="" /></div>
          <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1.5 text-xs text-muted">
            {Object.entries(RIDER_COLOR).map(([k, c]) => <span key={k} className="flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-full" style={{ background: c }} />{RIDER_LABEL[k]}</span>)}
            <span className="flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-full bg-sun ring-1 ring-ink/30" />customer waiting</span>
            <span>· idle riders hidden</span>
          </div>
        </div>
      </Card>
      <div className="space-y-5">
        <Card title="Order load generator" subtitle="Synthetic customers through the real checkout">
          <div className="flex items-baseline justify-between text-sm"><span className="text-ink-2">Orders per minute</span><span className="num font-semibold text-ink">{rate}</span></div>
          <input type="range" min={5} max={120} value={rate} onChange={e => setRate(+e.target.value)} className="mt-2 w-full accent-[#0b6b3a]" aria-label="Orders per minute" />
          <div className="mt-4 flex gap-2">
            {sim?.running
              ? <><Button variant="secondary" onClick={() => post('/api/ops/simulator/stop', undefined, { role: 'OPS' }).then(onChange)}><Pause className="h-4 w-4" /> Stop</Button>
                <Button variant="ghost" onClick={start}>Apply rate</Button></>
              : <Button onClick={start}><Play className="h-4 w-4" /> Start</Button>}
          </div>
          <div className="mt-3 flex items-center gap-2 text-xs text-muted">
            {sim?.running && <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-brand" />}
            {sim?.placed ?? 0} simulated orders placed{sim?.failed ? `, ${sim.failed} rejected` : ''}
          </div>
        </Card>
        <Card title="Surge staffing" subtitle="Orders piling up? Put more riders on shift.">
          <div className="flex gap-2">
            <select value={surgeStore} onChange={e => setSurgeStore(e.target.value)} aria-label="Store"
              className="h-10 min-w-0 flex-1 rounded-xl border border-line-2 bg-surface px-3 text-sm text-ink outline-none focus:border-brand">
              <option value="">Choose a store…</option>{stores.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
            <Button variant="dark" disabled={!surgeStore} onClick={() => post<{ riders: number }>('/api/dispatch/ops/riders', { storeId: surgeStore, count: 3 }, { role: 'OPS' })
              .then(r => setMsg(`Store now has ${r.riders} riders`)).catch(e => setMsg(e.message))}><Plus className="h-4 w-4" /> 3 riders</Button>
          </div>
          {msg && <div className="mt-2 text-xs text-brand">{msg}</div>}
        </Card>
        <Card padded={false} title="Active orders" subtitle={`${orders.length} in progress`}>
          <div className="mt-2 max-h-[300px] divide-y divide-line overflow-auto">
            {orders.length === 0 && <div className="px-5 py-8 text-center text-sm text-muted">No active orders. Start the load generator.</div>}
            {orders.slice(0, 60).map(o => (
              <Link key={o.id} to={`/ops/orders/${o.id}`} className="flex items-center gap-3 px-5 py-2.5 text-xs transition hover:bg-cream">
                <span className="font-mono text-ink-2">{o.id}</span><span className="flex-1 text-muted">{o.storeId}</span>
                <Pill tone={STATUS_TONE[o.status]}>{STATUS_LABEL[o.status]}</Pill>
              </Link>
            ))}
          </div>
        </Card>
      </div>
    </div>
  )
}

interface FlashResult {
  mode: string; storeId: string; units: number; buyers: number; toldYes: number; confirmed: number; soldOut: number
  refundedAfterPay: number; errors: number; oversold: number; durationMs: number; errorKinds: Record<string, number>
  stockAfter: { ledgerOnHand: number; redisAvailable: number; reserved: number }
}

function FlashLab({ stores }: { stores: Store[] }) {
  const [storeId, setStoreId] = useState('BLR-KOR')
  const [units, setUnits] = useState(50)
  const [buyers, setBuyers] = useState(300)
  const [runs, setRuns] = useState<FlashResult[]>([])
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const run = async (mode: 'ATOMIC' | 'NAIVE') => {
    setBusy(mode); setError(null)
    try { const r = await post<FlashResult>('/api/ops/flash-sale', { storeId, units, buyers, mode }, { role: 'OPS' }); setRuns(x => [r, ...x]) }
    catch (e) { setError((e as Error).message) } finally { setBusy(null) }
  }

  return (
    <div className="grid gap-5 xl:grid-cols-[1fr_1.9fr]">
      <Card title="12 PM flash sale" subtitle="PlayStation 5 at ₹29,990">
        <div className="flex items-center gap-4 rounded-xl bg-ink p-4 text-white">
          <ProductArt sku="FLASH-PS5" size="sm" />
          <p className="text-sm text-white/75">Hundreds of people tap <b className="text-white">Buy</b> in the same second. Each buyer runs reserve → pay → commit.</p>
        </div>
        <div className="mt-4 space-y-3">
          <label className="label">Dark store<select value={storeId} onChange={e => setStoreId(e.target.value)} className="input">{stores.map(s => <option key={s.id} value={s.id}>{s.name} ({s.city})</option>)}</select></label>
          <div className="grid grid-cols-2 gap-3">
            <label className="label">Consoles in stock<input type="number" className="input num" value={units} onChange={e => setUnits(+e.target.value)} /></label>
            <label className="label">Simultaneous buyers<input type="number" className="input num" value={buyers} onChange={e => setBuyers(+e.target.value)} /></label>
          </div>
        </div>
        <div className="mt-5 grid gap-2">
          <Button onClick={() => run('ATOMIC')} disabled={busy !== null}>{busy === 'ATOMIC' ? 'Running…' : 'Run with atomic Redis Lua (production)'}</Button>
          <Button variant="danger" onClick={() => run('NAIVE')} disabled={busy !== null}>{busy === 'NAIVE' ? 'Running…' : 'Run with naive check-then-act'}</Button>
        </div>
        <div className="mt-3"><ErrorNote error={error} /></div>
        <p className="mt-3 text-xs leading-relaxed text-muted">Confirmed flash orders are real: they are packed and delivered, so expect a rush of orders waiting for riders at that store.</p>
      </Card>
      <div className="space-y-5">
        {runs.length === 0 && (
          <div className="flex h-full min-h-[300px] flex-col items-center justify-center rounded-[var(--radius-card)] border border-dashed border-line-2 text-center">
            <FlaskConical className="h-9 w-9 text-line-2" strokeWidth={1.5} />
            <div className="mt-3 font-semibold text-ink">Run the sale in both modes and compare</div>
            <div className="mt-1 max-w-sm text-sm text-muted">The naive version tells far more buyers "reserved" than there are consoles.</div>
          </div>
        )}
        <AnimatePresence initial={false}>
          {runs.map((r, i) => (
            <motion.div key={runs.length - i} initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }}>
              <Card title={<span className="flex items-center gap-2">{r.mode === 'ATOMIC' ? <Pill tone="green" dot>Atomic reservation</Pill> : <Pill tone="red" dot>Naive check-then-act</Pill>}
                <span className="font-normal text-muted">{r.storeId} · {r.buyers} buyers for {r.units} units · {r.durationMs.toLocaleString('en-IN')} ms</span></span>}>
                <StatGrid className="grid-cols-2 md:grid-cols-5">
                  <Stat label="Told “reserved”" value={r.toldYes} tone={r.toldYes > r.units ? 'red' : 'slate'} />
                  <Stat label="Confirmed" value={r.confirmed} tone="green" />
                  <Stat label="Told “sold out”" value={r.soldOut} />
                  <Stat label="Refunded after paying" value={r.refundedAfterPay} tone={r.refundedAfterPay > 0 ? 'red' : 'green'} />
                  <Stat label="Oversold promises" value={r.oversold} tone={r.oversold > 0 ? 'red' : 'green'} />
                </StatGrid>
                {r.errors > 0 && <p className="mt-3 rounded-xl bg-sun-soft px-3.5 py-2.5 text-xs text-[#8a5600]">{r.errors} checkouts failed with errors
                  ({Object.entries(r.errorKinds).map(([k, v]) => `${k}: ${v}`).join(', ')}): the circuit breaker shed load instead of queueing. Run again once warm.</p>}
                <p className="mt-4 text-sm leading-relaxed text-ink-2">
                  {r.oversold === 0
                    ? `Exactly ${r.confirmed} customers got a console and everyone else was told "sold out" instantly, before paying. Stock after: ledger ${r.stockAfter.ledgerOnHand}, Redis ${r.stockAfter.redisAvailable}.`
                    : `${r.toldYes} customers were told their console was reserved, but only ${r.units} exist. ${r.refundedAfterPay} paid and had to be refunded. The Postgres ledger refused to go negative, and the reconciler will repair Redis (${r.stockAfter.redisAvailable} phantom units).`}
                </p>
              </Card>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </div>
  )
}

interface StockLine { sku: string; name: string; emoji: string; category: string; ledgerOnHand: number; reserved: number; available: number; soldLast30Min: number; hoursOfCover: number | null; restockSoon: boolean }
interface Reconciliation { ranAt: string | null; checked: number; drifts: { storeId: string; sku: string; ledgerOnHand: number; reservedCounter: number; liveReserved: number; redisAvailable: number; expectedAvailable: number; action: string }[] }

function StockBoard({ stores }: { stores: Store[] }) {
  const [storeId, setStoreId] = useState('BLR-KOR')
  const [lines, setLines] = useState<StockLine[] | null>(null)
  const [recon, setRecon] = useState<Reconciliation | null>(null)
  const [running, setRunning] = useState(false)
  const load = useCallback(() => {
    api<StockLine[]>(`/api/inventory/ops/stores/${storeId}/stock`, { role: 'OPS' }).then(setLines).catch(() => {})
    api<Reconciliation>('/api/inventory/ops/reconciliation', { role: 'OPS' }).then(setRecon).catch(() => {})
  }, [storeId])
  useEffect(() => { load(); const t = setInterval(load, 5000); return () => clearInterval(t) }, [load])
  const runRecon = async () => { setRunning(true); try { setRecon(await post<Reconciliation>('/api/inventory/ops/reconciliation/run', undefined, { role: 'OPS' })) } finally { setRunning(false) } }

  return (
    <div className="grid gap-5 xl:grid-cols-[1.9fr_1fr]">
      <Card padded={false} title="Stock board" subtitle="Most urgent restock first"
        action={<select value={storeId} onChange={e => setStoreId(e.target.value)} aria-label="Store" className="h-9 rounded-xl border border-line-2 bg-surface px-3 text-sm text-ink outline-none focus:border-brand">{stores.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select>}>
        <div className="mt-2 overflow-x-auto">
          <table className="w-full min-w-[640px] text-sm">
            <thead><tr className="border-b border-line text-left text-xs text-muted">
              <th className="px-5 py-2.5 font-medium">Product</th><th className="px-3 py-2.5 text-right font-medium">Ledger</th><th className="px-3 py-2.5 text-right font-medium">Reserved</th>
              <th className="px-3 py-2.5 text-right font-medium">Sellable</th><th className="px-3 py-2.5 text-right font-medium">Sold, 30 min</th><th className="px-3 py-2.5 font-medium">Cover</th><th className="px-5 py-2.5" />
            </tr></thead>
            <tbody className="divide-y divide-line">
              {!lines && <tr><td colSpan={7} className="p-5"><Skeleton className="h-40" /></td></tr>}
              {lines?.map(l => (
                <tr key={l.sku} className={clsx('transition hover:bg-cream', l.restockSoon && 'bg-sun-soft/50')}>
                  <td className="px-5 py-2"><span className="flex items-center gap-3"><ProductArt sku={l.sku} size="sm" className="!h-8 !w-8 !rounded-lg" /><span className="text-ink">{l.name}</span></span></td>
                  <td className="num px-3 py-2 text-right text-ink-2">{l.ledgerOnHand}</td><td className="num px-3 py-2 text-right text-ink-2">{l.reserved}</td>
                  <td className="num px-3 py-2 text-right font-semibold text-ink">{l.available}</td><td className="num px-3 py-2 text-right text-ink-2">{l.soldLast30Min}</td>
                  <td className="px-3 py-2 text-xs">{l.restockSoon ? <Pill tone="amber">{l.hoursOfCover} h · restock</Pill> : <span className="text-muted">{l.hoursOfCover == null ? '-' : `${l.hoursOfCover} h`}</span>}</td>
                  <td className="px-5 py-2 text-right"><Button size="sm" variant="secondary" onClick={() => post(`/api/inventory/ops/stores/${storeId}/restock`, { sku: l.sku, qty: 24 }, { role: 'OPS' }).then(load)}>+24</Button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
      <Card title="Redis ↔ ledger reconciliation" subtitle="Every 30 s; drift seen twice is repaired"
        action={<Button size="sm" variant="secondary" onClick={runRecon} disabled={running}><RefreshCw className={clsx('h-3.5 w-3.5', running && 'animate-spin')} /> Run now</Button>}>
        <div className="text-sm text-ink-2">Last run {recon?.ranAt ? new Date(recon.ranAt).toLocaleTimeString('en-IN') : 'not yet'} · <span className="num">{recon?.checked ?? 0}</span> counters checked</div>
        {recon && recon.drifts.length === 0 && (
          <div className="mt-4 flex items-center gap-2.5 rounded-xl bg-brand-soft px-3.5 py-3 text-sm font-medium text-brand"><CheckCircle2 className="h-4 w-4" /> All counters consistent</div>
        )}
        <ul className="mt-4 space-y-2">
          {recon?.drifts.map((d, i) => (
            <li key={i} className="rounded-xl border border-line p-3 text-xs">
              <div className="flex items-center justify-between font-medium text-ink">{d.storeId} · {d.sku} <Pill tone={d.action === 'REPAIRED' ? 'green' : 'amber'}>{d.action.toLowerCase()}</Pill></div>
              <div className="mt-1 text-muted">Redis sellable {d.redisAvailable} vs expected {d.expectedAvailable} (ledger {d.ledgerOnHand}, reserved {d.reservedCounter}→{d.liveReserved})</div>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  )
}
