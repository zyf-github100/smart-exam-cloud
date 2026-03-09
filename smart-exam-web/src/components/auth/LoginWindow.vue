<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { api, getApiBase, setApiBase, setSavedUser, setToken } from '../../api/client'
import { getDefaultAccessiblePath } from '../../composables/accessControl'
import { useAsyncAction } from '../../composables/useAsyncAction'

const route = useRoute()
const router = useRouter()
const { loading, run } = useAsyncAction()

const authForm = reactive({
  username: 'student001',
  password: '123456',
})

const apiBaseInput = ref(getApiBase())
const currentApiBase = ref(getApiBase())
const assistantMode = ref('idle')
const mascotRef = ref(null)
const particles = ref([])

const pointerState = reactive({
  eyeX: 0,
  eyeY: 0,
  tiltX: 0,
  tiltY: 0,
  sparkleX: 50,
  sparkleY: 24,
  hover: false,
  pressed: false,
  waving: false,
  gesture: 'none',
})

const basePresets = [
  { label: '网关代理', value: '/api/v1' },
  { label: '本地直连', value: 'http://localhost:9000/api/v1' },
]

const errorSuggestions = [
  '确认用户名和密码是否正确。',
  '确认接口地址可访问（网关或本地直连）。',
  '确认认证服务是否已经启动。',
]

const assistantMessageMap = {
  idle: '你好，我是登录小助手。',
  watch: '我在看着你，用户名别输错。',
  password: '你输入密码时，我先捂住眼睛。',
  config: '接口地址已就位，可以继续登录。',
  loading: '正在登录，马上带你进入系统。',
  success: '登录成功，正在为你打开控制台。',
  error: '登录失败了，我把排查建议放在下面。',
  hover: '鼠标移过来了，我会跟着你看。',
  tap: '收到点击，向你打个招呼。',
  easter: '彩蛋触发：今天也要稳定上线。',
  heart: '长按触发比心模式。',
  salute: '长按触发敬礼模式。',
}
let assistantTimer = null
let waveTimer = null
let gestureTimer = null
let clickTimer = null
let longPressTimer = null
let particleTimer = null
let longPressTriggered = false
let gestureToggle = false
let clickBurst = 0

const redirectPath = computed(() => {
  const raw = String(route.query.redirect || '').trim()
  if (!raw || raw === '/login') return ''
  return raw.startsWith('/') ? raw : ''
})

const assistantMessage = computed(() => assistantMessageMap[assistantMode.value] || assistantMessageMap.idle)
const showErrorSuggestions = computed(() => assistantMode.value === 'error')

const mascotClass = computed(() => ({
  'is-looking': ['watch', 'loading', 'success', 'config', 'hover', 'tap', 'easter'].includes(assistantMode.value),
  'is-password': assistantMode.value === 'password',
  'is-loading': assistantMode.value === 'loading',
  'is-success': assistantMode.value === 'success',
  'is-error': assistantMode.value === 'error',
  'is-config': assistantMode.value === 'config',
  'is-hover': pointerState.hover,
  'is-pressed': pointerState.pressed,
  'is-wave': pointerState.waving,
  'is-heart': pointerState.gesture === 'heart',
  'is-salute': pointerState.gesture === 'salute',
  'is-easter': assistantMode.value === 'easter',
}))

const mascotVars = computed(() => {
  const baseEyeX = ['watch', 'config', 'loading', 'success', 'hover', 'tap', 'easter'].includes(assistantMode.value)
    ? 1.8
    : 0
  return {
    '--eye-x': `${baseEyeX + pointerState.eyeX}px`,
    '--eye-y': `${pointerState.eyeY}px`,
    '--head-rx': `${pointerState.tiltX}deg`,
    '--head-ry': `${pointerState.tiltY}deg`,
    '--sparkle-x': `${pointerState.sparkleX}%`,
    '--sparkle-y': `${pointerState.sparkleY}%`,
  }
})

const clamp = (value, min, max) => Math.min(max, Math.max(min, value))

