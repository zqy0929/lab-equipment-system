const puppeteer = require('puppeteer-core')
const fs = require('fs')

const OUT = 'C:/Users/zqy27/实验室设备预约管理系统/2026-10-30-09-12-56/lab-equipment-system/.shots'
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'

const sleep = ms => new Promise(r => setTimeout(r, ms))

const clickByText = async (page, selector, text) => {
  await page.evaluate((sel, txt) => {
    const el = [...document.querySelectorAll(sel)].find(x => x.textContent.replace(/\s/g, '').includes(txt))
    if (el) el.click()
  }, selector, text)
}

const typeInto = async (page, placeholder, value) => {
  const sel = `input[placeholder="${placeholder}"]`
  await page.waitForSelector(sel, { visible: true })
  await page.click(sel, { clickCount: 3 })
  await page.type(sel, value)
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: true,
    args: ['--no-sandbox', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1']
  })
  const page = await browser.newPage()
  await page.setViewport({ width: 1440, height: 900 })
  page.on('pageerror', e => console.log('PAGE_ERROR:', e.message))

  await page.goto('http://localhost:5173/', { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(1500)
  await page.screenshot({ path: `${OUT}/01-login.png` })
  console.log('shot 01-login')

  // 空表单提交 → 校验错误
  await clickByText(page, 'button', '登录')
  await sleep(900)
  await page.screenshot({ path: `${OUT}/02-login-error.png` })
  console.log('shot 02-login-error')

  const card = await page.$('.auth-card')
  if (card) await card.screenshot({ path: `${OUT}/03-login-error-card.png` })

  // 前往注册
  await clickByText(page, '.auth-switch a', '前往注册')
  await sleep(900)
  await page.screenshot({ path: `${OUT}/04-register.png` })
  console.log('shot 04-register')

  // 注册校验：两次密码不一致
  await typeInto(page, '用户名（3-30 个字符）', 'stu001')
  await typeInto(page, '密码（6-32 位）', '123456')
  await typeInto(page, '确认密码', '654321')
  await typeInto(page, '姓名', '张三')
  await clickByText(page, 'button', '注册')
  await sleep(900)
  await page.screenshot({ path: `${OUT}/05-register-error.png` })
  console.log('shot 05-register-error')
  const card2 = await page.$('.auth-card')
  if (card2) await card2.screenshot({ path: `${OUT}/06-register-error-card.png` })

  // 返回登录并登录
  await clickByText(page, '.auth-switch a', '返回登录')
  await sleep(700)
  await typeInto(page, '请输入账号', 'admin')
  await typeInto(page, '请输入密码', 'admin123')
  await clickByText(page, 'button', '登录')
  await sleep(4200)
  await page.screenshot({ path: `${OUT}/07-dashboard.png` })
  console.log('shot 07-dashboard, url =', page.url())

  const pages = [
    ['/equipment', '08-equipment'],
    ['/calendar', '09-calendar'],
    ['/my-reservations', '10-my-reservations'],
    ['/approval', '11-approval'],
    ['/damage', '12-damage'],
    ['/notices', '13-notices'],
    ['/users', '14-users']
  ]
  for (const [p, name] of pages) {
    await page.goto('http://localhost:5173' + p, { waitUntil: 'networkidle2', timeout: 60000 })
    await sleep(1600)
    await page.screenshot({ path: `${OUT}/${name}.png` })
    console.log('shot', name)
  }

  await browser.close()
})().catch(e => { console.error('FAILED:', e.message); process.exit(1) })
