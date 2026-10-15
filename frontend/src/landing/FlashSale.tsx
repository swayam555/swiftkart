import { motion, useInView, useReducedMotion } from 'motion/react'
import { useEffect, useRef, useState } from 'react'

const BUYERS = 300
const UNITS = 50
type Phase = 0 | 1 | 2

/**
 * 300 buyers, 50 consoles, run twice. Each dot is a buyer. Naive: most are told "reserved", then
 * all but 50 are refunded. Atomic: exactly 50 are told yes; everyone else hears "sold out" before paying.
 */
export default function FlashSale() {
  const ref = useRef<HTMLDivElement>(null)
  const inView = useInView(ref, { once: true, margin: '-120px' })
  const reduce = useReducedMotion()
  const [phase, setPhase] = useState<Phase>(reduce ? 2 : 0)

  useEffect(() => {
    if (!inView || reduce) return
    const a = setTimeout(() => setPhase(1), 300)
    const b = setTimeout(() => setPhase(2), 2400)
    return () => { clearTimeout(a); clearTimeout(b) }
  }, [inView, reduce])

  return (
    <div ref={ref} className="grid gap-5 lg:grid-cols-2">
      <Panel title="Naive check-then-act" note="What most code does first" tone="bad" phase={phase} toldYes={284} />
      <Panel title="Atomic reservation in Redis Lua" note="What SwiftKart does" tone="good" phase={phase} toldYes={UNITS} />
    </div>
  )
}

function Panel({ title, note, tone, phase, toldYes }: { title: string; note: string; tone: 'good' | 'bad'; phase: Phase; toldYes: number }) {
  const refunded = toldYes - UNITS
  const colour = (i: number) => {
    if (phase === 0) return '#e3e0d6'
    if (i >= toldYes) return phase === 2 ? '#d9d5c9' : '#e3e0d6' // told "sold out" before paying
    if (phase === 1) return tone === 'bad' ? '#ffb020' : '#0b6b3a'   // told "reserved"
    return i < UNITS ? '#0b6b3a' : '#c4382a'                          // confirmed, or refunded after paying
  }
  return (
    <div className={`rounded-[var(--radius-card)] border p-6 ${tone === 'good' ? 'border-brand/30 bg-surface' : 'border-line bg-surface'}`}>
      <div className="flex items-baseline justify-between gap-3">
        <h3 className="text-[17px] font-semibold tracking-tight text-ink">{title}</h3>
        <span className="text-xs text-muted">{note}</span>
      </div>
      <div className="mt-5 grid grid-cols-[repeat(25,minmax(0,1fr))] gap-[3px]" aria-hidden>
        {Array.from({ length: BUYERS }, (_, i) => (
          <motion.span key={i} className="aspect-square rounded-[3px]" animate={{ backgroundColor: colour(i) }}
            transition={{ duration: 0.35, delay: phase === 1 ? (i % 60) * 0.012 : phase === 2 ? ((i * 7) % 50) * 0.012 : 0 }} />
        ))}
      </div>
      <dl className="mt-6 grid grid-cols-3 gap-4 border-t border-line pt-4 text-sm">
        <div><dt className="text-muted">Told “reserved”</dt><dd className={`num mt-1 text-2xl font-semibold ${tone === 'bad' ? 'text-[#a96800]' : 'text-ink'}`}>{phase ? toldYes : '…'}</dd></div>
        <div><dt className="text-muted">Got a console</dt><dd className="num mt-1 text-2xl font-semibold text-brand">{phase === 2 ? UNITS : '…'}</dd></div>
        <div><dt className="text-muted">Refunded after paying</dt><dd className={`num mt-1 text-2xl font-semibold ${refunded ? 'text-danger' : 'text-brand'}`}>{phase === 2 ? refunded : '…'}</dd></div>
      </dl>
    </div>
  )
}
