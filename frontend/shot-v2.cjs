const puppeteer = require('puppeteer-core')
const OUT = 'C:/Users/zqy27/实验室设备预约管理系统/2026-10-30-09-12-56/lab-equipment-system/.shots'
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const sleep = ms => new Promise(r => setTimeout(r, ms))

;(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME, headless: true,
    args: ['--no-sandbox', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1']
  })
  const page = await browser.newPage()
  await page.setViewport({ width: 1440, height: 900 })
  page.on('pageerror', e => console.log('PAGE_ERROR:', e.message))

  // 登录页：英文副标题
  await page.goto('http://localhost:5173/', { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(1500)
  await page.screenshot({ path: `${OUT}/v2-01-login.png` })
  console.log('01 login done')

  // 管理员登录
  await page.type('input[placeholder="请输入账号"]', 'admin')
  await page.type('input[placeholder="请输入密码"]', 'admin123')
  await page.evaluate(() => { [...document.querySelectorAll('button')].find(b => b.textContent.replace(/\s/g, '').includes('登录')).click() })
  await sleep(4000)

  // 设备名录：附件模块 + 导出按钮
  await page.goto('http://localhost:5173/equipment', { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(1800)
  await page.screenshot({ path: `${OUT}/v2-02-equipment-files.png` })
  console.log('02 equipment done')

  // 看板：导出按钮 + 铃铛
  await page.goto('http://localhost:5173/dashboard', { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(1800)
  await page.screenshot({ path: `${OUT}/v2-03-dashboard.png` })
  console.log('03 dashboard done')

  // 消息中心弹窗
  await page.evaluate(() => document.querySelector('.bell')?.click())
  await sleep(1200)
  await page.screenshot({ path: `${OUT}/v2-04-messages.png` })
  console.log('04 messages done')

  // 系统配置页
  await page.goto('http://localhost:5173/config', { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(1500)
  await page.screenshot({ path: `${OUT}/v2-05-config.png` })
  console.log('05 config done')

  // 信用管理页
  await page.goto('http://localhost:5173/credits', { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(1500)
  await page.screenshot({ path: `${OUT}/v2-06-credits.png` })
  console.log('06 credits done')

  await browser.close()
})().catch(e => { console.error('FAILED:', e.message); process.exit(1) })
