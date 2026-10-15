// End-to-end browser tests against a running stack:  cd e2e && npm install && npx playwright install chromium && npm test
// Every step is something a real user does; console errors and 5xx responses fail the run.
import { expect, test, type Page } from '@playwright/test'

const B = process.env.BASE_URL ?? 'http://localhost:3002'
const addAndCheckout = async (p, products) => {
  for (const name of products) {
    const card = p.locator('div.group', { hasText: name }).first()
    await card.getByRole('button', { name: 'Add' }).click()
  }
  await p.getByRole('button', { name: /items?\s*₹|Cart/ }).first().click()
  await p.getByRole('button', { name: 'Proceed to pay' }).click()
  await expect(p.getByRole('heading', { name: 'Pay with UPI' })).toBeVisible()
}
type Step = [string, (p: Page) => Promise<void>]

const steps: Step[] = [
  ['landing renders with route demo', async p => { await p.goto(B); await expect(p.getByRole('heading', { level: 1 })).toContainText('ten minutes'); await expect(p.getByText(/Arriving in|Delivered in/).first()).toBeVisible() }],
  ['flash-sale lab animates on scroll', async p => { await p.locator('#flash').scrollIntoViewIfNeeded(); await p.waitForTimeout(3500); await expect(p.getByText('Refunded after paying').first()).toBeVisible(); await expect(p.locator('#flash').getByText('234')).toBeVisible() }],
  ['other sections reveal', async p => { for (const id of ['saga', 'dispatch', 'architecture']) { await p.locator('#' + id).scrollIntoViewIfNeeded(); await p.waitForTimeout(300) } await expect(p.getByText('An order only ever moves forward.')).toBeVisible() }],
  ['"Start shopping" asks to sign in', async p => { await p.evaluate(() => scrollTo(0, 0)); await p.getByRole('link', { name: 'Start shopping' }).first().click(); await expect(p).toHaveURL(/\/login$/) }],
  ['invalid phone keeps Continue disabled', async p => { const ph = p.getByRole('textbox').nth(1); await ph.fill('123'); await expect(p.getByRole('button', { name: /Continue/ })).toBeDisabled(); await ph.fill('9876543210') }],
  ['customer signs in', async p => { await p.getByRole('button', { name: /Continue/ }).click(); await expect(p).toHaveURL(/\/shop$/); await expect(p.getByText('Where are you?')).toBeVisible() }],
  ['choose Indiranagar: live catalogue', async p => { await p.getByRole('button', { name: 'Choose location' }).click(); await p.getByRole('button', { name: 'Indiranagar' }).click(); await expect(p.getByText(/minute delivery/).first()).toBeVisible(); await expect(p.getByRole('button', { name: 'Add' }).first()).toBeVisible() }],
  ['category filter', async p => { await p.getByRole('button', { name: 'Snacks & Munchies', exact: true }).click(); await expect(p.getByRole('heading', { name: 'Snacks & Munchies' })).toBeVisible(); await expect(p.getByRole('heading', { name: 'Personal Care' })).toHaveCount(0); await p.getByRole('button', { name: 'All', exact: true }).click() }],
  ['cart: add, step quantity, delivery fee', async p => { const card = p.locator('div.group', { hasText: 'Amul Taaza Toned Milk' }).first(); await card.getByRole('button', { name: 'Add' }).click(); await card.getByRole('button', { name: 'Add one' }).click(); await expect(card.getByText('2', { exact: true })).toBeVisible(); await p.getByRole('button', { name: /items?\s*₹/ }).click(); await expect(p.getByText(/more for free delivery/)).toBeVisible(); await p.getByRole('button', { name: 'Close cart' }).click() }],
  ['checkout reserves stock and shows the 2-minute hold', async p => { await addAndCheckout(p, ['Brown Bread']); await expect(p.getByText(/held for you/)).toBeVisible(); await expect(p.getByText(/^1:5\d$/)).toBeVisible() }],
  ['pay: order confirmed and tracked live', async p => { await p.getByRole('button', { name: /^Pay ₹/ }).click(); await expect(p).toHaveURL(/\/shop\/orders\/SK-/); await expect(p.getByRole('heading', { level: 1 })).toContainText(/Arriving in|Order confirmed|Packing/, { timeout: 15000 }) }],
  ['order is delivered (6× clock)', async p => { await expect(p.getByRole('heading', { level: 1 })).toContainText('Delivered in', { timeout: 180000 }); await expect(p.getByText(/On time|Promised/).first()).toBeVisible() }],
  ['declined payment releases stock', async p => { await p.goto(B + '/shop'); await addAndCheckout(p, ['Farm Eggs']); await p.getByText('Simulate a declined payment').click(); await p.getByRole('button', { name: /^Pay ₹/ }).click(); await expect(p.getByRole('heading', { level: 1 })).toContainText('Payment failed', { timeout: 15000 }) }],
  ['cancel before pickup', async p => { await p.goto(B + '/shop'); await addAndCheckout(p, ['Coca-Cola']); await p.getByRole('button', { name: /^Pay ₹/ }).click(); await expect(p.getByRole('button', { name: 'Cancel order' })).toBeVisible({ timeout: 15000 }); await p.getByRole('button', { name: 'Cancel order' }).click(); await expect(p.getByRole('heading', { level: 1 })).toContainText('Cancelled', { timeout: 10000 }) }],
  ['orders list shows all three outcomes', async p => { await p.getByRole('link', { name: 'Orders', exact: true }).click(); await expect(p.getByText(/Delivered in [\d.]+ min/).first()).toBeVisible(); await expect(p.getByText('Payment failed').first()).toBeVisible(); await expect(p.getByText('Cancelled').first()).toBeVisible() }],
  ['ops sign-in', async p => { await p.goto(B + '/ops'); await p.getByRole('button', { name: 'Sign in' }).click(); await expect(p.getByRole('heading', { name: 'Live operations' })).toBeVisible(); await expect(p.locator('.leaflet-tile-loaded').first()).toBeVisible({ timeout: 15000 }) }],
  ['load generator starts and places orders', async p => { await p.getByRole('button', { name: /^Start$/ }).click(); await expect(p.getByText(/[1-9]\d* simulated orders placed/)).toBeVisible({ timeout: 20000 }) }],
  ['flash-sale lab: atomic never oversells', async p => { await p.getByRole('button', { name: 'Flash-sale lab' }).click(); await p.getByRole('button', { name: /atomic Redis Lua/ }).click(); const card = p.locator('section', { hasText: 'Told “reserved”' }).filter({ hasText: 'Atomic reservation' }).first(); await expect(card).toBeVisible({ timeout: 30000 }); await expect(card.getByText('Oversold promises')).toBeVisible(); await expect(card).toContainText('Exactly 50 customers') }],
  ['flash-sale lab: naive oversells', async p => { await p.getByRole('button', { name: /naive check-then-act/ }).click(); await expect(p.locator('section', { hasText: 'Told “reserved”' }).filter({ hasText: 'Naive check-then-act' }).first()).toContainText('had to be refunded', { timeout: 30000 }) }],
  ['stock board: restock +24', async p => { await p.getByRole('button', { name: 'Stock & reconciliation' }).click(); const row = p.locator('tr', { hasText: 'Bisleri Water' }); await expect(row).toBeVisible(); const before = Number(await row.locator('td').nth(3).innerText()); await row.getByRole('button', { name: '+24' }).click(); await expect(row.locator('td').nth(3)).toHaveText(String(before + 24), { timeout: 10000 }) }],
  ['reconciliation repairs and reports', async p => { await p.getByRole('button', { name: 'Run now' }).click(); await p.waitForTimeout(31000); await p.getByRole('button', { name: 'Run now' }).click(); await expect(p.getByText(/counters checked/)).toBeVisible() }],
  ['stop load generator', async p => { await p.getByRole('button', { name: 'Live operations' }).click(); await p.getByRole('button', { name: 'Stop' }).click(); await expect(p.getByRole('button', { name: /^Start$/ })).toBeVisible({ timeout: 10000 }) }],
]

