/**
 * 角色权限与看板卡片验证脚本
 * 用法：node verify-role.cjs
 * 前置：后端 8081、前端 5173 已启动；存在 admin / verify_TEACHER / verify_STUDENT 账号
 */
const puppeteer = require('puppeteer-core')
const fs = require('fs')
const http = require('http')

const OUT = 'C:/Users/zqy27/实验室设备预约管理系统/2026-10-30-09-12-56/lab-equipment-system/.shots'
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:5173'
const API = 'http://127.0.0.1:8081/api'

const sleep = ms => new Promise(r => setTimeout(r, ms))
let failed = 0
const check = (name, ok, extra = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${extra ? '  ' + extra : ''}`)
  if (!ok) failed++
}

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
      res.on('end', () => {
        let json = null
        try { json = JSON.parse(buf) } catch { /* ignore */ }
        resolve({ status: res.statusCode, body: json })
      })
    })
    req.on('error', reject)
    if (data) req.write(data)
    req.end()
  })
}

async function login(username, password) {
  const r = await request('POST', `${API}/auth/login`, null, { username, password })
  if (!r.body || !r.body.data) throw new Error(`登录失败 ${username}: ${JSON.stringify(r.body)}`)
  return r.body.data
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })

  // ---------- 1. 接口层权限 ----------
  const admin = await login('admin', 'admin123')
  const teacher = await login('verify_TEACHER', '123456')
  const student = await login('verify_STUDENT', '123456')
  console.log(`账号就绪: admin=${admin.user.role}, teacher=${teacher.user.role}, student=${student.user.role}\n`)

  const statsA = await request('GET', `${API}/stats/dashboard`, admin.token)
  const statsT = await request('GET', `${API}/stats/dashboard`, teacher.token)
  const statsS = await request('GET', `${API}/stats/dashboard`, student.token)
  check('管理员看板返回待审批数', typeof statsA.body.data.pendingApprovals === 'number', `= ${statsA.body.data.pendingApprovals}`)
  check('老师看板返回待审批数', typeof statsT.body.data.pendingApprovals === 'number', `= ${statsT.body.data.pendingApprovals}`)
  check('学生看板不返回待审批数（数据层收口）', statsS.body.data.pendingApprovals === null, `= ${statsS.body.data.pendingApprovals}`)

  const apvA = await request('GET', `${API}/admin/reservations/page?status=PENDING&page=1&size=3`, admin.token)
  const apvT = await request('GET', `${API}/admin/reservations/page?status=PENDING&page=1&size=3`, teacher.token)
  const apvS = await request('GET', `${API}/admin/reservations/page?status=PENDING&page=1&size=3`, student.token)
  check('审批接口 admin 可访问', apvA.status === 200, `HTTP ${apvA.status}`)
  check('审批接口 teacher 可访问', apvT.status === 200, `HTTP ${apvT.status}`)
  check('审批接口 student 被拒', apvS.status === 403, `HTTP ${apvS.status}`)

  const usrT = await request('GET', `${API}/admin/users/page?page=1&size=3`, teacher.token)
  const usrS = await request('GET', `${API}/admin/users/page?page=1&size=3`, student.token)
  check('用户管理接口 teacher 被拒', usrT.status === 403, `HTTP ${usrT.status}`)
  check('用户管理接口 student 被拒', usrS.status === 403, `HTTP ${usrS.status}`)

  // ---------- 2. 浏览器渲染 ----------
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: true,
    args: ['--no-sandbox', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1']
  })
  const page = await browser.newPage()
  await page.setViewport({ width: 1440, height: 900 })
  page.on('pageerror', e => console.log('PAGE_ERROR:', e.message))

  const useAccount = async (acc) => {
    const client = await page.target().createCDPSession()
    await client.send('Network.clearBrowserCache')
    await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
    await page.evaluate((t, u) => {
      localStorage.setItem('token', t)
      localStorage.setItem('user', JSON.stringify(u))
    }, acc.token, acc.user)
  }

  const cardLabels = () => page.evaluate(() =>
    [...document.querySelectorAll('.stat-card .stat-label')].map(e => e.textContent.trim()))

  const clickCard = async (label) => {
    const ok = await page.evaluate(txt => {
      const card = [...document.querySelectorAll('.stat-card')]
        .find(c => c.querySelector('.stat-label')?.textContent.trim() === txt)
      if (!card) return false
      card.click()
      return true
    }, label)
    await sleep(1800)
    return ok
  }

  // --- 管理员：6 张卡片 + 跳转 ---
  await useAccount(admin)
  await page.goto(`${BASE}/dashboard`, { waitUntil: 'networkidle2' })
  await sleep(1800)
  let labels = await cardLabels()
  check('管理员看板渲染 6 张卡片', labels.length === 6, JSON.stringify(labels))
  await page.screenshot({ path: `${OUT}/20-dashboard-admin.png` })

  // hover 效果
  const hoverStyle = await page.evaluate(() => {
    const card = document.querySelector('.stat-card')
    const cs = getComputedStyle(card)
    return { cursor: cs.cursor, transition: cs.transitionProperty }
  })
  check('卡片 hover 为手型光标', hoverStyle.cursor === 'pointer', hoverStyle.cursor)

  // 设备总数 -> 设备名录
  await clickCard('设备总数')
  check('点击「设备总数」跳设备名录', page.url().includes('/equipment') && !page.url().includes('status='), page.url())

  // 可用设备 -> 设备名录 筛选空闲
  await page.goto(`${BASE}/dashboard`, { waitUntil: 'networkidle2' })
  await sleep(1200)
  await clickCard('可用设备')
  const eqUrl = page.url()
  const statusTags = await page.evaluate(() =>
    [...document.querySelectorAll('.el-table__body tr .el-tag')].map(e => e.textContent.trim()))
  check('点击「可用设备」带 status=AVAILABLE', eqUrl.includes('status=AVAILABLE'), eqUrl)
  check('设备列表已筛选为可用', statusTags.length > 0 && statusTags.every(t => t === '可用'), JSON.stringify(statusTags))
  await page.screenshot({ path: `${OUT}/21-equipment-available.png` })

  // 维修中 -> 损耗记录 FIXING
  await page.goto(`${BASE}/dashboard`, { waitUntil: 'networkidle2' })
  await sleep(1200)
  await clickCard('维修中')
  const dmgUrl = page.url()
  const activeRadio = await page.evaluate(() => {
    const el = document.querySelector('.el-radio-button.is-active')
    return el ? el.textContent.trim() : ''
  })
  check('点击「维修中」跳损耗记录 status=FIXING', dmgUrl.includes('/damage') && dmgUrl.includes('status=FIXING'), dmgUrl)
  check('损耗页筛选按钮选中「维修中」', activeRadio === '维修中', activeRadio)
  await page.screenshot({ path: `${OUT}/22-damage-fixing.png` })

  // 待处理损耗 -> 损耗记录 REPORTED
  await page.goto(`${BASE}/dashboard`, { waitUntil: 'networkidle2' })
  await sleep(1200)
  await clickCard('待处理损耗')
  const dmg2 = page.url()
  const activeRadio2 = await page.evaluate(() => {
    const el = document.querySelector('.el-radio-button.is-active')
    return el ? el.textContent.trim() : ''
  })
  check('点击「待处理损耗」跳损耗记录 status=REPORTED', dmg2.includes('status=REPORTED'), dmg2)
  check('损耗页筛选按钮选中「待处理」', activeRadio2 === '待处理', activeRadio2)
  await page.screenshot({ path: `${OUT}/23-damage-reported.png` })

  // 本月预约 -> 预约日历
  await page.goto(`${BASE}/dashboard`, { waitUntil: 'networkidle2' })
  await sleep(1200)
  await clickCard('本月预约')
  const calUrl = page.url()
  const calMonth = await page.evaluate(() => document.querySelector('.cal-month')?.textContent.trim() || '')
  check('点击「本月预约」跳预约日历并带月份', /\/calendar\?month=\d{4}-\d{2}/.test(calUrl), calUrl)
  check('日历显示本月', calMonth.includes(String(new Date().getFullYear())), calMonth)
  await page.screenshot({ path: `${OUT}/24-calendar-month.png` })

  // 待审批 -> 审批页（管理员）
  await page.goto(`${BASE}/dashboard`, { waitUntil: 'networkidle2' })
  await sleep(1200)
  await clickCard('待审批')
  check('管理员点击「待审批」进入审批页', page.url().includes('/approval'), page.url())

  // --- 老师：6 张卡片 + 侧栏有审批 ---
  await useAccount(teacher)
  await page.goto(`${BASE}/dashboard`, { waitUntil: 'networkidle2' })
  await sleep(1800)
  labels = await cardLabels()
  const teacherMenu = await page.evaluate(() =>
    [...document.querySelectorAll('.el-menu-item')].map(e => e.textContent.trim()))
  check('老师看板渲染 6 张卡片', labels.length === 6, JSON.stringify(labels))
  check('老师侧栏含借用审批', teacherMenu.some(m => m.includes('借用审批')), JSON.stringify(teacherMenu))
  check('老师侧栏不含用户管理', !teacherMenu.some(m => m.includes('用户管理')), '')
  await page.screenshot({ path: `${OUT}/25-dashboard-teacher.png` })

  // --- 学生：5 张卡片、无待审批 ---
  await useAccount(student)
  await page.goto(`${BASE}/dashboard`, { waitUntil: 'networkidle2' })
  await sleep(1800)
  labels = await cardLabels()
  const studentMenu = await page.evaluate(() =>
    [...document.querySelectorAll('.el-menu-item')].map(e => e.textContent.trim()))
  check('学生看板渲染 5 张卡片', labels.length === 5, JSON.stringify(labels))
  check('学生看板无「待审批」卡片', !labels.includes('待审批'), JSON.stringify(labels))
  check('学生侧栏无借用审批', !studentMenu.some(m => m.includes('借用审批')), JSON.stringify(studentMenu))
  await page.screenshot({ path: `${OUT}/26-dashboard-student.png` })

  // 学生强行访问审批页应被重定向
  await page.goto(`${BASE}/approval`, { waitUntil: 'networkidle2' })
  await sleep(1500)
  check('学生直接访问 /approval 被重定向到看板', page.url().includes('/dashboard'), page.url())

  await browser.close()
  console.log(`\n结果：${failed === 0 ? '全部通过' : failed + ' 项失败'}`)
  process.exit(failed === 0 ? 0 : 1)
})().catch(e => { console.error('FAILED:', e); process.exit(1) })
