export type Role = 'CUSTOMER' | 'OPS'

export interface Session { accessToken: string; userId: string; name: string; role: Role }

export interface Store { id: string; name: string; city: string; lat: number; lon: number; radiusKm: number }
export interface Nearest { store: Store; distanceKm: number; serviceable: boolean }
export interface Product {
  sku: string; name: string; unit: string; price: number; mrp: number; emoji: string; maxPerOrder: number
  available: number; lowStock: boolean
}
export type Catalog = Record<string, Product[]>

export interface OrderLine { sku: string; name: string; qty: number; unitPrice: number }
export interface Order {
  id: string; status: string; storeId: string; customerId: string; customerName: string | null; lines: OrderLine[]
  itemCount: number; itemTotal: number; deliveryFee: number; total: number; customerLat: number; customerLon: number
  address: string | null; distanceKm: number; promisedMinutes: number; promisedBy: string; expectedBy: string | null
  minutesRemaining: number | null; minutesElapsed: number | null; deliveredInMinutes: number | null; onTime: boolean | null
  reservationExpiresAt: string | null; riderId: string | null; riderName: string | null; batchedWith: string | null
  cancelReason: string | null; source: string; placedAt: string; paidAt: string | null; deliveredAt: string | null
  timeline: { at: string; label: string; detail: string | null }[]
}

export interface Track {
  orderId: string; status: string; packed: boolean; drop: { lat: number; lon: number }
  store?: { lat: number; lon: number; name: string }; etaMinutes?: number
  rider?: { id: string; name: string; lat: number; lon: number; status: string; carrying: number }
  batchedWith: string | null
}

export interface Rider { id: string; name: string; storeId: string; status: string; lat: number; lon: number
  orders: string[]; deliveries: number; batches: number }

const KEY = 'swiftkart.session.'

export const sessions = {
  get(role: Role): Session | null {
    const raw = localStorage.getItem(KEY + role)
    return raw ? JSON.parse(raw) : null
  },
  set(s: Session) { localStorage.setItem(KEY + s.role, JSON.stringify(s)) },
  clear(role: Role) { localStorage.removeItem(KEY + role) },
}

export class ApiError extends Error {
  status: number
  body: Record<string, unknown>
  constructor(status: number, message: string, body: Record<string, unknown>) { super(message); this.status = status; this.body = body }
}

export async function api<T>(path: string, init: RequestInit & { role?: Role } = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  const session = sessions.get(init.role ?? (path.startsWith('/api/ops') || path.includes('/ops/') ? 'OPS' : 'CUSTOMER'))
  if (session) headers.set('Authorization', `Bearer ${session.accessToken}`)
  const res = await fetch(path, { ...init, headers })
  if (!res.ok) {
    let body: Record<string, unknown> = {}
    try { body = await res.json() } catch { /* not json */ }
    const message = (body.detail ?? body.error ?? res.statusText) as string
    throw new ApiError(res.status, res.status === 429 ? 'Too many requests, please slow down.' : message, body)
  }
  return res.json()
}

export const post = <T,>(path: string, body?: unknown, init: RequestInit & { role?: Role } = {}) =>
  api<T>(path, { ...init, method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) })

export const inr = (v: number) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(v)

export const STATUS_LABEL: Record<string, string> = {
  PAYMENT_PENDING: 'Awaiting payment', CONFIRMED: 'Order confirmed', PACKING: 'Packing your order', PACKED: 'Packed',
  OUT_FOR_DELIVERY: 'Out for delivery', DELIVERED: 'Delivered', CANCELLED: 'Cancelled', PAYMENT_FAILED: 'Payment failed',
  EXPIRED: 'Expired',
}

export const LOCALITIES = [
  { name: 'Koramangala, Bengaluru', lat: 12.9341, lon: 77.6101 },
  { name: 'Indiranagar, Bengaluru', lat: 12.9719, lon: 77.6412 },
  { name: 'HSR Layout, Bengaluru', lat: 12.9081, lon: 77.6476 },
  { name: 'Bandra West, Mumbai', lat: 19.0544, lon: 72.8340 },
  { name: 'Powai, Mumbai', lat: 19.1197, lon: 72.9050 },
  { name: 'Hauz Khas, Delhi', lat: 28.5535, lon: 77.1940 },
  { name: 'Cyber City, Gurugram', lat: 28.4960, lon: 77.0880 },
]
