import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { AnimatePresence, motion } from 'motion/react'
import { Bike, Check, ChevronLeft, Clock3, House, Package, ShieldCheck, Store as StoreIcon, X } from 'lucide-react'
import clsx from 'clsx'
import { api, inr, post, sessions, STATUS_LABEL, type Order, type Track } from '../api'
import MapView, { type Pin } from '../components/MapView'
import ProductArt from '../components/ProductArt'
import { Button, Card, ErrorNote, Pill, Skeleton, STATUS_TONE } from '../components/ui'

const STEPS = [
  { label: 'Confirmed', icon: Check },
  { label: 'Packing', icon: Package },
  { label: 'On the way', icon: Bike },
  { label: 'Delivered', icon: House },
]
const RANK: Record<string, number> = { PAYMENT_PENDING: 0, CONFIRMED: 1, PACKING: 2, PACKED: 2, OUT_FOR_DELIVERY: 3, DELIVERED: 4 }

/** Live order tracking: status over Server-Sent Events, rider position polled from dispatch. */
export default function OrderPage({ ops = false }: { ops?: boolean }) {
  const { id } = useParams()
  const [order, setOrder] = useState<Order | null>(null)
  const [track, setTrack] = useState<Track | null>(null)
  const [error, setError] = useState<string | null>(null)
  const role = ops ? 'OPS' : 'CUSTOMER'

  useEffect(() => {
    api<Order>(`/api/orders/${id}`, { role }).then(setOrder).catch(e => setError(e.message))
    const token = sessions.get(role)?.accessToken ?? ''
    const es = new EventSource(`/api/orders/${id}/stream?access_token=${encodeURIComponent(token)}`)
    es.addEventListener('order', e => setOrder(JSON.parse((e as MessageEvent).data)))
    return () => es.close()
  }, [id, role])

  const active = order && !['DELIVERED', 'CANCELLED', 'PAYMENT_FAILED', 'EXPIRED', 'PAYMENT_PENDING'].includes(order.status)
  useEffect(() => {
    if (!active) return
    const load = () => api<Track>(`/api/dispatch/orders/${id}/track`, { role }).then(setTrack).catch(() => {})
    load()
    const t = setInterval(load, 1000)
    return () => clearInterval(t)
  }, [id, active, role])

  if (error) return <ErrorNote error={error} />
  if (!order) return <div className="mx-auto max-w-5xl space-y-4"><Skeleton className="h-36" /><Skeleton className="h-80" /></div>
  if (order.status === 'PAYMENT_PENDING' && !ops) return <PayPanel order={order} onUpdate={setOrder} />

  const pins: Pin[] = [{ id: 'home', lat: order.customerLat, lon: order.customerLon, kind: 'home', label: 'You', pulse: order.status !== 'DELIVERED' }]
  if (track?.store) pins.push({ id: 'store', lat: track.store.lat, lon: track.store.lon, kind: 'store', label: track.store.name })
  if (track?.rider) pins.push({ id: 'rider', lat: track.rider.lat, lon: track.rider.lon, kind: 'rider', label: track.rider.name })
  const rank = RANK[order.status] ?? -1
  const failed = ['CANCELLED', 'PAYMENT_FAILED', 'EXPIRED'].includes(order.status)
  const cancellable = ['CONFIRMED', 'PACKING', 'PACKED'].includes(order.status) && !ops
  const eta = order.minutesRemaining != null ? Math.max(1, Math.ceil(track?.etaMinutes ?? order.minutesRemaining)) : null

  return (
    <div className="mx-auto max-w-5xl">
      <Link to={ops ? '/ops' : '/shop/orders'} className="inline-flex items-center gap-1 text-sm text-muted hover:text-ink"><ChevronLeft className="h-4 w-4" /> {ops ? 'Control room' : 'Your orders'}</Link>

      <section className={clsx('mt-3 rounded-[22px] p-6 sm:p-7', failed ? 'bg-surface ring-1 ring-line' : 'bg-brand text-white')}>
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <div className={clsx('text-sm', failed ? 'text-muted' : 'text-white/70')}>Order {order.id}</div>
            <AnimatePresence mode="wait">
              <motion.h1 key={order.status} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }}
                className={clsx('display mt-1 text-[34px] font-bold leading-tight tracking-[-0.03em]', failed && 'text-ink')}>
                {order.status === 'DELIVERED' ? `Delivered in ${order.deliveredInMinutes} minutes`
                  : failed ? STATUS_LABEL[order.status] : eta != null ? `Arriving in ${eta} min` : STATUS_LABEL[order.status]}
              </motion.h1>
            </AnimatePresence>
            <div className={clsx('mt-1 text-sm', failed ? 'text-muted' : 'text-white/75')}>
              {order.status === 'DELIVERED' ? (order.onTime ? `On time · promised ${order.promisedMinutes} min` : `Promised ${order.promisedMinutes} min`)
                : failed ? order.cancelReason : `Promised in ${order.promisedMinutes} min · ${STATUS_LABEL[order.status]}`}
            </div>
          </div>
          <span className={clsx('inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium', failed ? 'bg-ink/5 text-ink-2' : 'bg-white/15 text-white')}>
            <Clock3 className="h-3.5 w-3.5" /> Demo clock runs 6× faster
          </span>
        </div>
        {!failed && (
          <div className="mt-6 grid grid-cols-4 gap-2">
            {STEPS.map((s, i) => {
              const done = rank >= i + 1
              return (
                <div key={s.label}>
                  <div className="h-1.5 overflow-hidden rounded-full bg-white/20">
                    <motion.div className="h-full rounded-full bg-sun" initial={false} animate={{ width: done ? '100%' : '0%' }} transition={{ duration: 0.6 }} />
                  </div>
                  <div className={clsx('mt-2 flex items-center gap-1.5 text-xs font-medium', done ? 'text-white' : 'text-white/55')}><s.icon className="h-3.5 w-3.5" />{s.label}</div>
                </div>
              )
            })}
          </div>
        )}
      </section>

      <div className="mt-5 grid gap-5 md:grid-cols-[1.4fr_1fr]">
        <div className="space-y-5">
          <Card padded={false} className="overflow-hidden">
            <MapView pins={pins} fitKey={`${order.id}-${!!track?.store}-${!!track?.rider}`} height={360} className="rounded-t-[var(--radius-card)]"
              lines={track?.rider ? [[[track.rider.lat, track.rider.lon], track.status === 'PICKED_UP' ? [order.customerLat, order.customerLon] : [track.store!.lat, track.store!.lon]]] : []} />
            <div className="flex items-center gap-3 px-5 py-4 text-sm">
              {track?.rider ? (
                <>
                  <span className="flex h-10 w-10 items-center justify-center rounded-full bg-ink text-white"><Bike className="h-5 w-5" /></span>
                  <div><div className="font-semibold text-ink">{track.rider.name}</div>
                    <div className="text-xs text-muted">{track.rider.status === 'AT_STORE' ? 'Waiting at the store for your order'
                      : track.rider.status === 'TO_STORE' ? 'Heading to the store' : track.rider.status === 'DELIVERING' ? 'On the way to you' : track.rider.status.replace('_', ' ').toLowerCase()}
                      {track.rider.carrying > 1 && ' · carrying 2 orders (batched)'}</div></div>
                </>
              ) : (
                <><span className="flex h-10 w-10 items-center justify-center rounded-full bg-brand-soft text-brand"><StoreIcon className="h-5 w-5" /></span>
                  <div className="text-muted">{order.status === 'DELIVERED' ? 'Delivered. Enjoy!' : failed ? 'No rider was needed.' : 'Assigning the best rider…'}</div></>
              )}
            </div>
          </Card>
          <Card title="Timeline">
            <ol className="relative space-y-4">
              <span className="absolute bottom-2 left-[5px] top-2 w-px bg-line-2" />
              {order.timeline.map((t, i) => (
                <motion.li key={i} initial={{ opacity: 0, x: -6 }} animate={{ opacity: 1, x: 0 }} className="relative flex gap-4 pl-6 text-sm">
                  <span className={clsx('absolute left-0 top-1.5 h-[11px] w-[11px] rounded-full border-2 border-surface', i === order.timeline.length - 1 ? 'bg-brand' : 'bg-line-2')} />
                  <div className="flex-1"><div className="font-medium text-ink">{t.label}</div>{t.detail && <div className="text-xs text-muted">{t.detail}</div>}</div>
                  <div className="num text-xs text-muted">{new Date(t.at).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}</div>
                </motion.li>
              ))}
            </ol>
          </Card>
        </div>
        <div className="space-y-5">
          <Card title="Items" action={<Pill tone={STATUS_TONE[order.status]} dot>{STATUS_LABEL[order.status]}</Pill>}>
            <ul className="space-y-3">
              {order.lines.map(l => (
                <li key={l.sku} className="flex items-center gap-3 text-sm">
                  <ProductArt sku={l.sku} size="sm" />
                  <span className="min-w-0 flex-1 truncate text-ink"><span className="num text-muted">{l.qty} ×</span> {l.name}</span>
                  <span className="num font-medium text-ink">{inr(l.qty * l.unitPrice)}</span>
                </li>
              ))}
            </ul>
            <div className="mt-4 space-y-1.5 border-t border-line pt-4 text-sm">
              <div className="flex justify-between text-ink-2"><span>Delivery</span><span>{order.deliveryFee === 0 ? 'Free' : inr(order.deliveryFee)}</span></div>
              <div className="flex justify-between font-semibold text-ink"><span>{order.paidAt ? 'Total paid' : 'Total'}</span><span className="num">{inr(order.total)}</span></div>
            </div>
          </Card>
          {cancellable && <CancelButton id={order.id} onDone={setOrder} />}
        </div>
      </div>
    </div>
  )
}

