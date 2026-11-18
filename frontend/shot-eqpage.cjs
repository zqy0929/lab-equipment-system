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
  await page.goto('http://localhost:5173/equipment', { waitUntil: 'networkidle2' }); await sleep(2000)

  const detailName = () => page.evaluate(() => document.querySelector('.detail-name')?.textContent.trim() || 'NONE')
  const cardNames = () => page.evaluate(() => [...document.querySelectorAll('.eq-card-name')].map(e => e.textContent.trim()))

  console.log('P1 first detail:', await detailName())
  const cards1 = await cardNames()
  console.log('P1 cards:', cards1.join(' | '))

  // 点击第 3 台设备
  await page.evaluate(() => document.querySelectorAll('.eq-card')[2].click())
  await sleep(1200)
  const picked = await detailName()
  console.log('PICKED:', picked)

  // 翻到第 2 页
  await page.evaluate(() => { [...document.querySelectorAll('.el-pager li')].find(li => li.textContent.trim() === '2')?.click() })
  await sleep(1800)
  const afterPager = await detailName()
  const cards2 = await cardNames()
  console.log('P2 cards:', cards2.join(' | '))
  console.log('AFTER_PAGER:', afterPager)
  console.log('RESULT:', afterPager === picked ? 'PASS 保持选中不变' : 'FAIL 被切换了')
  await page.screenshot({ path: `${OUT}/eq-pager-keep.png` })

  // 翻回第 1 页，验证高亮恢复
  await page.evaluate(() => { [...document.querySelectorAll('.el-pager li')].find(li => li.textContent.trim() === '1')?.click() })
  await sleep(1500)
  const activeName = await page.evaluate(() => document.querySelector('.eq-card.is-active .eq-card-name')?.textContent.trim() || 'NONE')
  console.log('BACK P1 active card:', activeName, activeName === picked ? 'PASS 高亮恢复' : 'FAIL')
  console.log('ERRORS:', errs.length)
  await browser.close()
})().catch(e => { console.error('FAILED:', e.message); process.exit(1) })
