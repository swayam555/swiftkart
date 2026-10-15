import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, sessions, type Nearest, type Product, type Session } from './api'

export interface CartLine { product: Product; qty: number }
export interface Place { name: string; lat: number; lon: number }

interface ShopState {
  customer: Session | null; setCustomer: (s: Session | null) => void
  place: Place | null; setPlace: (p: Place) => void
  nearest: Nearest | null | undefined
  cart: Record<string, CartLine>; setQty: (p: Product, qty: number) => void; clearCart: () => void
  cartCount: number; cartTotal: number
}

const Ctx = createContext<ShopState>(null!)

/** Rough promise before checkout; the exact one comes from order-service at checkout. */
export function quickEta(distanceKm: number) {
  const seconds = 60 + 36 + 60 + distanceKm * 1.4 / 20 * 3600
  return Math.ceil(seconds * 1.1 / 60)
}

export function ShopProvider({ children }: { children: ReactNode }) {
  const [customer, setCustomerState] = useState<Session | null>(sessions.get('CUSTOMER'))
  const [place, setPlaceState] = useState<Place | null>(() => {
    const raw = localStorage.getItem('swiftkart.place')
    return raw ? JSON.parse(raw) : null
  })
  const [nearest, setNearest] = useState<Nearest | null | undefined>(undefined)
  const [cart, setCart] = useState<Record<string, CartLine>>({})

  useEffect(() => {
    if (!place) return
    setNearest(undefined)
    api<Nearest>(`/api/stores/nearest?lat=${place.lat}&lon=${place.lon}`).then(setNearest).catch(() => setNearest(null))
  }, [place])

  const value = useMemo<ShopState>(() => ({
    customer,
    setCustomer: s => { if (s) sessions.set(s); else sessions.clear('CUSTOMER'); setCustomerState(s) },
    place,
    setPlace: p => { localStorage.setItem('swiftkart.place', JSON.stringify(p)); setPlaceState(p); setCart({}) },
    nearest,
    cart,
    setQty: (product, qty) => setCart(c => {
      const next = { ...c }
      if (qty <= 0) delete next[product.sku]
      else next[product.sku] = { product, qty: Math.min(qty, product.maxPerOrder, product.available) }
      return next
    }),
    clearCart: () => setCart({}),
    cartCount: Object.values(cart).reduce((n, l) => n + l.qty, 0),
    cartTotal: Object.values(cart).reduce((n, l) => n + l.qty * l.product.price, 0),
  }), [customer, place, nearest, cart])

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export const useShop = () => useContext(Ctx)
