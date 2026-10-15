import { useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'motion/react'
import { ArrowRight, ArrowUpRight, Bike, Check, Clock3, Copy, MapPinned, PackageCheck, Radar, ShieldCheck, Split, Store } from 'lucide-react'
import { Logo } from '../components/ui'
import RouteDemo from './RouteDemo'
import FlashSale from './FlashSale'
import Architecture from './Architecture'
import { CountUp, Reveal } from './motion'

// Where the code lives. Override at build time with VITE_REPO_URL.
const REPO: string = import.meta.env.VITE_REPO_URL ?? 'https://github.com/swayam555/swiftkart'

export default function Landing() {
  return (
    <div className="overflow-x-clip">
      <Nav />
      <Hero />
      <Proof />
      <Problems />
      <FlashSection />
      <Saga />
      <Dispatch />
      <ArchitectureSection />
      <RunIt />
      <Footer />
    </div>
  )
}

function Nav() {
  return (
    <header className="sticky top-0 z-30 border-b border-line/80 bg-cream/80 backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-5">
        <Link to="/" className="focus-ring rounded-md"><Logo /></Link>
        <nav className="hidden items-center gap-7 text-sm text-ink-2 md:flex">
          <a href="#flash" className="hover:text-ink">Flash sales</a>
          <a href="#saga" className="hover:text-ink">Order saga</a>
          <a href="#dispatch" className="hover:text-ink">Dispatch</a>
          <a href="#architecture" className="hover:text-ink">Architecture</a>
        </nav>
        <div className="flex items-center gap-2">
          <Link to="/ops" className="hidden h-9 items-center rounded-xl px-3 text-sm text-ink-2 hover:bg-ink/5 sm:inline-flex">Ops console</Link>
          <Link to="/shop" className="inline-flex h-9 items-center gap-1.5 rounded-xl bg-brand px-3.5 text-sm font-semibold text-white hover:bg-brand-dark">
            Start shopping <ArrowRight className="h-4 w-4" />
          </Link>
        </div>
      </div>
    </header>
  )
}

function Hero() {
  return (
    <section className="relative">
      <div className="pointer-events-none absolute -top-40 right-0 -z-10 h-[520px] w-[620px] rounded-full bg-sun/20 blur-3xl" />
      <div className="mx-auto grid max-w-6xl items-center gap-16 px-5 pb-24 pt-14 lg:grid-cols-[1fr_1fr] lg:pt-20">
        <div>
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
            className="inline-flex items-center gap-2 rounded-full bg-sun-soft px-3 py-1 text-xs font-medium text-[#8a5600]">
            <Clock3 className="h-3.5 w-3.5" /> A quick-commerce engine, like Blinkit or Zepto
          </motion.div>
          <motion.h1 initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.08, duration: 0.6 }}
            className="display mt-6 text-[48px] font-bold leading-[0.98] tracking-[-0.04em] text-ink sm:text-[64px] lg:text-[76px]">
            Groceries at your door in <span className="text-brand">ten minutes.</span>
          </motion.h1>
          <motion.p initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.16, duration: 0.6 }}
            className="mt-7 max-w-lg text-lg leading-relaxed text-ink-2">
            The nearest dark store, stock reserved the instant you check out, an honest delivery promise and the best
            rider assigned automatically. Then you watch them ride to your door.
          </motion.p>
          <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.24, duration: 0.6 }} className="mt-9 flex flex-wrap gap-3">
            <Link to="/shop" className="inline-flex h-12 items-center gap-2 rounded-xl bg-brand px-5 text-[15px] font-semibold text-white shadow-[var(--shadow-pop)] hover:bg-brand-dark">
              Place a live order <ArrowRight className="h-4 w-4" />
            </Link>
            <Link to="/ops" className="inline-flex h-12 items-center gap-2 rounded-xl border border-line-2 bg-surface px-5 text-[15px] font-semibold text-ink hover:bg-cream">
              Open the control room
            </Link>
          </motion.div>
          <motion.p initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.5 }} className="mt-6 text-sm text-muted">
            11 dark stores in Bengaluru, Mumbai and Delhi NCR · 88 riders · payments simulated
          </motion.p>
        </div>
        <motion.div initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2, duration: 0.8 }} className="pb-8 lg:pl-6">
          <RouteDemo />
        </motion.div>
      </div>
    </section>
  )
}

