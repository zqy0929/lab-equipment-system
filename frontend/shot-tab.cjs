const puppeteer = require('puppeteer-core')
const OUT = 'C:/Users/zqy27/实验室设备预约管理系统/2026-10-30-09-12-56/lab-equipment-system/.shots'
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const sleep = ms => new Promise(r => setTimeout(r, ms))
;(async () => {
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: true, args: ['--no-sandbox', '--disable-gpu'] })
  const page = await browser.newPage()
  await page.setViewport({ width: 1440, height: 900 })
  const errs = []
  page.on('pageerror', e => errs.push(e.message))
  await page.goto('http://localhost:5173/', { waitUntil: 'networkidle2' }); await sleep(1200)
  await page.type('input[placeholder="请输入账号"]', 'admin')
  await page.type('input[placeholder="请输入密码"]', 'admin123')
  await page.evaluate(() => { [...document.querySelectorAll('button')].find(b => b.textContent.replace(/\s/g, '').includes('登录')).click() })
  await sleep(3500)
  await page.goto('http://localhost:5173/credits', { waitUntil: 'networkidle2' }); await sleep(1800)
  // 切到违约记录 tab
  await page.evaluate(() => { [...document.querySelectorAll('.tab-btn')].find(b => b.textContent.includes('违约记录')).click() })
  await sleep(1500)
  await page.screenshot({ path: `${OUT}/final-violations.png` })
  // 打开登记违约弹窗
  await page.evaluate(() => { [...document.querySelectorAll('button')].find(b => b.textContent.replace(/\s/g,'') === '登记违约')?.click() })
  await sleep(1500)
  await page.screenshot({ path: `${OUT}/final-dialog.png` })
  console.log('ERRORS:', errs.length); [...new Set(errs)].slice(0,3).forEach(m => console.log(' ->', m))
  await browser.close()
})().catch(e => { console.error('FAILED:', e.message); process.exit(1) })
