import { animate, AnimatePresence, motion, useReducedMotion } from 'motion/react'
import { Check, Package, Timer } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'

// One delivery, played on loop: confirmed → packing → on the way → delivered, against the 10-minute clock.
const ROUTE = 'M 92 300 L 92 236 Q 92 220 108 220 L 250 220 Q 266 220 266 204 L 266 132 Q 266 116 282 116 L 404 116'
const STEPS = ['Confirmed', 'Packed', 'On the way', 'Delivered'] as const

export default function RouteDemo() {
  const reduce = useReducedMotion()
  const path = useRef<SVGPathElement>(null)
  const [step, setStep] = useState(reduce ? 2 : 0)
  const [rider, setRider] = useState({ x: 92, y: 300 })
  const [minutes, setMinutes] = useState(9.6)
  const [drawn, setDrawn] = useState(reduce ? 1 : 0)

  useEffect(() => {
    if (reduce) return
    let cancelled = false
    const stops: (() => void)[] = []
    const wait = (ms: number) => new Promise<void>(r => { const t = setTimeout(r, ms); stops.push(() => clearTimeout(t)) })
    const run = async () => {
      while (!cancelled) {
        setStep(0); setDrawn(0); setRider({ x: 92, y: 300 })
        const clock = animate(9.6, 0.4, { duration: 10.5, ease: 'linear', onUpdate: setMinutes }); stops.push(() => clock.stop())
        await wait(1300); if (cancelled) return
        setStep(1)
        const draw = animate(0, 1, { duration: 1.1, ease: 'easeInOut', onUpdate: setDrawn }); stops.push(() => draw.stop())
        await wait(1500); if (cancelled) return
        setStep(2)
        const total = path.current?.getTotalLength() ?? 1
        const ride = animate(0, 1, {
          duration: 6.2, ease: [0.45, 0, 0.55, 1],
          onUpdate: v => { const p = path.current?.getPointAtLength(v * total); if (p) setRider({ x: p.x, y: p.y }) },
        })
        stops.push(() => ride.stop())
        await wait(6400); if (cancelled) return
        setStep(3)
        await wait(2600)
      }
    }
    run()
    return () => { cancelled = true; stops.forEach(s => s()) }
  }, [reduce])

  const mins = Math.max(0, minutes)
  return (
    <div className="relative mx-auto aspect-[5/4] w-full max-w-[540px]">
      <div className="absolute inset-0 overflow-hidden rounded-[28px] border border-line bg-[#eef0ea] shadow-[var(--shadow-pop)]">
        <svg viewBox="0 0 500 400" className="h-full w-full" aria-hidden>
          {/* City blocks and a park. */}
          {[[20, 20, 150, 80], [190, 20, 120, 70], [330, 20, 150, 70], [20, 130, 110, 70], [150, 130, 90, 70], [300, 140, 180, 110],
            [20, 250, 50, 130], [120, 250, 120, 130], [260, 250, 60, 130], [340, 280, 140, 100]].map(([x, y, w, h], i) => (
            <rect key={i} x={x} y={y} width={w} height={h} rx="10" fill={i === 5 ? '#dcebd2' : '#e3e6dd'} />
          ))}
          {/* Streets */}
          {['M 0 110 H 500', 'M 0 220 H 500', 'M 92 0 V 400', 'M 266 0 V 400', 'M 0 300 H 92', 'M 404 0 V 400'].map(d => (
            <path key={d} d={d} stroke="#ffffff" strokeWidth="14" fill="none" />
          ))}
          {/* Route */}
          <path ref={path} d={ROUTE} fill="none" stroke="transparent" />
          <motion.path d={ROUTE} fill="none" stroke="#0b6b3a" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round"
            style={{ pathLength: drawn }} />
          {/* Dark store */}
          <g transform="translate(74 282)">
            <rect width="36" height="36" rx="10" fill="#0b6b3a" stroke="#fff" strokeWidth="3" />
            <path d="M9 15 11 10h14l2 5M9 15v11h18V15M9 15h18M16 26v-6h4v6" stroke="#fff" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round" />
          </g>
          {/* Home */}
          <g transform="translate(388 100)">
            <circle cx="16" cy="16" r="18" fill="#ffb020" stroke="#fff" strokeWidth="3" />
            <path d="M9 17 16 10l7 7M11 15v8h10v-8" stroke="#1a1d1a" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round" />
          </g>
          {/* Rider */}
          {step >= 2 && (
            <g transform={`translate(${rider.x - 15} ${rider.y - 15})`}>
              <circle cx="15" cy="15" r="15" fill="#1a1d1a" stroke="#fff" strokeWidth="3" />
              <circle cx="10" cy="18" r="2.6" stroke="#fff" strokeWidth="1.6" fill="none" />
              <circle cx="20" cy="18" r="2.6" stroke="#fff" strokeWidth="1.6" fill="none" />
              <path d="M10 18h4l2.6-5.5H19" stroke="#ffb020" strokeWidth="1.8" fill="none" strokeLinecap="round" />
            </g>
          )}
        </svg>
      </div>

      {/* Order card */}
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.5, duration: 0.6 }}
        className="absolute -bottom-8 -right-3 w-[250px] rounded-2xl border border-line bg-surface p-4 shadow-[var(--shadow-pop)] sm:-right-8">
        <div className="flex items-center justify-between">
          <span className="text-xs text-muted">Order SK-7Q2F</span>
          <span className="flex items-center gap-1 text-xs font-medium text-brand"><Timer className="h-3.5 w-3.5" /> 6× demo clock</span>
        </div>
        <AnimatePresence mode="wait">
          <motion.div key={step === 3 ? 'done' : 'eta'} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -6 }}
            className="display mt-1 text-[26px] font-bold leading-tight tracking-tight text-ink">
            {step === 3 ? 'Delivered in 9 min' : <>Arriving in <span className="num">{Math.max(1, Math.ceil(mins))}</span> min</>}
          </motion.div>
        </AnimatePresence>
        <div className="mt-3 grid grid-cols-4 gap-1">
          {STEPS.map((s, i) => <div key={s} className={`h-1.5 rounded-full transition-colors duration-500 ${i <= step ? 'bg-brand' : 'bg-line'}`} />)}
        </div>
        <div className="mt-2 flex items-center gap-1.5 text-xs text-ink-2">
          {step === 3 ? <Check className="h-3.5 w-3.5 text-brand" /> : <Package className="h-3.5 w-3.5 text-muted" />}{STEPS[step]}
        </div>
      </motion.div>

      <motion.div initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} transition={{ delay: 0.8 }}
        className="absolute -left-3 top-6 rounded-xl border border-line bg-surface px-3 py-2 text-xs shadow-[var(--shadow-pop)] sm:-left-8">
        <div className="text-muted">Stock held for you</div>
        <div className="mt-0.5 font-semibold text-ink">Milk ×2 · Bread · Eggs</div>
      </motion.div>
    </div>
  )
}