function Proof() {
  const items = [
    { v: <CountUp to={0} />, l: 'refunds when 300 people buy 50 consoles at once' },
    { v: <><CountUp to={9.2} decimals={1} /> min</>, l: 'average delivery at normal load' },
    { v: <CountUp to={23} />, l: 'tests, including real Redis and Postgres races' },
    { v: <CountUp to={4} />, l: 'Spring Boot services, one Kafka event bus' },
  ]
  return (
    <section className="border-y border-line bg-surface">
      <div className="mx-auto grid max-w-6xl grid-cols-2 gap-y-8 px-5 py-10 md:grid-cols-4">
        {items.map((it, i) => (
          <Reveal key={i} delay={i * 0.06} className="md:border-l md:border-line md:pl-6 md:first:border-0 md:first:pl-0">
            <div className="display text-4xl font-bold tracking-tight text-ink">{it.v}</div>
            <p className="mt-1.5 max-w-[16rem] text-sm leading-snug text-muted">{it.l}</p>
          </Reveal>
        ))}
      </div>
    </section>
  )
}

function SectionHead({ eyebrow, title, children, center }: { eyebrow: string; title: string; children?: React.ReactNode; center?: boolean }) {
  return (
    <Reveal className={center ? 'mx-auto max-w-2xl text-center' : 'max-w-2xl'}>
      <div className="text-sm font-semibold text-brand">{eyebrow}</div>
      <h2 className="display mt-2 text-[34px] font-bold leading-[1.05] tracking-[-0.03em] text-ink sm:text-[46px]">{title}</h2>
      {children && <p className="mt-4 text-[17px] leading-relaxed text-ink-2">{children}</p>}
    </Reveal>
  )
}

function Problems() {
  const rows = [
    { icon: ShieldCheck, problem: 'Overselling', fix: 'The whole cart is checked and reserved in one atomic Redis script. Postgres is the ledger and can never go negative.' },
    { icon: Clock3, problem: 'Broken promises', fix: 'The ETA counts picking time, the real queue ahead of you, the handover and the ride. The clock starts when you pay.' },
    { icon: Bike, problem: 'Idle or overloaded riders', fix: 'Riders are found with a GEO query, scored on time-to-store and fairness, and batched when two drops go the same way.' },
  ]
  return (
    <section className="mx-auto max-w-6xl px-5 py-24">
      <SectionHead eyebrow="The hard part" title="Three things break ten-minute delivery.">
        Quick commerce runs on thin margins and tight promises. These are the failure modes this system is built around.
      </SectionHead>
      <div className="mt-12 grid gap-5 md:grid-cols-3">
        {rows.map((r, i) => (
          <Reveal key={r.problem} delay={i * 0.08} className="group rounded-[var(--radius-card)] border border-line bg-surface p-6 transition hover:-translate-y-1 hover:shadow-[var(--shadow-pop)]">
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-soft"><r.icon className="h-5 w-5 text-brand" strokeWidth={1.8} /></div>
            <h3 className="display mt-5 text-xl font-bold tracking-tight text-ink">{r.problem}</h3>
            <p className="mt-2 text-[15px] leading-relaxed text-ink-2">{r.fix}</p>
          </Reveal>
        ))}
      </div>
    </section>
  )
}

function FlashSection() {
  return (
    <section id="flash" className="scroll-mt-16 border-y border-line bg-[#f3f0e7]">
      <div className="mx-auto max-w-6xl px-5 py-24">
        <SectionHead eyebrow="The flash-sale lab" title="300 buyers. 50 consoles. One second.">
          Every dot is a buyer running the real checkout: reserve, pay, commit. Watch who is told "reserved", and who
          is refunded after paying. Numbers are from the running system; the naive result varies run to run (174 to 300 told yes).
        </SectionHead>
        <div className="mt-12"><FlashSale /></div>
        <Reveal className="mt-6 flex flex-wrap gap-x-6 gap-y-2 text-sm text-muted">
          <span className="flex items-center gap-2"><span className="h-3 w-3 rounded-[3px] bg-[#ffb020]" /> told "reserved"</span>
          <span className="flex items-center gap-2"><span className="h-3 w-3 rounded-[3px] bg-brand" /> got a console</span>
          <span className="flex items-center gap-2"><span className="h-3 w-3 rounded-[3px] bg-danger" /> paid, then refunded</span>
          <span className="flex items-center gap-2"><span className="h-3 w-3 rounded-[3px] bg-[#d9d5c9]" /> told "sold out" before paying</span>
        </Reveal>
      </div>
    </section>
  )
}

