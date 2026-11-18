<template>
  <div class="auth-bg">
    <div class="auth-card">
      <!-- 标题区 -->
      <header class="auth-head">
        <h1 class="auth-title">实验室设备预约与管理系统</h1>
        <p class="auth-subtitle">Laboratory Equipment Management System</p>
      </header>

      <!-- 登录 -->
      <el-form
        v-if="mode === 'login'"
        ref="loginRef"
        :model="loginForm"
        :rules="loginRules"
        class="auth-form"
        label-width="58px"
        label-position="right"
      >
        <el-form-item prop="username">
          <template #label>
            <span class="f-label">账号<i class="req">*</i></span>
          </template>
          <el-input
            v-model.trim="loginForm.username"
            size="large"
            placeholder="用户名 / 学号 / 工号"
            :prefix-icon="User"
            clearable
            @keyup.enter="doLogin"
          />
        </el-form-item>
        <el-form-item prop="password">
          <template #label>
            <span class="f-label">密码<i class="req">*</i></span>
          </template>
          <el-input
            v-model="loginForm.password"
            size="large"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="doLogin"
          />
        </el-form-item>

        <div class="btn-shell">
          <el-button
            class="auth-btn"
            type="primary"
            size="large"
            :loading="loading"
            :disabled="!canLogin"
            @click="doLogin"
          >登 录</el-button>
          <!-- 禁用态仍可点击提示（禁用按钮不派发 click，用透明遮罩承接） -->
          <span v-if="!canLogin" class="btn-mask" @click="tipLoginEmpty"></span>
        </div>

        <p class="auth-switch">
          没有账号？<a @click="switchMode('register')">前往注册</a>
        </p>
      </el-form>

      <!-- 注册 -->
      <el-form
        v-else
        ref="regRef"
        :model="regForm"
        :rules="regRules"
        class="auth-form"
        label-width="116px"
        label-position="right"
      >
        <el-form-item prop="username">
          <template #label>
            <span class="f-label">用户名<i class="req">*</i></span>
          </template>
          <el-input
            v-model.trim="regForm.username"
            size="large"
            maxlength="30"
            placeholder="3-30 个字符"
            :prefix-icon="User"
            @keyup.enter="doRegister"
          />
        </el-form-item>
        <el-form-item prop="password">
          <template #label>
            <span class="f-label">密码<i class="req">*</i></span>
          </template>
          <el-input
            v-model="regForm.password"
            size="large"
            type="password"
            maxlength="32"
            placeholder="6-32 位"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="doRegister"
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <template #label>
            <span class="f-label">确认密码<i class="req">*</i></span>
          </template>
          <el-input
            v-model="regForm.confirmPassword"
            size="large"
            type="password"
            maxlength="32"
            placeholder="请再次输入密码"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="doRegister"
          />
        </el-form-item>
        <el-form-item prop="employeeNo">
          <template #label>
            <span class="f-label">学号/教职工号<i class="req">*</i></span>
          </template>
          <el-input
            v-model.trim="regForm.employeeNo"
            size="large"
            maxlength="20"
            placeholder="仅可输入数字，注册后不可重复使用"
            :prefix-icon="Postcard"
            @input="val => (regForm.employeeNo = val.replace(/\D/g, ''))"
            @keyup.enter="doRegister"
          />
        </el-form-item>
        <el-form-item prop="realName">
          <template #label>
            <span class="f-label">姓名<i class="req">*</i></span>
          </template>
          <el-input
            v-model.trim="regForm.realName"
            size="large"
            placeholder="请输入真实姓名"
            :prefix-icon="Avatar"
            @keyup.enter="doRegister"
          />
        </el-form-item>

        <el-form-item prop="gender">
          <template #label>
            <span class="f-label">性别<i class="opt">选填</i></span>
          </template>
          <el-radio-group v-model="regForm.gender" size="large">
            <el-radio-button value="男">男</el-radio-button>
            <el-radio-button value="女">女</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item prop="role" class="role-item">
          <template #label>
            <span class="f-label">角色<i class="req">*</i></span>
          </template>
          <el-select v-model="regForm.role" size="large" placeholder="请选择注册角色" style="width: 100%">
            <el-option v-for="r in ROLE_OPTIONS" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>

        <el-form-item prop="captchaCode">
          <template #label>
            <span class="f-label">验证码<i class="req">*</i></span>
          </template>
          <div class="captcha-row">
            <el-input
              v-model.trim="regForm.captchaCode"
              size="large"
              maxlength="4"
              placeholder="请输入图中字符"
              @keyup.enter="doRegister"
            />
            <img
              v-if="captcha.image"
              :src="captcha.image"
              class="captcha-img"
              alt="图形验证码，点击刷新"
              title="看不清？点击刷新"
              @click="refreshCaptcha"
            />
            <span v-else class="captcha-img captcha-loading" @click="refreshCaptcha">加载中…</span>
          </div>
        </el-form-item>

        <p class="role-hint">学号/教职工号仅可注册一次；选择学生可直接注册生效；教师、安全员、设备保管员、管理员账号需要管理员后台审核通过后方可启用。</p>

        <div class="btn-shell">
          <el-button
            class="auth-btn"
            type="primary"
            size="large"
            :loading="loading"
            :disabled="!canRegister"
            @click="doRegister"
          >注 册</el-button>
          <span v-if="!canRegister" class="btn-mask" @click="tipRegisterEmpty"></span>
        </div>

        <p class="auth-switch">
          已有账号？<a @click="switchMode('login')">返回登录</a>
        </p>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, Lock, Avatar, Postcard } from '@element-plus/icons-vue'
