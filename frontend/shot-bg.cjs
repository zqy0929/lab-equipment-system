/** 背景效果验证截图：node shot-bg.cjs */
const puppeteer = require('puppeteer-core')
const fs = require('fs')
const http = require('http')

const OUT = 'C:/Users/zqy27/实验室设备预约管理系统/2026-10-30-09-12-56/lab-equipment-system/.shots'
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const API = 'http://127.0.0.1:8081/api'
const sleep = ms => new Promise(r => setTimeout(r, ms))

function request(method, path, token, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null
    const u = new URL(path)
    const req = http.request({
      hostname: u.hostname, port: u.port, path: u.pathname + u.search, method,
      headers: {
        ...(data ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) } : {}),
        ...(token ? { Authorization: 'Bearer ' + token } : {})
      }
    }, res => {
      let buf = ''
      res.on('data', c => (buf += c))
      res.on('end', () => resolve(JSON.parse(buf || 'null')))
    })
    req.on('error', reject)
    if (data) req.write(data)
    req.end()
  })
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await puppeteer.launch({
    executablePath: CHROME, headless: true,
    args: ['--no-sandbox', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1']
  })
  const page = await browser.newPage()
  await page.setViewport({ width: 1440, height: 900 })

  // 1. 登录页（未登录，看完整背景插画）
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(2000)
  await page.screenshot({ path: `${OUT}/30-login-newbg.png` })
  console.log('shot 30-login-newbg')

  // 2. 管理员：看板 + 设备名录
  const login = await request('POST', `${API}/auth/login`, null, { username: 'admin', password: 'admin123' })
  await page.evaluateOnNewDocument((t, u) => {
    localStorage.setItem('token', t); localStorage.setItem('user', JSON.stringify(u))
  }, login.data.token, login.data.user)

  await page.goto(`${BASE}/dashboard`, { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(2200)
  await page.screenshot({ path: `${OUT}/31-dashboard-newbg.png` })
  console.log('shot 31-dashboard-newbg')

  await page.goto(`${BASE}/equipment`, { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(1800)
  await page.screenshot({ path: `${OUT}/32-equipment-newbg.png` })
  console.log('shot 32-equipment-newbg')

  await browser.close()
})().catch(e => { console.error('FAILED:', e.message); process.exit(1) })
