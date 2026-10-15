import { useState, type FormEvent } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { motion } from 'motion/react'
import { ArrowLeft, ArrowRight, Clock3, ShieldCheck, Store } from 'lucide-react'
import { post, type Session } from '../api'
import { useShop } from '../state'
import { Button, ErrorNote, Logo } from '../components/ui'

export default function Login() {
  const shop = useShop()
  const [name, setName] = useState('Swayam')
  const [phone, setPhone] = useState('9876543210')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  if (shop.customer) return <Navigate to="/shop" replace />

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true); setError(null)
    try { shop.setCustomer(await post<Session>('/api/auth/customer', { name, phone })) }
    catch (err) { setError((err as Error).message) }
    finally { setBusy(false) }
  }

  return (
    <div className="grid min-h-screen lg:grid-cols-[1fr_1fr]">
      <aside className="relative hidden flex-col justify-between overflow-hidden bg-brand p-12 text-white lg:flex">
        <div className="pointer-events-none absolute -bottom-32 -right-32 h-[420px] w-[420px] rounded-full border-[40px] border-sun/20" />
        <Link to="/" className="relative w-fit"><Logo dark /></Link>
        <div className="relative">
          <h2 className="display max-w-md text-[52px] font-bold leading-[0.98] tracking-[-0.04em]">Your groceries are about ten minutes away.</h2>
          <ul className="mt-10 space-y-4 text-[15px] text-white/80">
            <li className="flex gap-3"><Store className="h-5 w-5 shrink-0 text-sun" /> Only what your nearest dark store has right now</li>
            <li className="flex gap-3"><ShieldCheck className="h-5 w-5 shrink-0 text-sun" /> Items held for you the moment you check out</li>
            <li className="flex gap-3"><Clock3 className="h-5 w-5 shrink-0 text-sun" /> A delivery promise that counts the real queue</li>
          </ul>
        </div>
        <p className="relative text-sm text-white/55">Demo system · payments are simulated · the delivery clock runs 6× faster</p>
      </aside>
      <main className="flex flex-col px-6 py-8 sm:px-12">
        <div className="flex items-center justify-between">
          <Link to="/" className="inline-flex items-center gap-1.5 text-sm text-muted hover:text-ink"><ArrowLeft className="h-4 w-4" /> Back to overview</Link>
          <Link to="/ops" className="text-sm text-muted hover:text-ink">Ops console →</Link>
        </div>
        <motion.form onSubmit={submit} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="m-auto w-full max-w-[380px] py-12">
          <div className="lg:hidden"><Logo /></div>
          <h1 className="display mt-8 text-[32px] font-bold tracking-[-0.03em] text-ink lg:mt-0">Log in or sign up</h1>
          <p className="mt-1.5 text-[15px] text-muted">No password, no OTP in the demo.</p>
          <div className="mt-8 space-y-4">
            <label className="label">Your name<input className="input" value={name} onChange={e => setName(e.target.value)} autoComplete="name" /></label>
            <label className="label">Mobile number
              <div className="mt-1.5 flex rounded-xl border border-line-2 bg-surface focus-within:border-brand focus-within:ring-4 focus-within:ring-brand/10">
                <span className="flex items-center border-r border-line px-3.5 text-sm text-muted">+91</span>
                <input className="num w-full bg-transparent px-3.5 py-2.5 text-sm outline-none" inputMode="numeric" autoComplete="tel-national"
                  value={phone} onChange={e => setPhone(e.target.value.replace(/\D/g, '').slice(0, 10))} />
              </div>
            </label>
            <ErrorNote error={error} />
            <Button type="submit" size="lg" disabled={busy || !name.trim() || phone.length !== 10} className="w-full">
              {busy ? 'Signing in…' : <>Continue <ArrowRight className="h-4 w-4" /></>}
            </Button>
          </div>
        </motion.form>
      </main>
    </div>
  )
}