function Saga() {
  const states = [
    { s: 'Payment pending', d: 'Stock reserved for 2 minutes' }, { s: 'Confirmed', d: 'UPI captured, ledger committed' },
    { s: 'Packing', d: 'Picker assigned' }, { s: 'Out for delivery', d: 'Rider picked it up' }, { s: 'Delivered', d: 'On time, or not' },
  ]
  return (
    <section id="saga" className="mx-auto max-w-6xl scroll-mt-16 px-5 py-24">
      <div className="grid gap-14 lg:grid-cols-[0.9fr_1.1fr] lg:items-center">
        <SectionHead eyebrow="The order saga" title="An order only ever moves forward.">
          One service owns the state machine. Events reach Kafka exactly when the database commits, late events can't
          move an order backwards, and a declined payment or an abandoned cart puts the stock straight back on the shelf.
        </SectionHead>
        <Reveal delay={0.1}>
          <ol className="relative space-y-3">
            <motion.span className="absolute bottom-6 left-[19px] top-6 w-0.5 origin-top bg-brand/25"
              initial={{ scaleY: 0 }} whileInView={{ scaleY: 1 }} viewport={{ once: true }} transition={{ duration: 1.4, ease: 'easeOut' }} />
            {states.map((x, i) => (
              <motion.li key={x.s} initial={{ opacity: 0, x: 16 }} whileInView={{ opacity: 1, x: 0 }} viewport={{ once: true }} transition={{ delay: 0.15 + i * 0.18 }}
                className="relative flex items-center gap-4 rounded-2xl border border-line bg-surface p-4">
                <span className={`relative z-10 flex h-10 w-10 shrink-0 items-center justify-center rounded-full ${i === states.length - 1 ? 'bg-brand text-white' : 'bg-brand-soft text-brand'}`}>
                  {i === states.length - 1 ? <PackageCheck className="h-5 w-5" /> : <span className="num text-sm font-semibold">{i + 1}</span>}
                </span>
                <div><div className="font-semibold text-ink">{x.s}</div><div className="text-sm text-muted">{x.d}</div></div>
              </motion.li>
            ))}
          </ol>
          <p className="mt-4 text-sm text-muted">Any state before pickup can be cancelled: refund, and the stock returns to the shelf.</p>
        </Reveal>
      </div>
    </section>
  )
}

