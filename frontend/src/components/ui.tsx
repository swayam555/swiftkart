import clsx from 'clsx'
import type { ReactNode } from 'react'

type Variant = 'primary' | 'secondary' | 'danger' | 'dark' | 'ghost' | 'sun'

export function Button({ children, onClick, variant = 'primary', size = 'md', disabled, type = 'button', className, title }: {
  children: ReactNode; onClick?: () => void; variant?: Variant; size?: 'sm' | 'md' | 'lg'; disabled?: boolean
  type?: 'button' | 'submit'; className?: string; title?: string
}) {
  const styles: Record<Variant, string> = {
    primary: 'bg-brand text-white hover:bg-brand-dark',
    secondary: 'bg-surface text-ink ring-1 ring-inset ring-line-2 hover:bg-cream',
    danger: 'bg-surface text-danger ring-1 ring-inset ring-danger/30 hover:bg-danger/5',
    dark: 'bg-ink text-white hover:bg-ink/90',
    ghost: 'text-ink-2 hover:bg-ink/5',
    sun: 'bg-sun text-ink hover:brightness-95',
  }
  const sizes = { sm: 'h-8 px-3 text-[13px]', md: 'h-10 px-4 text-sm', lg: 'h-12 px-5 text-[15px]' }
  return (
    <button type={type} onClick={onClick} disabled={disabled} title={title}
      className={clsx('focus-ring inline-flex select-none items-center justify-center gap-2 rounded-xl font-semibold transition active:scale-[0.98] disabled:pointer-events-none disabled:opacity-45',
        styles[variant], sizes[size], className)}>
      {children}
    </button>
  )
}

export function Card({ title, subtitle, action, children, className, padded = true }: {
  title?: ReactNode; subtitle?: ReactNode; action?: ReactNode; children: ReactNode; className?: string; padded?: boolean
}) {
  return (
    <section className={clsx('rounded-[var(--radius-card)] border border-line bg-surface shadow-[var(--shadow-card)]', className)}>
      {(title || action) && (
        <header className="flex items-start justify-between gap-3 px-5 pt-4">
          <div>
            <h2 className="text-[15px] font-semibold tracking-tight text-ink">{title}</h2>
            {subtitle && <p className="mt-0.5 text-[13px] text-muted">{subtitle}</p>}
          </div>
          {action}
        </header>
      )}
      <div className={clsx(padded ? 'p-5' : (title || action) ? 'pt-3' : '')}>{children}</div>
    </section>
  )
}

const TONES: Record<string, string> = { slate: 'text-ink', green: 'text-brand', amber: 'text-[#a96800]', red: 'text-danger', blue: 'text-info' }

export function Stat({ label, value, hint, tone = 'slate' }: { label: string; value: ReactNode; hint?: ReactNode; tone?: string }) {
  return (
    <div className="bg-surface px-5 py-4">
      <div className="text-[13px] text-muted">{label}</div>
      <div className={clsx('num mt-1.5 text-[26px] font-semibold leading-none tracking-tight', TONES[tone] ?? TONES.slate)}>{value}</div>
      {hint && <div className="mt-2 text-xs text-muted">{hint}</div>}
    </div>
  )
}

/** A row of stats sharing hairline dividers. */
export function StatGrid({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={clsx('grid gap-px overflow-hidden rounded-[var(--radius-card)] border border-line bg-line', className)}>{children}</div>
}

const PILL: Record<string, string> = {
  slate: 'bg-ink/[0.06] text-ink-2', green: 'bg-brand-soft text-brand', amber: 'bg-sun-soft text-[#8a5600]',
  red: 'bg-danger/10 text-danger', blue: 'bg-info/10 text-info', yellow: 'bg-sun-soft text-[#8a5600]', dark: 'bg-ink text-white',
}

export function Pill({ children, tone = 'slate', dot }: { children: ReactNode; tone?: string; dot?: boolean }) {
  return (
    <span className={clsx('inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium', PILL[tone] ?? PILL.slate)}>
      {dot && <span className="h-1.5 w-1.5 rounded-full bg-current" />}{children}
    </span>
  )
}

export function ErrorNote({ error }: { error: string | null }) {
  return error ? <div role="alert" className="rounded-xl border border-danger/20 bg-danger/[0.06] px-3.5 py-2.5 text-sm text-danger">{error}</div> : null
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={clsx('skeleton', className)} />
}

/** SwiftKart mark: a ten-minute clock arc around a parcel dot. */
export function Logo({ className, dark }: { className?: string; dark?: boolean }) {
  return (
    <span className={clsx('inline-flex items-center gap-2', className)}>
      <svg viewBox="0 0 32 32" className="h-7 w-7" aria-hidden>
        <rect width="32" height="32" rx="9" fill={dark ? '#ffb020' : '#0b6b3a'} />
        <path d="M16 6.5 A9.5 9.5 0 1 1 7.8 11.2" fill="none" stroke={dark ? '#1a1d1a' : '#ffb020'} strokeWidth="3" strokeLinecap="round" />
        <circle cx="16" cy="16" r="3.2" fill={dark ? '#1a1d1a' : '#ffffff'} />
      </svg>
      <span className={clsx('display text-[19px] font-bold tracking-[-0.02em]', dark ? 'text-white' : 'text-ink')}>swiftkart</span>
    </span>
  )
}

export const STATUS_TONE: Record<string, string> = {
  PAYMENT_PENDING: 'amber', CONFIRMED: 'blue', PACKING: 'blue', PACKED: 'blue', OUT_FOR_DELIVERY: 'yellow',
  DELIVERED: 'green', CANCELLED: 'red', PAYMENT_FAILED: 'red', EXPIRED: 'red',
}
