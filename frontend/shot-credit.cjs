const puppeteer = require('puppeteer-core')
const OUT = 'C:/Users/zqy27/实验室设备预约管理系统/2026-10-30-09-12-56/lab-equipment-system/.shots'
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const sleep = ms => new Promise(r => setTimeout(r, ms))
let url = process.argv[2] || 'credits'
if (!url.startsWith('/')) url = '/' + url
const shot = process.argv[3] || 'test'

;(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME, headless: true,
    args: ['--no-sandbox', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1']
  })
  const page = await browser.newPage()
  await page.setViewport({ width: 1440, height: 900 })
  const errs = []
  page.on('pageerror', e => errs.push(e.message))

  await page.goto('http://localhost:5173/', { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(1200)
  await page.type('input[placeholder="请输入账号"]', 'admin')
  await page.type('input[placeholder="请输入密码"]', 'admin123')
  await page.evaluate(() => { [...document.querySelectorAll('button')].find(b => b.textContent.replace(/\s/g, '').includes('登录')).click() })
  await sleep(3500)

  await page.goto('http://localhost:5173' + url, { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(2200)
  await page.screenshot({ path: `${OUT}/${shot}.png`, fullPage: true })

  const text = await page.evaluate(() => document.body.innerText.replace(/\s+/g, ' ').slice(0, 300))
  console.log('ERRORS:', errs.length)
  ;[...new Set(errs)].slice(0, 5).forEach(m => console.log('  ->', m))
  console.log('TEXT:', text)
  await browser.close()
})().catch(e => { console.error('FAILED:', e.message); process.exit(1) })
