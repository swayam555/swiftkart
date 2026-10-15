import { useEffect, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { motion } from 'motion/react'
import { MapPin, Store, Zap } from 'lucide-react'
import clsx from 'clsx'
import { api, inr, type Catalog, type Product } from '../api'
import { quickEta, useShop } from '../state'
import { Button, Skeleton } from '../components/ui'
import { Stepper } from '../components/CustomerLayout'
import ProductArt from '../components/ProductArt'

export default function Shop() {
  const shop = useShop()
  const { openPicker } = useOutletContext<{ openPicker: () => void }>()
  const [catalog, setCatalog] = useState<Catalog | null>(null)
  const [category, setCategory] = useState<string | null>(null)
  const storeId = shop.nearest?.serviceable ? shop.nearest.store.id : null

  useEffect(() => {
    if (!storeId) return
    const load = () => api<Catalog>(`/api/catalog?storeId=${storeId}`).then(setCatalog).catch(() => {})
    load()
    const t = setInterval(load, 10_000) // availability changes as other customers buy
    return () => clearInterval(t)
  }, [storeId])

  if (!shop.place) {
    return (
      <EmptyState icon={<MapPin className="h-6 w-6 text-brand" />} title="Where are you?"
        body="We'll find the nearest dark store and show what's on its shelves right now." action={<Button onClick={openPicker}>Choose location</Button>} />
    )
  }
  if (shop.nearest === undefined) return <div className="grid gap-4 sm:grid-cols-3 lg:grid-cols-5">{Array.from({ length: 10 }, (_, i) => <Skeleton key={i} className="h-60" />)}</div>
  if (!shop.nearest || !shop.nearest.serviceable) {
    return <EmptyState icon={<Store className="h-6 w-6 text-brand" />} title="We're not in your area yet"
      body="Try one of our Bengaluru, Mumbai or Delhi NCR localities." action={<Button onClick={openPicker}>Change location</Button>} />
  }

  const categories = catalog ? Object.keys(catalog).sort((a, b) => (a === 'Flash Deals' ? -1 : b === 'Flash Deals' ? 1 : a.localeCompare(b))) : []
  const flash = catalog?.['Flash Deals']?.[0]
  const shownCats = (category ? [category] : categories).filter(c => category || c !== 'Flash Deals')
  return (
    <div>
      <section className="grid gap-4 lg:grid-cols-[1.5fr_1fr]">
        <div className="relative overflow-hidden rounded-[22px] bg-brand p-7 text-white">
          <div className="pointer-events-none absolute -right-10 -top-10 h-48 w-48 rounded-full border-[18px] border-sun/25" />
          <div className="text-sm text-white/70">From {shop.nearest.store.name} · {shop.nearest.distanceKm} km away</div>
          <h1 className="display mt-2 text-[40px] font-bold leading-none tracking-[-0.03em]">{quickEta(shop.nearest.distanceKm)} minute delivery</h1>
          <p className="mt-3 max-w-md text-[15px] text-white/75">Stock on this page is live. When you check out, your items are held for you before you pay.</p>
        </div>
        {flash && (
          <div className="flex items-center gap-5 rounded-[22px] bg-ink p-6 text-white">
            <ProductArt sku={flash.sku} size="lg" />
            <div className="min-w-0">
              <div className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-sun"><Zap className="h-3.5 w-3.5" /> Flash deal</div>
              <div className="mt-1 truncate font-semibold">{flash.name.replace(' (Flash Deal)', '')}</div>
              <div className="num mt-1 text-lg font-semibold">{inr(flash.price)} <s className="text-sm font-normal text-white/45">{inr(flash.mrp)}</s></div>
              <div className={clsx('mt-1 text-xs', flash.available > 0 ? 'text-sun' : 'text-white/55')}>{flash.available > 0 ? `${flash.available} left` : 'Sold out'}</div>
            </div>
          </div>
        )}
      </section>

      <div className="sticky top-[68px] z-[900] -mx-5 mt-6 border-b border-line bg-cream/90 px-5 py-3 backdrop-blur-md">
        <div className="flex gap-2 overflow-x-auto">
          <Chip active={!category} onClick={() => setCategory(null)}>All</Chip>
          {categories.map(c => <Chip key={c} active={category === c} onClick={() => setCategory(c)}>{c}</Chip>)}
        </div>
      </div>

      {!catalog && <div className="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">{Array.from({ length: 10 }, (_, i) => <Skeleton key={i} className="h-64" />)}</div>}
      {catalog && shownCats.map(cat => (
        <section key={cat} className="mt-8">
          <div className="mb-4 flex items-baseline justify-between">
            <h2 className="display text-2xl font-bold tracking-tight text-ink">{cat}</h2>
            <span className="text-sm text-muted">{catalog[cat].length} items</span>
          </div>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
            {catalog[cat].map((p, i) => <ProductCard key={p.sku} p={p} index={i} />)}
          </div>
        </section>
      ))}
    </div>
  )
}

function EmptyState({ icon, title, body, action }: { icon: React.ReactNode; title: string; body: string; action: React.ReactNode }) {
  return (
    <div className="mx-auto flex max-w-md flex-col items-center py-24 text-center">
      <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-brand-soft">{icon}</div>
      <h1 className="display mt-5 text-3xl font-bold tracking-tight text-ink">{title}</h1>
      <p className="mt-2 text-[15px] text-muted">{body}</p>
      <div className="mt-6">{action}</div>
    </div>
  )
}

function Chip({ children, active, onClick }: { children: React.ReactNode; active: boolean; onClick: () => void }) {
  return <button onClick={onClick} className={clsx('focus-ring whitespace-nowrap rounded-full px-3.5 py-1.5 text-sm font-medium transition',
    active ? 'bg-ink text-white' : 'bg-surface text-ink-2 ring-1 ring-inset ring-line hover:ring-line-2')}>{children}</button>
}

function ProductCard({ p, index }: { p: Product; index: number }) {
  const shop = useShop()
  const inCart = shop.cart[p.sku]?.qty ?? 0
  const off = Math.round((1 - p.price / p.mrp) * 100)
  const soldOut = p.available === 0
  return (
    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: Math.min(index, 10) * 0.03 }}
      className={clsx('group flex flex-col rounded-2xl border border-line bg-surface p-3 transition hover:border-line-2 hover:shadow-[var(--shadow-pop)]', soldOut && 'opacity-60')}>
      <div className="relative">
        <ProductArt sku={p.sku} />
        {off > 0 && <span className="absolute left-2 top-2 rounded-md bg-surface/90 px-1.5 py-0.5 text-[11px] font-semibold text-brand">{off}% off</span>}
      </div>
      <div className="mt-3 line-clamp-2 min-h-10 text-sm font-medium leading-snug text-ink">{p.name}</div>
      <div className="mt-0.5 flex items-center gap-1.5 text-xs text-muted">
        <span>{p.unit}</span>
        {p.lowStock && !soldOut && <><span>·</span><span className="font-medium text-[#a96800]">only {p.available} left</span></>}
      </div>
      <div className="mt-auto flex items-end justify-between pt-3">
        <div className="leading-tight">
          <div className="num text-[15px] font-semibold text-ink">{inr(p.price)}</div>
          {p.mrp > p.price && <s className="num text-xs text-muted">{inr(p.mrp)}</s>}
        </div>
        {soldOut ? <span className="pb-1 text-xs font-medium text-muted">Sold out</span>
          : inCart === 0
            ? <button onClick={() => shop.setQty(p, 1)} className="focus-ring h-9 rounded-xl border border-brand/40 px-4 text-sm font-semibold text-brand transition hover:bg-brand hover:text-white">Add</button>
            : <Stepper qty={inCart} onChange={q => shop.setQty(p, q)} max={Math.min(p.maxPerOrder, p.available)} />}
      </div>
    </motion.div>
  )
}