function CancelButton({ id, onDone }: { id: string; onDone: (o: Order) => void }) {
  const [error, setError] = useState<string | null>(null)
  return (
    <div>
      <Button variant="danger" className="w-full" onClick={() => post<Order>(`/api/orders/${id}/cancel`).then(onDone).catch(e => setError(e.message))}><X className="h-4 w-4" /> Cancel order</Button>
      <p className="mt-2 text-center text-xs text-muted">Free cancellation until the rider picks it up</p>
      <ErrorNote error={error} />
    </div>
  )
}

function PayPanel({ order, onUpdate }: { order: Order; onUpdate: (o: Order) => void }) {
  const [fail, setFail] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [left, setLeft] = useState(0)
  const [app, setApp] = useState('PhonePe')
  const timer = useRef<number>(0)
  useEffect(() => {
    const tick = () => setLeft(Math.max(0, Math.round((new Date(order.reservationExpiresAt ?? 0).getTime() - Date.now()) / 1000)))
    tick()
    timer.current = window.setInterval(tick, 1000)
    return () => clearInterval(timer.current)
  }, [order.reservationExpiresAt])

  const pay = async () => {
    setBusy(true); setError(null)
    try { onUpdate(await post<Order>(`/api/orders/${order.id}/pay`, { method: 'UPI', simulateFailure: fail })) }
    catch (e) { setError((e as Error).message) } finally { setBusy(false) }
  }
  const total = 120
  const pct = Math.min(1, left / total)
  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="mx-auto max-w-md">
      <div className="rounded-[22px] border border-line bg-surface p-7 shadow-[var(--shadow-pop)]">
        <div className="flex items-center justify-between">
          <h1 className="display text-2xl font-bold tracking-tight text-ink">Pay with UPI</h1>
          <div className="relative h-14 w-14">
            <svg viewBox="0 0 36 36" className="h-14 w-14 -rotate-90">
              <circle cx="18" cy="18" r="15.5" fill="none" stroke="#ebe7dd" strokeWidth="3" />
              <circle cx="18" cy="18" r="15.5" fill="none" stroke={left > 30 ? '#0b6b3a' : '#ffb020'} strokeWidth="3" strokeLinecap="round"
                strokeDasharray={`${pct * 97.4} 97.4`} className="transition-[stroke-dasharray] duration-1000 ease-linear" />
            </svg>
            <span className="num absolute inset-0 flex items-center justify-center text-xs font-semibold text-ink">{Math.floor(left / 60)}:{String(left % 60).padStart(2, '0')}</span>
          </div>
        </div>
        <div className="mt-5 rounded-2xl bg-cream p-5 text-center">
          <div className="text-sm text-muted">Amount to pay</div>
          <div className="display num mt-1 text-[44px] font-bold leading-none tracking-tight text-ink">{inr(order.total)}</div>
          <div className="mt-2 text-xs text-muted">{order.itemCount} {order.itemCount === 1 ? 'item' : 'items'} · order {order.id}</div>
        </div>
        <div className="mt-4 flex items-center gap-2 text-xs text-ink-2"><ShieldCheck className="h-4 w-4 text-brand" /> Your items are held for you until the timer runs out.</div>
        <div className="mt-5 grid grid-cols-3 gap-2 text-center text-sm">
          {['PhonePe', 'Google Pay', 'Paytm'].map(a => (
            <button key={a} onClick={() => setApp(a)} className={clsx('focus-ring rounded-xl border py-2.5 font-medium transition', app === a ? 'border-brand bg-brand-soft text-brand' : 'border-line text-ink-2 hover:border-line-2')}>{a}</button>
          ))}
        </div>
        <label className="mt-5 flex cursor-pointer items-center gap-2.5 rounded-xl border border-dashed border-line-2 px-3.5 py-2.5 text-xs text-ink-2">
          <input type="checkbox" checked={fail} onChange={e => setFail(e.target.checked)} className="accent-brand" /> Simulate a declined payment (stock goes back on the shelf)
        </label>
        <div className="mt-4"><ErrorNote error={error} /></div>
        <Button onClick={pay} size="lg" disabled={busy || left === 0} className="mt-2 w-full">
          {busy ? 'Waiting for your bank…' : left === 0 ? 'Reservation expired' : `Pay ${inr(order.total)} with ${app}`}
        </Button>
      </div>
    </motion.div>
  )
}
