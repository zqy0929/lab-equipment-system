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
  await page.type('input[placeholder="请输入账号"]', 'stu2026001')
  await page.type('input[placeholder="请输入密码"]', '123456')
  await page.evaluate(() => { [...document.querySelectorAll('button')].find(b => b.textContent.replace(/\s/g, '').includes('登录')).click() })
  await sleep(3500)
  const badgeBefore = await page.evaluate(() => document.querySelector('.el-badge__content')?.textContent || 'NO_BADGE')
  // 点击铃铛打开消息中心
  await page.evaluate(() => document.querySelector('.bell')?.click())
  await sleep(2000)
  await page.screenshot({ path: `${OUT}/msg-auto-read.png` })
  const dlgText = await page.evaluate(() => document.querySelector('.el-dialog')?.innerText.replace(/\s+/g, ' ').slice(0, 150) || 'NO_DIALOG')
  const badgeAfter = await page.evaluate(() => document.querySelector('.el-badge__content')?.textContent || 'NO_BADGE')
  console.log('ERRORS:', errs.length)
  console.log('BADGE_BEFORE:', badgeBefore)
  console.log('DIALOG:', dlgText)
  console.log('BADGE_AFTER:', badgeAfter)
  await browser.close()
})().catch(e => { console.error('FAILED:', e.message); process.exit(1) })
