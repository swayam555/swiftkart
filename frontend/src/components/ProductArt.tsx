import clsx from 'clsx'
import {
  Apple, Banana, Bath, Battery, Bean, Brush, Candy, Cherry, Citrus, Coffee, Cookie, Croissant, CupSoda, Dessert, Droplet, Egg,
  Flame, Gamepad2, Grape, Leaf, Milk, Nut, Package, Salad, Sandwich, Scissors, ShowerHead, Soup, Sprout, Toilet, Utensils,
  WashingMachine, Zap, type LucideIcon,
} from 'lucide-react'

const ICONS: Record<string, LucideIcon> = {
  'FV-BANANA': Banana, 'FV-ONION': Sprout, 'FV-TOMATO': Cherry, 'FV-POTATO': Nut, 'FV-CORIANDER': Leaf, 'FV-APPLE': Apple, 'FV-LEMON': Citrus,
  'DA-MILK': Milk, 'DA-BUTTER': Sandwich, 'DA-EGGS': Egg, 'DA-PANEER': Salad, 'DA-CURD': Dessert, 'DA-BREAD': Croissant,
  'SN-KURKURE': Flame, 'SN-COOKIES': Cookie, 'SN-MAGGI': Soup, 'SN-SILK': Candy,
  'BV-COKE': CupSoda, 'BV-REDBULL': Zap, 'BV-TEA': Coffee, 'BV-COFFEE': Bean, 'BV-WATER': Droplet, 'BV-AAMRAS': Grape,
  'PC-PASTE': Brush, 'PC-SOAP': Bath, 'PC-SHAMPOO': ShowerHead, 'PC-RAZOR': Scissors,
  'HM-DETERGENT': WashingMachine, 'HM-VIM': Utensils, 'HM-HARPIC': Toilet, 'HM-BATTERY': Battery, 'FLASH-PS5': Gamepad2,
}

/** Tile colours per aisle: a tint for the background and a deeper shade for the icon. */
const AISLE: Record<string, [string, string]> = {
  'FV': ['#eaf3e3', '#3f7d2a'], 'DA': ['#fdf3d8', '#946300'], 'SN': ['#fbe7df', '#b0452a'], 'BV': ['#e3eff8', '#25679a'],
  'PC': ['#efe8f7', '#6c4b98'], 'HM': ['#e3f1ef', '#2b6e66'], 'FLASH': ['#1a1d1a', '#ffb020'],
}

export const aisleColors = (sku: string) => AISLE[sku.split('-')[0]] ?? ['#f1efe8', '#464a45']

export default function ProductArt({ sku, className, size = 'md' }: { sku: string; className?: string; size?: 'sm' | 'md' | 'lg' }) {
  const Icon = ICONS[sku] ?? Package
  const [bg, fg] = aisleColors(sku)
  const dims = { sm: 'h-11 w-11 rounded-xl', md: 'aspect-[4/3] w-full rounded-xl', lg: 'h-20 w-20 rounded-2xl' }[size]
  const icon = { sm: 'h-5 w-5', md: 'h-10 w-10', lg: 'h-9 w-9' }[size]
  return (
    <div className={clsx('relative flex shrink-0 items-center justify-center overflow-hidden', dims, className)} style={{ background: bg }}>
      <div className="absolute -bottom-6 -right-6 h-20 w-20 rounded-full opacity-[0.08]" style={{ background: fg }} />
      <Icon className={icon} style={{ color: fg }} strokeWidth={1.6} />
    </div>
  )
}
