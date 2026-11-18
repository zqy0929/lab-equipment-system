const puppeteer = require('puppeteer-core')
const OUT = 'C:/Users/zqy27/实验室设备预约管理系统/2026-10-30-09-12-56/lab-equipment-system/.shots'
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const sleep = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: true, args: ['--no-sandbox', '--disable-gpu'] })
  const page = await browser.newPage()
  await page.setViewport({ width: 1440, height: 950 })
  const errs = []
  page.on('pageerror', e => errs.push(e.message))
  await page.goto('http://localhost:5173/', { waitUntil: 'networkidle2' }); await sleep(1200)
  await page.type('input[placeholder="请输入账号"]', 'admin')
  await page.type('input[placeholder="请输入密码"]', 'admin123')
  await page.evaluate(() => { [...document.querySelectorAll('button')].find(b => b.textContent.replace(/\s/g, '').includes('登录')).click() })
  await sleep(3500)
  await page.goto('http://localhost:5173/equipment', { waitUntil: 'networkidle2' }); await sleep(2200)

  const dump = async () => page.evaluate(() => [...document.querySelectorAll('.eq-card')].map(c => {
    const name = c.querySelector('.eq-card-name').textContent.trim()
    const icon = c.querySelector('.eq-card-icon svg') ? c.querySelector('.eq-card-icon svg').innerHTML.slice(0, 40) : 'NO_ICON'
    return name + ' :: ' + icon
  }))

  for (const p of [1, 2, 3]) {
    if (p > 1) {
      await page.evaluate(n => { [...document.querySelectorAll('.el-pager li')].find(li => li.textContent.trim() === String(n))?.click() }, p)
      await sleep(1600)
    }
    const rows = await dump()
    console.log(`--- PAGE ${p} (${rows.length} cards) ---`)
    rows.forEach(r => console.log('  ' + r))
    await page.screenshot({ path: `${OUT}/eq-icons-p${p}.png` })
  }
  console.log('ERRORS:', errs.length)
  await browser.close()
})().catch(e => { console.error('FAILED:', e.message); process.exit(1) })
