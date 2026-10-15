import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'motion/react'
import { ChevronRight, Package } from 'lucide-react'
import { api, inr, STATUS_LABEL, type Order } from '../api'
import { Pill, Skeleton, STATUS_TONE } from '../components/ui'
import ProductArt from '../components/ProductArt'

export default function Orders() {
  const [orders, setOrders] = useState<Order[] | null>(null)
  useEffect(() => { api<Order[]>('/api/orders').then(setOrders).catch(() => setOrders([])) }, [])
  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="display text-[34px] font-bold tracking-[-0.03em] text-ink">Your orders</h1>
      <p className="mt-1 text-sm text-muted">Live orders update as they're packed and delivered.</p>
      <div className="mt-6 space-y-3">
        {!orders && [0, 1, 2].map(i => <Skeleton key={i} className="h-24" />)}
        {orders?.length === 0 && (
          <div className="flex flex-col items-center rounded-2xl border border-dashed border-line-2 py-16 text-center">
            <Package className="h-9 w-9 text-line-2" strokeWidth={1.5} />
            <div className="mt-3 font-semibold text-ink">No orders yet</div>
            <Link to="/shop" className="mt-4 text-sm font-semibold text-brand">Start shopping →</Link>
          </div>
        )}
        {orders?.map((o, i) => (
          <motion.div key={o.id} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.04 }}>
            <Link to={`/shop/orders/${o.id}`} className="group flex items-center gap-4 rounded-2xl border border-line bg-surface p-4 transition hover:border-line-2 hover:shadow-[var(--shadow-pop)]">
              <div className="flex -space-x-3">
                {o.lines.slice(0, 3).map(l => <ProductArt key={l.sku} sku={l.sku} size="sm" className="ring-2 ring-surface" />)}
              </div>
              <div className="min-w-0 flex-1">
                <div className="truncate font-medium text-ink">{o.lines.map(l => l.name).slice(0, 3).join(', ')}{o.lines.length > 3 && '…'}</div>
                <div className="mt-0.5 text-xs text-muted">{new Date(o.placedAt).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })} · {o.itemCount} {o.itemCount === 1 ? 'item' : 'items'} · <span className="num">{inr(o.total)}</span></div>
              </div>
              <Pill tone={STATUS_TONE[o.status]} dot>{o.status === 'DELIVERED' ? `Delivered in ${o.deliveredInMinutes} min` : STATUS_LABEL[o.status]}</Pill>
              <ChevronRight className="h-4 w-4 text-muted transition group-hover:translate-x-0.5" />
            </Link>
          </motion.div>
        ))}
      </div>
    </div>
  )
}