function Dispatch() {
  const items = [
    { icon: Radar, t: 'Found with a GEO query', b: 'The same spatial-index search real dispatchers run on live GPS pings.' },
    { icon: Split, t: 'Batched when it helps', b: 'Two drops within 1.2 km with under 4 minutes of detour share one trip.' },
    { icon: MapPinned, t: 'Tracked live', b: 'Server-Sent Events fanned out over Redis pub/sub, so any replica can serve you.' },
    { icon: Store, t: 'One leader at a time', b: 'A fenced Redis lease means exactly one replica moves the fleet.' },
  ]
  return (
    <section id="dispatch" className="scroll-mt-16 bg-ink text-white">
      <div className="mx-auto max-w-6xl px-5 py-24">
        <Reveal className="max-w-2xl">
          <div className="text-sm font-semibold text-sun">Rider dispatch</div>
          <h2 className="display mt-2 text-[34px] font-bold leading-[1.05] tracking-[-0.03em] sm:text-[46px]">The right rider, not just the closest one.</h2>
          <p className="mt-4 text-[17px] leading-relaxed text-white/65">Candidates are scored on time to reach the store, minus a bonus for how long they've been waiting, so work is shared fairly.</p>
        </Reveal>
        <div className="mt-12 grid gap-px overflow-hidden rounded-[var(--radius-card)] border border-white/10 bg-white/10 sm:grid-cols-2 lg:grid-cols-4">
          {items.map((it, i) => (
            <Reveal key={it.t} delay={i * 0.07} className="bg-ink p-6">
              <it.icon className="h-5 w-5 text-sun" strokeWidth={1.8} />
              <h3 className="mt-5 font-semibold">{it.t}</h3>
              <p className="mt-1.5 text-sm leading-relaxed text-white/60">{it.b}</p>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  )
}

function ArchitectureSection() {
  return (
    <section id="architecture" className="mx-auto max-w-6xl scroll-mt-16 px-5 py-24">
      <SectionHead eyebrow="Architecture" title="Three services, one event bus, two stores of truth.">
        Redis is the fast counter and the live map; PostgreSQL is the ledger and the order history. Kafka connects
        them through transactional outboxes, and every consumer de-duplicates.
      </SectionHead>
      <Reveal delay={0.1} className="mt-12 rounded-[var(--radius-card)] border border-line bg-surface p-6"><Architecture /></Reveal>
      <Reveal delay={0.15} className="mt-8 flex flex-wrap gap-2">
        {['Java 21', 'Spring Boot 4', 'Spring Cloud Gateway', 'Kafka', 'Redis · Lua · GEO', 'PostgreSQL', 'React 19', 'Leaflet', 'Docker', 'Kubernetes'].map(t => (
          <span key={t} className="rounded-lg border border-line bg-surface px-2.5 py-1 text-xs font-medium text-ink-2">{t}</span>
        ))}
      </Reveal>
    </section>
  )
}

function RunIt() {
  const cmd = 'git clone ' + REPO + '.git\ncd swiftkart\ndocker compose up --build\n# open http://localhost:3002'
  const [copied, setCopied] = useState(false)
  const copy = () => { navigator.clipboard?.writeText(cmd).then(() => { setCopied(true); setTimeout(() => setCopied(false), 1500) }) }
  return (
    <section className="px-5 pb-24">
      <Reveal className="mx-auto grid max-w-6xl items-center gap-10 rounded-[28px] bg-brand p-8 text-white sm:p-12 lg:grid-cols-2">
        <div>
          <h2 className="display text-[34px] font-bold leading-[1.05] tracking-[-0.03em] sm:text-[42px]">Run a whole city on your laptop.</h2>
          <p className="mt-4 text-[17px] leading-relaxed text-white/75">Every service, Kafka, Redis and Postgres in one command. The demo clock runs six times faster, so a ten-minute delivery takes under two.</p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link to="/shop" className="inline-flex h-11 items-center gap-2 rounded-xl bg-sun px-5 text-sm font-semibold text-ink hover:brightness-95">Start shopping <ArrowRight className="h-4 w-4" /></Link>
            <a href={REPO} className="inline-flex h-11 items-center gap-2 rounded-xl border border-white/25 px-5 text-sm font-semibold hover:bg-white/10">View the source <ArrowUpRight className="h-4 w-4" /></a>
          </div>
        </div>
        <div className="relative rounded-2xl bg-black/25 p-5 font-mono text-[13px] leading-7 text-white/85">
          <button onClick={copy} aria-label="Copy commands" className="absolute right-3 top-3 rounded-md p-2 text-white/60 hover:bg-white/10 hover:text-white">
            {copied ? <Check className="h-4 w-4 text-sun" /> : <Copy className="h-4 w-4" />}
          </button>
          {cmd.split('\n').map(l => <div key={l} className={l.startsWith('#') ? 'text-white/45' : ''}>{l.startsWith('#') ? l : <><span className="text-sun">$</span> {l}</>}</div>)}
        </div>
      </Reveal>
    </section>
  )
}

function Footer() {
  return (
    <footer className="border-t border-line">
      <div className="mx-auto flex max-w-6xl flex-col items-start justify-between gap-4 px-5 py-8 text-sm text-muted sm:flex-row sm:items-center">
        <div className="flex items-center gap-3"><Logo /><span>A portfolio project. Payments and riders are simulated.</span></div>
        <div className="flex gap-5">
          <Link to="/shop" className="hover:text-ink">Shop</Link>
          <Link to="/ops" className="hover:text-ink">Ops console</Link>
          <a href={REPO} className="hover:text-ink">Source</a>
        </div>
      </div>
    </footer>
  )
}