import { login, register, authCaptcha } from '../api'
import { useUserStore } from '../store/user'

const router = useRouter()
const store = useUserStore()

const mode = ref('login')
const loading = ref(false)
const loginRef = ref()
const regRef = ref()

const loginForm = reactive({ username: '', password: '' })
// 注册角色选项：五类均可申请；学生直接生效，其余需管理员审核
const ROLE_OPTIONS = [
  { label: '学生', value: 'STUDENT' },
  { label: '教师', value: 'TEACHER' },
  { label: '安全员', value: 'SAFETY' },
  { label: '设备保管员', value: 'KEEPER' },
  { label: '管理员', value: 'ADMIN' }
]

// 注册表单：角色必选，编号仅数字且全局唯一
const regForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  employeeNo: '',
  realName: '',
  gender: '',
  role: '',
  captchaCode: ''
})

/* ---------------- 图形验证码 ---------------- */
const captcha = ref({ captchaId: '', image: '' })
async function refreshCaptcha() {
  try {
    captcha.value = await authCaptcha()
  } catch {
    captcha.value = { captchaId: '', image: '' }
  }
}
// 每次切到注册页签都换一张新验证码
watch(mode, m => { if (m === 'register') refreshCaptcha() })

/* ---------------- 按钮可用性 ---------------- */
const canLogin = computed(() => !!loginForm.username.trim() && !!loginForm.password)
const canRegister = computed(() =>
  !!(regForm.username.trim() &&
    regForm.password &&
    regForm.confirmPassword &&
    regForm.employeeNo.trim() &&
    regForm.realName.trim() &&
    regForm.role &&
    regForm.captchaCode)
)

/* ---------------- 校验规则 ---------------- */
const loginRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const validateConfirm = (rule, value, callback) => {
  if (!value) return callback(new Error('请再次输入密码'))
  if (value !== regForm.password) return callback(new Error('两次输入的密码不一致，请重新确认'))
  callback()
}

const regRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 30, message: '用户名长度为 3-30 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6-32 位', trigger: 'blur' }
  ],
  confirmPassword: [{ required: true, validator: validateConfirm, trigger: 'blur' }],
  employeeNo: [
    { required: true, message: '请输入学号 / 教职工号', trigger: 'blur' },
    { pattern: /^\d{3,20}$/, message: '学号 / 工号须为 3-20 位数字', trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择注册角色', trigger: 'change' }],
  captchaCode: [
    { required: true, message: '请输入图形验证码', trigger: 'blur' },
    { min: 4, max: 4, message: '验证码为 4 位字符', trigger: 'blur' }
  ]
}

// 修改密码后，若确认密码已填则联动复校
watch(
  () => regForm.password,
  () => {
    if (regForm.confirmPassword) regRef.value?.validateField('confirmPassword').catch(() => {})
  }
)

/* ---------------- 提示 ---------------- */
function softTip(message) {
  ElMessage({ message, type: 'warning', duration: 2200, showClose: false, grouping: true, customClass: 'auth-tip' })
}

function tipLoginEmpty() {
  softTip('请填写账号和密码')
}

function tipRegisterEmpty() {
  const missing = []
  if (!regForm.username.trim()) missing.push('用户名')
  if (!regForm.password) missing.push('密码')
  if (!regForm.confirmPassword) missing.push('确认密码')
  if (!regForm.employeeNo.trim()) missing.push('学号/教职工号')
  if (!regForm.realName.trim()) missing.push('姓名')
  if (!regForm.role) missing.push('角色')
  if (!regForm.captchaCode) missing.push('验证码')
  if (missing.length === 1 && missing[0] === '角色') {
    softTip('请选择注册角色')
    return
  }
  softTip(`请完善必填信息：${missing.join('、')}`)
}

/* ---------------- 交互 ---------------- */
function switchMode(target) {
  mode.value = target
  loading.value = false
  nextTick(() => {
    if (target === 'login') loginRef.value?.clearValidate()
    else regRef.value?.clearValidate()
  })
}

async function doLogin() {
  if (!canLogin.value) {
    tipLoginEmpty()
    return
  }
  if (!loginRef.value) return
  try {
    await loginRef.value.validate()
  } catch {
    return
  }
  // 一个浏览器同一时间只能登录一个账号：已存在登录态且登录的是其他账号时，先二次确认
  if (hasOtherAccountLoggedIn()) {
    try {
      await ElMessageBox.confirm(
        '该浏览器已登录其他账号，继续登录会退出原有账号，是否确认？',
        '切换账号确认',
        {
          confirmButtonText: '确认登录',
          cancelButtonText: '取消',
          type: 'warning',
          customClass: 'switch-account-box',
          closeOnClickModal: false,
          distinguishCancelAndClose: true
        }
      )
    } catch {
      return // 取消 / 关闭：停留在登录页，不做登录、不改动原有登录态
    }
  }
  loading.value = true
  try {
    const data = await login(loginForm)
    store.setLogin(data.token, data.user)
    ElMessage({
      message: '登录成功，正在进入系统...',
      type: 'success',
      duration: 1200,
      showClose: false
    })
    setTimeout(() => router.push('/dashboard'), 700)
  } finally {
    loading.value = false
  }
}

/** 是否已有其他账号处于登录状态（token 存 localStorage，同浏览器多标签共享） */
function hasOtherAccountLoggedIn() {
  const token = store.token || localStorage.getItem('token')
  if (!token) return false
  const current = store.user || JSON.parse(localStorage.getItem('user') || 'null')
  // 同一账号重复登录不打扰；未取到当前账号信息时按“已登录其他账号”处理
  return !current?.username || current.username !== loginForm.username.trim()
}

async function doRegister() {
  if (!canRegister.value) {
    tipRegisterEmpty()
    return
  }
  if (!regRef.value) return
  try {
    await regRef.value.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    await register({
      username: regForm.username,
      password: regForm.password,
      employeeNo: regForm.employeeNo,
      realName: regForm.realName,
      gender: regForm.gender || undefined,
      role: regForm.role,
      captchaId: captcha.value.captchaId,
      captchaCode: regForm.captchaCode
    })
    // 弹窗按角色区分：学生直接生效，其余角色待审核
    ElMessage.success(regForm.role === 'STUDENT'
      ? '注册成功，学生权限已生效！'
      : '注册提交成功，请等待系统管理员审核你的角色申请。')
    loginForm.username = regForm.username
    Object.assign(regForm, {
      username: '',
      password: '',
      confirmPassword: '',
      employeeNo: '',
      realName: '',
      gender: '',
      role: '',
      captchaCode: ''
    })
    switchMode('login')
  } catch {
    // 验证码一次性使用：无论何种失败（验证码错误 / 用户名重复等），刷新验证码让用户重试
    regForm.captchaCode = ''
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* 页面背景：儿童科学插画（左下小男孩 / 右下小女孩），中央留白放卡片 */
.auth-bg {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  background-color: #f1f5f6;
  background-image: url('/images/login-bg.png');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  background-attachment: fixed;
}

/* 居中白色圆角卡片 */
.auth-card {
  width: 460px;
  max-height: calc(100vh - 56px);
  overflow-y: auto;
  background: #ffffff;
  border: 1px solid #eaf0f3;
  border-radius: 20px;
  padding: 40px 40px 28px;
  box-shadow: 0 4px 16px rgba(150, 165, 175, 0.1);
}

.auth-head { text-align: center; }
.auth-title {
  font-size: 21px;
  font-weight: 700;
  color: #2b2b2b;
  letter-spacing: 0.8px;
}
.auth-subtitle {
  margin-top: 10px;
  font-size: 13px;
  font-weight: 500;
  color: #666666;
  letter-spacing: 1.2px;
  text-transform: uppercase;
}

.auth-form { margin-top: 26px; }
.auth-form :deep(.el-form-item) { margin-bottom: 19px; }

/* ---------- 标签：柔和字重，星号在标签右侧 ---------- */
.auth-form :deep(.el-form-item__label) {
  padding-right: 10px;
  font-weight: 400;
}
/* 隐藏 Element 默认必填星号（左侧），统一用自绘右侧星号 */
.auth-form :deep(.el-form-item.is-required .el-form-item__label::before) {
  content: none;
  display: none;
}
.f-label {
  font-size: 13.5px;
  font-weight: 500;
  color: #4a555c;
  letter-spacing: 0.3px;
  line-height: 1.5;
}
.req {
  font-style: normal;
  font-size: 13px;
  font-weight: 600;
  color: #c09a9a;
  margin-left: 3px;
  line-height: 1;
}
.opt {
  font-style: normal;
  font-size: 12px;
  color: #9aa8b0;
  margin-left: 3px;
  line-height: 1;
}

/* ---------- 错误提示：莫兰迪浅红，字号柔和 ---------- */
.auth-form :deep(.el-form-item__error) {
  color: #b98c8c;
  font-size: 12.5px;
  font-weight: 400;
  padding-top: 4px;
  letter-spacing: 0.2px;
}
.auth-form :deep(.el-form-item.is-error .el-input__wrapper) {
  box-shadow: 0 0 0 1px #d6b3b3 inset;
}
.auth-form :deep(.el-form-item.is-error .el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #d6b3b3 inset, 0 0 0 3px rgba(185, 140, 140, 0.1);
}

/* ---------- 按钮 ---------- */
.btn-shell { position: relative; }
.auth-btn {
  width: 100%;
  height: 44px;
  margin-top: 4px;
  font-size: 15px;
  letter-spacing: 6px;
  text-indent: 6px;
  border-radius: 10px;
}
.btn-mask {
  position: absolute;
  inset: 0;
  top: 4px;
  cursor: not-allowed;
  border-radius: 10px;
}

/* 底部切换文案 */
.auth-switch {
  margin-top: 18px;
  text-align: center;
  font-size: 13.5px;
  color: #666666;
}
.auth-switch a {
  color: #4a6c80;
  font-weight: 600;
  cursor: pointer;
  margin-left: 4px;
  transition: color 0.2s ease;
}
.auth-switch a:hover { color: #38566a; text-decoration: underline; }

/* ---------- 输入框 placeholder：加深一档，保证可读 ---------- */
.auth-form :deep(.el-input__wrapper input::placeholder) {
  color: #7c848c;
}

/* 角色下拉 */
.role-item :deep(.el-select__wrapper) {
  border-radius: 8px;
}

/* 图形验证码：输入框与图片同行 */
.captcha-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.captcha-row .el-input {
  flex: 1;
}
.captcha-img {
  width: 130px;
  height: 44px;
  flex: none;
  border-radius: 8px;
  border: 1px solid #d9d4cd;
  cursor: pointer;
  display: block;
  transition: border-color 0.2s;
}
.captcha-img:hover {
  border-color: #8fa9bc;
}
.captcha-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #9a9a9a;
  background: #f1efe8;
}

/* 注册说明 */
.role-hint {
  margin: -6px 0 16px 116px;
  font-size: 12.5px;
  line-height: 1.65;
  color: #4f5860;
  font-weight: 500;
  letter-spacing: 0.2px;
}
</style>

<!-- 切换账号确认弹窗（teleport 到 body，需非 scoped 样式）：莫兰迪浅色 -->
<style>
.switch-account-box {
  width: 420px;
  border-radius: 14px;
  border: 1px solid #eaf0f3;
  background: #ffffff;
  box-shadow: 0 18px 48px rgba(130, 145, 155, 0.18);
  padding-bottom: 6px;
}
.switch-account-box .el-message-box__header { padding: 20px 22px 6px; }
.switch-account-box .el-message-box__title {
  font-size: 15.5px;
  font-weight: 700;
  color: #333333;
  letter-spacing: 0.3px;
}
.switch-account-box .el-message-box__headerbtn { top: 16px; right: 14px; }
.switch-account-box .el-message-box__content { padding: 4px 22px 16px; }
.switch-account-box .el-message-box__message {
  font-size: 13.5px;
  color: #4a555c;
  line-height: 1.75;
}
/* 图标改莫兰迪暖灰，不刺眼 */
.switch-account-box .el-message-box__status.el-icon { color: #c9b39a; }
.switch-account-box .el-message-box__btns { padding: 0 22px 14px; gap: 10px; }
.switch-account-box .el-message-box__btns .el-button {
  min-width: 92px;
  border-radius: 8px;
  font-size: 13px;
}
.switch-account-box .el-message-box__btns .el-button--default {
  color: #5c6b74;
  border-color: #dde6ea;
  background: #fbfcfd;
}
.switch-account-box .el-message-box__btns .el-button--default:hover {
  color: #46545d;
  border-color: #c3d4dd;
  background: #f4f8fa;
}
.switch-account-box .el-message-box__btns .el-button--primary {
  background: #8fa9bc;
  border-color: #8fa9bc;
  letter-spacing: 0.5px;
}
.switch-account-box .el-message-box__btns .el-button--primary:hover {
  background: #7d9aae;
  border-color: #7d9aae;
}
</style>
