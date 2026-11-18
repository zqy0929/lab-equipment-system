const puppeteer = require('puppeteer-core')

const OUT = 'C:/Users/zqy27/实验室设备预约管理系统/2026-10-30-09-12-56/lab-equipment-system/.shots'
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const sleep = ms => new Promise(r => setTimeout(r, ms))

;(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: true,
    args: ['--no-sandbox', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1']
  })
  const page = await browser.newPage()
  await page.setViewport({ width: 1440, height: 900 })
  page.on('pageerror', e => console.log('PAGE_ERROR:', e.message))

  await page.goto('http://localhost:5173/', { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(1200)
  await page.type('input[placeholder="请输入账号"]', 'admin')
  await page.type('input[placeholder="请输入密码"]', 'admin123')
  await page.evaluate(() => {
    const btn = [...document.querySelectorAll('button')].find(b => b.textContent.replace(/\s/g, '').includes('登录'))
    if (!btn) throw new Error('login button not found')
    btn.click()
  })
  await sleep(3500)

  await page.goto('http://localhost:5173/equipment', { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(1800)
  await page.screenshot({ path: `${OUT}/md-01-equipment.png` })
  console.log('shot md-01-equipment')

  // 点击右侧第 3 张卡片，验证主栏切换
  const clicked = await page.evaluate(() => {
    const cards = [...document.querySelectorAll('.eq-card')]
    if (cards.length < 3) return false
    cards[2].click()
    return cards[2].querySelector('.eq-card-name').textContent
  })
  await sleep(700)
  await page.screenshot({ path: `${OUT}/md-02-equipment-selected.png` })
  console.log('clicked card:', clicked)

  const detailName = await page.evaluate(() => {
    const el = document.querySelector('.detail-name')
    return el ? el.textContent : null
  })
  console.log('detail name:', detailName)

  await browser.close()
})().catch(e => { console.error('FAILED:', e.message); process.exit(1) })