test('customer and ops journey', async ({ page }) => {
  const problems: string[] = []
  page.on('pageerror', e => problems.push(`page error: ${e.message}`))
  page.on('response', r => { if (r.status() >= 500) problems.push(`HTTP ${r.status()} ${r.request().method()} ${r.url()}`) })
  for (const [label, fn] of steps) await test.step(label, () => fn(page))
  expect(problems).toEqual([])
})

test.describe('phone width', () => {
  test.use({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })

  test('no page scrolls sideways', async ({ page, request }) => {
    const paths = await (async () => {
  const c = await (await request.post('/api/auth/customer', { data: { name: 'Mobile check', phone: '9876500009' } })).json()
  const o = await (await request.post('/api/auth/ops', { data: { username: 'ops', password: 'swiftkart' } })).json()
  await page.addInitScript(([c, o]) => {
    localStorage.setItem('swiftkart.session.CUSTOMER', c); localStorage.setItem('swiftkart.session.OPS', o)
    localStorage.setItem('swiftkart.place', JSON.stringify({ name: 'Indiranagar, Bengaluru', lat: 12.9719, lon: 77.6412 }))
  }, [JSON.stringify(c), JSON.stringify(o)])
  return ['/', '/shop', '/shop/orders', '/ops']
    })()
    for (const path of paths) {
      await page.goto(path)
      await page.waitForTimeout(1500)
      const width = await page.evaluate(() => document.documentElement.scrollWidth)
      expect(width, `${path} is wider than the screen`).toBeLessThanOrEqual(391)
    }
  })
})