const clearTimer = (timerRef) => {
  if (timerRef) {
    window.clearTimeout(timerRef)
  }
}

const clearAllTransientTimers = () => {
  clearTimer(assistantTimer)
  clearTimer(waveTimer)
  clearTimer(gestureTimer)
  clearTimer(clickTimer)
  clearTimer(longPressTimer)
  clearTimer(particleTimer)
  assistantTimer = null
  waveTimer = null
  gestureTimer = null
  clickTimer = null
  longPressTimer = null
  particleTimer = null
}

const resetPointerMotion = () => {
  pointerState.eyeX = 0
  pointerState.eyeY = 0
  pointerState.tiltX = 0
  pointerState.tiltY = 0
  pointerState.sparkleX = 50
  pointerState.sparkleY = 24
}

const canInteractiveOverride = () => !loading.login && assistantMode.value !== 'success'

const setAssistantMode = (mode, resetMs = 0) => {
  clearTimer(assistantTimer)
  assistantTimer = null
  assistantMode.value = mode
  if (resetMs > 0) {
    assistantTimer = window.setTimeout(() => {
      assistantMode.value = pointerState.hover ? 'hover' : 'idle'
      assistantTimer = null
    }, resetMs)
  }
}

const setGesture = (gestureName, lifeMs = 1400) => {
  pointerState.gesture = gestureName
  clearTimer(gestureTimer)
  gestureTimer = window.setTimeout(() => {
    pointerState.gesture = 'none'
    gestureTimer = null
  }, lifeMs)
}

const startWave = (duration = 900) => {
  pointerState.waving = true
  clearTimer(waveTimer)
  waveTimer = window.setTimeout(() => {
    pointerState.waving = false
    waveTimer = null
  }, duration)
}

const particleStyle = (particle) => ({
  left: `${particle.left}%`,
  top: `${particle.top}%`,
  width: `${particle.size}px`,
  height: `${particle.size}px`,
  background: particle.color,
  borderRadius: particle.shape === 'star' ? '2px' : '999px',
  '--dx': `${particle.dx}px`,
  '--dy': `${particle.dy}px`,
  '--rot': `${particle.rot}deg`,
  '--dur': `${particle.dur}ms`,
  '--delay': `${particle.delay}ms`,
})

const launchSuccessParticles = () => {
  const colors = ['#ff78b8', '#ffd36e', '#7cd8ff', '#9f8bff', '#83e8c1']
  particles.value = Array.from({ length: 24 }, (_, index) => ({
    id: `${Date.now()}-${index}`,
    left: 48 + Math.random() * 8,
    top: 44 + Math.random() * 8,
    dx: -95 + Math.random() * 190,
    dy: 28 + Math.random() * 95,
    rot: -220 + Math.random() * 440,
    dur: 760 + Math.floor(Math.random() * 420),
    delay: Math.floor(Math.random() * 140),
    size: 5 + Math.floor(Math.random() * 6),
    color: colors[Math.floor(Math.random() * colors.length)],
    shape: Math.random() > 0.6 ? 'star' : 'dot',
  }))

  clearTimer(particleTimer)
  particleTimer = window.setTimeout(() => {
    particles.value = []
    particleTimer = null
  }, 1400)
}

const updatePointerMotion = (event) => {
  if (!mascotRef.value) return
  const rect = mascotRef.value.getBoundingClientRect()
  const centerX = rect.left + rect.width / 2
  const centerY = rect.top + rect.height / 2
  const dx = clamp((event.clientX - centerX) / (rect.width / 2), -1, 1)
  const dy = clamp((event.clientY - centerY) / (rect.height / 2), -1, 1)

  pointerState.eyeX = Math.round(dx * 4)
  pointerState.eyeY = Math.round(dy * 3)
  pointerState.tiltX = Math.round(-dy * 6 * 10) / 10
  pointerState.tiltY = Math.round(dx * 9 * 10) / 10
  pointerState.sparkleX = Math.round(clamp(((event.clientX - rect.left) / rect.width) * 100, 8, 92))
  pointerState.sparkleY = Math.round(clamp(((event.clientY - rect.top) / rect.height) * 100, 8, 92))
}

