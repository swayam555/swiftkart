import { useState } from 'react'
import { Link, Navigate, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'motion/react'
import { ChevronDown, LogOut, MapPin, Minus, Plus, ShoppingBag, X } from 'lucide-react'
import clsx from 'clsx'
import { inr, LOCALITIES, post, ApiError, type Order } from '../api'
import { quickEta, useShop } from '../state'
import { Button, ErrorNote, Logo } from './ui'
import ProductArt from './ProductArt'

export default function CustomerLayout() {
  const shop = useShop()
  const [picking, setPicking] = useState(false)
  const [cartOpen, setCartOpen] = useState(false)
  if (!shop.customer) return <Navigate to="/login" replace />

  const eta = shop.nearest?.serviceable ? quickEta(shop.nearest.distanceKm) : null
  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-[1000] border-b border-line bg-cream/90 backdrop-blur-md">
        <div className="mx-auto flex h-[68px] max-w-6xl items-center gap-2 px-4 sm:gap-5 sm:px-5">
          <Link to="/" className="focus-ring shrink-0 rounded-md"><Logo className="[&>span]:hidden sm:[&>span]:inline" /></Link>
          <button onClick={() => setPicking(true)} className="focus-ring group min-w-0 rounded-xl px-3 py-1.5 text-left transition hover:bg-ink/[0.04]">
            <div className="display truncate whitespace-nowrap text-[15px] font-bold leading-tight text-ink">
              {eta ? `Delivery in ${eta} minutes` : shop.nearest === null || shop.nearest?.serviceable === false ? 'Not serviceable here yet' : 'Choose your location'}
            </div>
            <div className="flex items-center gap-1 truncate text-xs text-muted"><MapPin className="h-3 w-3 shrink-0" />{shop.place?.name ?? 'Set a delivery location'}<ChevronDown className="h-3 w-3 shrink-0" /></div>
          </button>
          <div className="flex-1" />
          <nav className="hidden items-center gap-1 sm:flex">
            <NavLink to="/shop" end className={({ isActive }) => clsx('rounded-lg px-3 py-2 text-sm font-medium', isActive ? 'text-ink' : 'text-muted hover:text-ink')}>Shop</NavLink>
            <NavLink to="/shop/orders" className={({ isActive }) => clsx('rounded-lg px-3 py-2 text-sm font-medium', isActive ? 'text-ink' : 'text-muted hover:text-ink')}>Orders</NavLink>
          </nav>
          <button onClick={() => setCartOpen(true)}
            className="focus-ring relative flex h-11 items-center gap-2.5 rounded-xl bg-brand pl-3.5 pr-4 text-sm font-semibold text-white transition hover:bg-brand-dark">
            <ShoppingBag className="h-[18px] w-[18px]" />
            {shop.cartCount > 0
              ? <span className="text-left leading-tight"><span className="block text-[11px] font-medium text-white/75">{shop.cartCount} {shop.cartCount === 1 ? 'item' : 'items'}</span><span className="num">{inr(shop.cartTotal)}</span></span>
              : <span className="hidden sm:inline">Cart</span>}
          </button>
          <button title="Sign out" onClick={() => shop.setCustomer(null)} className="focus-ring rounded-lg p-2 text-muted hover:bg-ink/5 hover:text-ink"><LogOut className="h-4 w-4" /></button>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-5 py-7"><Outlet context={{ openPicker: () => setPicking(true) }} /></main>
      <AnimatePresence>{picking && <LocationPicker onClose={() => setPicking(false)} />}</AnimatePresence>
      <AnimatePresence>{cartOpen && <CartDrawer onClose={() => setCartOpen(false)} />}</AnimatePresence>
    </div>
  )
}

