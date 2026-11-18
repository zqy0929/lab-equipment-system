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
  // 学生登录
  await page.goto('http://localhost:5173/', { waitUntil: 'networkidle2' }); await sleep(1200)
  await page.type('input[placeholder="请输入账号"]', 'stu2026001')
  await page.type('input[placeholder="请输入密码"]', '123456')
  await page.evaluate(() => { [...document.querySelectorAll('button')].find(b => b.textContent.replace(/\s/g, '').includes('登录')).click() })
  await sleep(3500)
  // 点击设备名录卡片，验证激活态
  await page.evaluate(() => { [...document.querySelectorAll('.menu-card')].find(c => c.textContent.includes('设备名录'))?.click() })
  await sleep(1800)
  await page.screenshot({ path: `${OUT}/sidebar-student.png` })
  const cards = await page.evaluate(() => [...document.querySelectorAll('.menu-card')].map(c => c.querySelector('.menu-cn').textContent).join(','))
  console.log('ERRORS:', errs.length)
  console.log('STUDENT_CARDS:', cards)
  console.log('URL:', page.url())
  await browser.close()
})().catch(e => { console.error('FAILED:', e.message); process.exit(1) })