const handleWindowMouseMove = (event) => {
  updatePointerMotion(event)
}

const handleWindowMouseLeave = () => {
  resetPointerMotion()
}

const handleMascotEnter = () => {
  pointerState.hover = true
  if (canInteractiveOverride() && assistantMode.value !== 'password') {
    setAssistantMode('hover')
  }
}

const handleMascotLeave = () => {
  pointerState.hover = false
  pointerState.pressed = false
  longPressTriggered = false
  clearTimer(longPressTimer)
  longPressTimer = null
  if (canInteractiveOverride() && assistantMode.value !== 'password') {
    setAssistantMode('idle')
  }
}

const handleMascotDown = () => {
  pointerState.pressed = true
  if (!canInteractiveOverride()) return

  longPressTriggered = false
  clearTimer(longPressTimer)
  longPressTimer = window.setTimeout(() => {
    longPressTriggered = true
    gestureToggle = !gestureToggle
    const mode = gestureToggle ? 'heart' : 'salute'
    setGesture(mode, 1500)
    setAssistantMode(mode, 1700)
  }, 1000)
}

const handleMascotUp = () => {
  pointerState.pressed = false
  clearTimer(longPressTimer)
  longPressTimer = null
}

const handleMascotClick = () => {
  if (!canInteractiveOverride()) return

  if (longPressTriggered) {
    longPressTriggered = false
    return
  }

  clickBurst += 1
  clearTimer(clickTimer)

  if (clickBurst >= 3) {
    clickBurst = 0
    startWave(1200)
    setGesture('heart', 1400)
    setAssistantMode('easter', 2200)
    return
  }

  clickTimer = window.setTimeout(() => {
    clickBurst = 0
    clickTimer = null
  }, 800)

  startWave()
  setAssistantMode('tap', 1200)
}

const handleInputFocus = (field) => {
  if (loading.login) return
  if (field === 'password') {
    setAssistantMode('password')
    return
  }
  if (field === 'api') {
    setAssistantMode('config')
    return
  }
  setAssistantMode('watch')
}

const handleInputBlur = () => {
  if (loading.login) return
  if (pointerState.hover) {
    setAssistantMode('hover')
    return
  }
  setAssistantMode('idle')
}

const applyBase = () => {
  setApiBase(apiBaseInput.value)
  currentApiBase.value = getApiBase()
  ElMessage.success('接口地址已更新')
  setAssistantMode('config', 1500)
}

const usePreset = (preset) => {
  apiBaseInput.value = preset
  applyBase()
}

const login = async () => {
  setAssistantMode('loading')
  const data = await run('login', () => api.login(authForm), { successMessage: '登录成功' })

  if (!data) {
    setAssistantMode('error')
    return
  }

  setAssistantMode('success')
  launchSuccessParticles()

  setToken(data.token)
  setSavedUser(data.user)
  const nextPath = redirectPath.value || getDefaultAccessiblePath(data.user)

  await new Promise((resolve) => {
    window.setTimeout(resolve, 900)
  })

  await router.replace(nextPath)
}

onMounted(() => {
  window.addEventListener('mousemove', handleWindowMouseMove)
  window.addEventListener('mouseleave', handleWindowMouseLeave)
})

onBeforeUnmount(() => {
  clearAllTransientTimers()
  window.removeEventListener('mousemove', handleWindowMouseMove)
  window.removeEventListener('mouseleave', handleWindowMouseLeave)
})
</script>