function LocationPicker({ onClose }: { onClose: () => void }) {
  const { setPlace, place } = useShop()
  const cities = [...new Set(LOCALITIES.map(l => l.name.split(', ')[1]))]
  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
      className="fixed inset-0 z-[2000] flex items-center justify-center bg-ink/40 p-4 backdrop-blur-sm" onClick={onClose}>
      <motion.div initial={{ opacity: 0, y: 16, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: 8 }}
        className="w-full max-w-md rounded-[22px] bg-surface p-6 shadow-[var(--shadow-pop)]" onClick={e => e.stopPropagation()} role="dialog" aria-label="Choose delivery location">
        <div className="flex items-start justify-between">
          <div>
            <h2 className="display text-2xl font-bold tracking-tight text-ink">Where should we deliver?</h2>
            <p className="mt-1 text-sm text-muted">Dark stores serve about 3 km around them.</p>
          </div>
          <button onClick={onClose} className="focus-ring rounded-lg p-1.5 text-muted hover:bg-ink/5" aria-label="Close"><X className="h-5 w-5" /></button>
        </div>
        <div className="mt-5 space-y-4">
          {cities.map(city => (
            <div key={city}>
              <div className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted">{city}</div>
              <div className="grid gap-2">
                {LOCALITIES.filter(l => l.name.endsWith(city)).map(l => (
                  <button key={l.name} onClick={() => { setPlace(l); onClose() }}
                    className={clsx('focus-ring flex items-center gap-3 rounded-xl border px-3.5 py-3 text-left text-sm transition',
                      place?.name === l.name ? 'border-brand bg-brand-soft' : 'border-line hover:border-brand/50 hover:bg-cream')}>
                    <MapPin className="h-4 w-4 text-brand" /><span className="font-medium text-ink">{l.name.split(', ')[0]}</span>
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      </motion.div>
    </motion.div>
  )
}

function CartDrawer({ onClose }: { onClose: () => void }) {
  const shop = useShop()
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const lines = Object.values(shop.cart)
  const fee = shop.cartTotal >= 199 || shop.cartTotal === 0 ? 0 : 25

  const checkout = async () => {
    if (!shop.place) return
    setBusy(true); setError(null)
    try {
      const order = await post<Order>('/api/orders', {
        lat: shop.place.lat, lon: shop.place.lon, address: shop.place.name,
        items: lines.map(l => ({ sku: l.product.sku, qty: l.qty })),
      }, { headers: { 'Idempotency-Key': crypto.randomUUID() } })
      shop.clearCart()
      onClose()
      navigate(`/shop/orders/${order.id}`)
    } catch (e) {
      setError(e instanceof ApiError && e.status === 409 ? `${e.message}. Please remove it and try again.` : (e as Error).message)
    } finally { setBusy(false) }
  }

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="fixed inset-0 z-[2000] flex justify-end bg-ink/40 backdrop-blur-sm" onClick={onClose}>
      <motion.aside initial={{ x: '100%' }} animate={{ x: 0 }} exit={{ x: '100%' }} transition={{ type: 'spring', stiffness: 380, damping: 38 }}
        className="flex h-full w-full max-w-[420px] flex-col bg-cream shadow-2xl" onClick={e => e.stopPropagation()} role="dialog" aria-label="Cart">
        <div className="flex items-center justify-between border-b border-line bg-surface px-5 py-4">
          <h2 className="display text-xl font-bold text-ink">Your cart</h2>
          <button onClick={onClose} className="focus-ring rounded-lg p-1.5 text-muted hover:bg-ink/5" aria-label="Close cart"><X className="h-5 w-5" /></button>
        </div>
        <div className="flex-1 space-y-2.5 overflow-auto p-5">
          {lines.length === 0 && (
            <div className="flex flex-col items-center py-16 text-center">
              <ShoppingBag className="h-10 w-10 text-line-2" strokeWidth={1.5} />
              <div className="mt-3 font-semibold text-ink">Your cart is empty</div>
              <div className="mt-1 text-sm text-muted">Add a few things and they'll be at your door in minutes.</div>
            </div>
          )}
          <AnimatePresence initial={false}>
            {lines.map(l => (
              <motion.div key={l.product.sku} layout initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, height: 0 }}
                className="flex items-center gap-3 rounded-2xl border border-line bg-surface p-3">
                <ProductArt sku={l.product.sku} size="sm" />
                <div className="min-w-0 flex-1"><div className="truncate text-sm font-medium text-ink">{l.product.name}</div>
                  <div className="text-xs text-muted">{l.product.unit} · {inr(l.product.price)}</div></div>
                <Stepper qty={l.qty} onChange={q => shop.setQty(l.product, q)} max={Math.min(l.product.maxPerOrder, l.product.available)} />
              </motion.div>
            ))}
          </AnimatePresence>
        </div>
        {lines.length > 0 && (
          <div className="space-y-2.5 border-t border-line bg-surface p-5 text-sm">
            <div className="flex justify-between text-ink-2"><span>Items</span><span className="num">{inr(shop.cartTotal)}</span></div>
            <div className="flex justify-between text-ink-2"><span>Delivery</span><span>{fee === 0 ? <span className="font-medium text-brand">Free</span> : <span className="num">{inr(fee)}</span>}</span></div>
            {fee > 0 && (
              <div>
                <div className="h-1.5 overflow-hidden rounded-full bg-line"><div className="h-full rounded-full bg-sun" style={{ width: `${Math.min(100, shop.cartTotal / 1.99)}%` }} /></div>
                <div className="mt-1.5 text-xs text-muted">Add {inr(199 - shop.cartTotal)} more for free delivery</div>
              </div>
            )}
            <div className="flex justify-between border-t border-line pt-3 text-base font-semibold text-ink"><span>To pay</span><span className="num">{inr(shop.cartTotal + fee)}</span></div>
            <ErrorNote error={error} />
            <Button onClick={checkout} size="lg" disabled={busy || !shop.nearest?.serviceable} className="w-full">{busy ? 'Reserving your items…' : 'Proceed to pay'}</Button>
            <p className="text-center text-xs text-muted">Your items are held for 2 minutes while you pay.</p>
          </div>
        )}
      </motion.aside>
    </motion.div>
  )
}

export function Stepper({ qty, onChange, max }: { qty: number; onChange: (q: number) => void; max: number }) {
  return (
    <div className="flex h-9 items-center overflow-hidden rounded-xl bg-brand text-sm font-semibold text-white">
      <button className="flex h-full w-8 items-center justify-center hover:bg-brand-dark" onClick={() => onChange(qty - 1)} aria-label="Remove one"><Minus className="h-3.5 w-3.5" /></button>
      <span className="num w-6 text-center">{qty}</span>
      <button className="flex h-full w-8 items-center justify-center hover:bg-brand-dark disabled:opacity-40" disabled={qty >= max} onClick={() => onChange(qty + 1)} aria-label="Add one"><Plus className="h-3.5 w-3.5" /></button>
    </div>
  )
}