<template>
  <div class="login-stage">
    <div class="anime-overlay"></div>
    <div class="login-orb orb-a"></div>
    <div class="login-orb orb-b"></div>

    <section class="login-card reveal-up">
      <div class="login-layout">
        <aside class="assistant-panel">
          <p class="assistant-kicker">互动助手</p>
          <p class="assistant-bubble">{{ assistantMessage }}</p>

          <ul v-if="showErrorSuggestions" class="assistant-suggest">
            <li v-for="tip in errorSuggestions" :key="tip">{{ tip }}</li>
          </ul>

          <div
            ref="mascotRef"
            class="assistant-mascot"
            :class="mascotClass"
            :style="mascotVars"
            @mouseenter="handleMascotEnter"
            @mouseleave="handleMascotLeave"
            @mousedown.prevent="handleMascotDown"
            @mouseup="handleMascotUp"
            @click="handleMascotClick"
          >
            <div class="mascot-shadow"></div>

            <div class="mascot-head">
              <div class="mascot-eyes">
                <span class="eye"></span>
                <span class="eye"></span>
              </div>
              <span class="mascot-mouth"></span>
              <span class="mascot-hand hand-left"></span>
              <span class="mascot-hand hand-right"></span>
              <span class="privacy-mask"></span>
            </div>

            <div class="mascot-body">
              <span class="mascot-heart">AI</span>
            </div>

            <span class="gesture-icon gesture-heart" aria-hidden="true">❤</span>
            <span class="gesture-icon gesture-salute" aria-hidden="true">★</span>

            <div class="particle-layer" aria-hidden="true">
              <span
                v-for="particle in particles"
                :key="particle.id"
                class="particle"
                :style="particleStyle(particle)"
              ></span>
            </div>
          </div>

          <p class="assistant-tip">可玩交互：移动鼠标跟随、单击挥手、三连击彩蛋、长按 1 秒触发比心/敬礼。</p>
        </aside>

        <div class="login-main">
          <header class="login-head">
            <p class="kicker">智慧考试云</p>
            <h1>登录</h1>
            <p class="sub">使用独立登录窗口，登录后将自动跳转到你有权限的模块。</p>
          </header>

          <el-form label-position="top" @submit.prevent>
            <el-form-item label="接口地址">
              <el-input
                v-model="apiBaseInput"
                placeholder="/api/v1 或 http://localhost:9000/api/v1"
                @focus="handleInputFocus('api')"
                @blur="handleInputBlur"
              >
                <template #append>
                  <el-button @click="applyBase">应用</el-button>
                </template>
              </el-input>
              <div class="preset-row">
                <el-button v-for="preset in basePresets" :key="preset.value" text @click="usePreset(preset.value)">
                  {{ preset.label }}
                </el-button>
              </div>
              <p class="hint-text">当前地址：{{ currentApiBase }}</p>
            </el-form-item>

            <div class="form-grid cols-2">
              <el-form-item label="用户名">
                <el-input
                  v-model="authForm.username"
                  autocomplete="username"
                  @focus="handleInputFocus('username')"
                  @blur="handleInputBlur"
                />
              </el-form-item>
              <el-form-item label="密码">
                <el-input
                  v-model="authForm.password"
                  type="password"
                  show-password
                  autocomplete="current-password"
                  @focus="handleInputFocus('password')"
                  @blur="handleInputBlur"
                  @keyup.enter="login"
                />
              </el-form-item>
            </div>

            <div class="action-row">
              <el-button type="primary" :loading="loading.login" @click="login">登录</el-button>
            </div>
          </el-form>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-stage {
  position: relative;
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 18px;
  overflow: hidden;
  background:
    radial-gradient(900px 560px at 16% 12%, rgba(255, 170, 220, 0.35), transparent 62%),
    radial-gradient(860px 520px at 88% 10%, rgba(255, 203, 232, 0.3), transparent 64%),
    linear-gradient(180deg, #fff3fa 0%, #ffeef8 48%, #ffe7f4 100%);
}

.anime-overlay {
  position: fixed;
  inset: 0;
  z-index: -3;
  background:
    radial-gradient(760px 420px at 50% 28%, rgba(255, 219, 237, 0.3), transparent 70%),
    linear-gradient(180deg, rgba(115, 43, 88, 0.24) 0%, rgba(96, 34, 72, 0.35) 100%);
}

.login-orb {
  position: fixed;
  z-index: -1;
  border-radius: 50%;
  filter: blur(56px);
  pointer-events: none;
}

.orb-a {
  width: 280px;
  height: 280px;
  left: -70px;
  top: -50px;
  background: rgba(255, 151, 203, 0.66);
}

.orb-b {
  width: 240px;
  height: 240px;
  right: -60px;
  bottom: -40px;
  background: rgba(255, 114, 175, 0.58);
}

.login-card {
  width: min(900px, 100%);
  border-radius: 20px;
  border: 1px solid rgba(255, 178, 216, 0.82);
  background: rgba(255, 245, 252, 0.9);
  backdrop-filter: blur(10px);
  box-shadow: 0 18px 36px rgba(154, 65, 118, 0.22);
  padding: clamp(14px, 2.2vw, 20px);
}

.login-layout {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
}

.assistant-panel {
  border-radius: 14px;
  border: 1px solid rgba(194, 207, 228, 0.72);
  background: rgba(255, 255, 255, 0.74);
  backdrop-filter: blur(6px);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.assistant-kicker {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #5f6f8c;
}

.assistant-bubble {
  margin: 0;
  min-height: 44px;
  border-radius: 12px;
  border: 1px solid rgba(191, 204, 224, 0.9);
  background: rgba(247, 251, 255, 0.88);
  color: #43536d;
  font-size: 12px;
  line-height: 1.45;
  padding: 8px 10px;
}

.assistant-suggest {
  margin: 0;
  padding: 8px 12px;
  border-radius: 12px;
  border: 1px dashed rgba(208, 118, 145, 0.72);
  background: rgba(255, 248, 251, 0.78);
  color: #7d4b63;
  font-size: 12px;
  line-height: 1.45;
}

.assistant-suggest li + li {
  margin-top: 4px;
}

.assistant-tip {
  margin: 0;
  color: #5d6c86;
  font-size: 12px;
  line-height: 1.4;
}

.assistant-mascot {
  --eye-x: 0px;
  --eye-y: 0px;
  --head-rx: 0deg;
  --head-ry: 0deg;
  --sparkle-x: 50%;
  --sparkle-y: 24%;
  position: relative;
  height: 166px;
  display: grid;
  place-items: center;
  animation: mascotFloat 3.6s ease-in-out infinite;
  cursor: pointer;
  user-select: none;
  touch-action: manipulation;
}

.mascot-shadow {
  position: absolute;
  bottom: 12px;
  width: 84px;
  height: 14px;
  border-radius: 999px;
  background: rgba(165, 96, 131, 0.24);
  filter: blur(4px);
  transition: width 0.25s ease, opacity 0.25s ease;
}

.mascot-head {
  position: relative;
  width: 94px;
  height: 90px;
  border-radius: 44px 44px 36px 36px;
  border: 2px solid rgba(219, 134, 184, 0.84);
  background: linear-gradient(160deg, #fffefd 0%, #ffe8f5 88%);
  box-shadow: inset 0 -8px 14px rgba(255, 196, 225, 0.48);
  transform: perspective(220px) rotateX(var(--head-rx)) rotateY(var(--head-ry));
  transition: transform 0.14s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.mascot-head::after {
  content: '';
  position: absolute;
  inset: 10px 12px 34px;
  border-radius: 999px;
  background: radial-gradient(circle at var(--sparkle-x) var(--sparkle-y), rgba(255, 255, 255, 0.9), transparent 44%);
  pointer-events: none;
}

.mascot-eyes {
  position: absolute;
  top: 33px;
  left: 25px;
  display: flex;
  gap: 22px;
  transform: translate(var(--eye-x), var(--eye-y));
  transition: transform 0.12s ease, opacity 0.2s ease;
}

.eye {
  width: 10px;
  height: 12px;
  border-radius: 50%;
  background: #7b3d5f;
  position: relative;
}

.eye::after {
  content: '';
  position: absolute;
  top: 1px;
  right: 1px;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.92);
}

.mascot-mouth {
  position: absolute;
  left: 37px;
  top: 59px;
  width: 20px;
  height: 8px;
  border-bottom: 3px solid #a44d7a;
  border-radius: 0 0 12px 12px;
  transition: all 0.22s ease;
}

.mascot-hand {
  position: absolute;
  top: 58px;
  width: 20px;
  height: 13px;
  border-radius: 9px;
  border: 2px solid rgba(219, 134, 184, 0.84);
  background: #ffe4f2;
  opacity: 0;
  transition: transform 0.22s ease, opacity 0.22s ease;
}

.hand-left {
  left: -9px;
  transform: translate(0, 10px) rotate(-24deg);
}

.hand-right {
  right: -9px;
  transform: translate(0, 10px) rotate(24deg);
}

.privacy-mask {
  position: absolute;
  left: 17px;
  top: 30px;
  width: 60px;
  height: 18px;
  border-radius: 999px;
  background: rgba(119, 86, 149, 0.34);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.mascot-body {
  margin-top: -8px;
  width: 72px;
  height: 58px;
  border-radius: 22px;
  border: 2px solid rgba(206, 116, 170, 0.8);
  background: linear-gradient(170deg, #ff94c7, #ff75b6);
  box-shadow: 0 8px 14px rgba(201, 91, 149, 0.28);
  display: grid;
  place-items: center;
  transition: transform 0.15s ease;
}

.mascot-heart {
  font-size: 10px;
  letter-spacing: 0.08em;
  color: rgba(255, 255, 255, 0.93);
  font-weight: 700;
}

.gesture-icon {
  position: absolute;
  top: 14px;
  left: 148px;
  font-size: 22px;
  line-height: 1;
  opacity: 0;
  transform: scale(0.5) translateY(8px);
}

.gesture-heart {
  color: #ff79ba;
}

.gesture-salute {
  color: #ffd96a;
}

.particle-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.particle {
  position: absolute;
  opacity: 0;
  transform: translate(-50%, -50%) scale(0.5);
  animation: confettiBurst var(--dur) cubic-bezier(0.18, 0.76, 0.4, 1) var(--delay) forwards;
}

.assistant-mascot.is-hover .mascot-head {
  border-color: rgba(204, 102, 161, 0.9);
  box-shadow:
    0 6px 14px rgba(197, 93, 151, 0.2),
    inset 0 -8px 14px rgba(255, 196, 225, 0.48);
}

.assistant-mascot.is-hover .mascot-shadow {
  width: 96px;
  opacity: 0.82;
}

.assistant-mascot.is-pressed .mascot-head {
  transform: perspective(220px) rotateX(calc(var(--head-rx) + 2deg)) rotateY(var(--head-ry)) scale(0.98);
}

.assistant-mascot.is-pressed .mascot-body {
  transform: scale(0.96);
}

.assistant-mascot.is-password .mascot-hand {
  opacity: 1;
}

.assistant-mascot.is-password .hand-left {
  transform: translate(10px, -16px) rotate(16deg);
}

.assistant-mascot.is-password .hand-right {
  transform: translate(-10px, -16px) rotate(-16deg);
}

.assistant-mascot.is-password .mascot-eyes {
  opacity: 0.08;
}

.assistant-mascot.is-password .privacy-mask {
  opacity: 1;
}

.assistant-mascot.is-loading .eye {
  animation: eyeBlink 0.8s ease-in-out infinite;
}

.assistant-mascot.is-success .mascot-mouth {
  width: 24px;
  left: 35px;
  border-bottom-color: #38a36d;
}

.assistant-mascot.is-config .mascot-mouth {
  width: 20px;
  border-bottom-color: #5a5fc9;
}

.assistant-mascot.is-error {
  animation: mascotShake 0.35s linear 2;
}

.assistant-mascot.is-error .mascot-mouth {
  top: 62px;
  height: 7px;
  border-bottom: 0;
  border-top: 3px solid #d35a7f;
  border-radius: 12px 12px 0 0;
}

.assistant-mascot.is-wave .hand-right {
  opacity: 1;
  transform: translate(-9px, -18px) rotate(-10deg);
  animation: handWave 0.45s ease-in-out 2;
}

.assistant-mascot.is-wave .hand-left {
  opacity: 1;
  transform: translate(7px, -12px) rotate(12deg);
}

.assistant-mascot.is-heart .gesture-heart,
.assistant-mascot.is-salute .gesture-salute {
  opacity: 1;
  animation: gesturePop 0.8s ease;
}

.assistant-mascot.is-easter .mascot-body {
  box-shadow:
    0 8px 14px rgba(201, 91, 149, 0.28),
    0 0 0 3px rgba(255, 165, 210, 0.32);
}

.login-main {
  min-width: 0;
}

.login-head {
  margin-bottom: 8px;
}

.login-head .kicker {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.14em;
  color: #c75b96;
  text-transform: uppercase;
}

.login-head h1 {
  margin: 6px 0 4px;
  font-family: 'ZCOOL XiaoWei', 'M PLUS Rounded 1c', serif;
  font-size: clamp(30px, 4vw, 38px);
  line-height: 1.02;
  color: #9f3d70;
  text-shadow: 0 4px 10px rgba(255, 211, 235, 0.7);
}

.login-head .sub {
  margin: 0;
  color: #7f4e66;
  font-size: 13px;
}

.preset-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
}

.login-card :deep(.el-form-item__label) {
  color: #8a4c6e;
}

.login-card :deep(.el-input__wrapper) {
  border-color: rgba(243, 177, 214, 0.92);
  background: rgba(255, 255, 255, 0.86);
}

.login-card :deep(.el-input__wrapper:hover) {
  border-color: rgba(229, 130, 186, 0.86);
}

.login-card :deep(.el-input__wrapper.is-focus) {
  border-color: rgba(225, 105, 174, 0.9) !important;
  box-shadow: 0 0 0 3px rgba(244, 184, 220, 0.45) !important;
}

.login-card :deep(.el-button--primary:not(.is-link):not(.is-text):not(.is-plain)) {
  border-color: transparent !important;
  background: linear-gradient(130deg, #ff66b3, #ff8cb6) !important;
  box-shadow: 0 10px 18px rgba(220, 89, 156, 0.34);
}

@keyframes mascotFloat {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-6px);
  }
}

@keyframes eyeBlink {
  0%,
  70%,
  100% {
    transform: scaleY(1);
  }
  35% {
    transform: scaleY(0.25);
  }
}

@keyframes mascotShake {
  0%,
  100% {
    transform: translateX(0);
  }
  25% {
    transform: translateX(-4px);
  }
  75% {
    transform: translateX(4px);
  }
}

@keyframes handWave {
  0%,
  100% {
    transform: translate(-9px, -18px) rotate(-10deg);
  }
  50% {
    transform: translate(-7px, -22px) rotate(16deg);
  }
}

@keyframes gesturePop {
  0% {
    transform: scale(0.4) translateY(6px);
    opacity: 0;
  }
  40% {
    transform: scale(1.08) translateY(-2px);
    opacity: 1;
  }
  100% {
    transform: scale(1) translateY(0);
    opacity: 1;
  }
}

@keyframes confettiBurst {
  0% {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0.4) rotate(0deg);
  }
  12% {
    opacity: 1;
  }
  100% {
    opacity: 0;
    transform: translate(calc(-50% + var(--dx)), calc(-50% + var(--dy))) scale(0.95) rotate(var(--rot));
  }
}

@media (max-width: 920px) {
  .login-layout {
    grid-template-columns: 1fr;
  }

  .assistant-panel {
    order: 2;
  }

  .login-main {
    order: 1;
  }
}

@media (max-width: 760px) {
  .login-stage {
    padding: 12px;
  }

  .login-card {
    padding: 12px;
  }
}
</style>
