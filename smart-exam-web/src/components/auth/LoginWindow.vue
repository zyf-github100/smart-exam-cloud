<script setup>
import { computed, reactive, ref } from 'vue'
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

const basePresets = [
  { label: 'Gateway Proxy', value: '/api/v1' },
  { label: 'Local Direct', value: 'http://localhost:9000/api/v1' },
]

const redirectPath = computed(() => {
  const raw = String(route.query.redirect || '').trim()
  if (!raw || raw === '/login') return ''
  return raw.startsWith('/') ? raw : ''
})

const applyBase = () => {
  setApiBase(apiBaseInput.value)
  currentApiBase.value = getApiBase()
  ElMessage.success('API base updated')
}

const usePreset = (preset) => {
  apiBaseInput.value = preset
  applyBase()
}

const login = async () => {
  const data = await run('login', () => api.login(authForm), { successMessage: 'Login succeeded' })
  if (!data) return
  setToken(data.token)
  setSavedUser(data.user)
  const nextPath = redirectPath.value || getDefaultAccessiblePath(data.user)
  await router.replace(nextPath)
}
</script>

<template>
  <div class="login-stage">
    <div class="anime-overlay"></div>
    <div class="login-orb orb-a"></div>
    <div class="login-orb orb-b"></div>

    <section class="login-card reveal-up">
      <header class="login-head">
        <p class="kicker">Smart Exam Cloud</p>
        <h1>Login</h1>
        <p class="sub">Use a dedicated login window, then jump to your authorized module.</p>
      </header>

      <el-form label-position="top" @submit.prevent>
        <el-form-item label="API Base">
          <el-input v-model="apiBaseInput" placeholder="/api/v1 or http://localhost:9000/api/v1">
            <template #append>
              <el-button @click="applyBase">Apply</el-button>
            </template>
          </el-input>
          <div class="preset-row">
            <el-button v-for="preset in basePresets" :key="preset.value" text @click="usePreset(preset.value)">
              {{ preset.label }}
            </el-button>
          </div>
          <p class="hint-text">Current base: {{ currentApiBase }}</p>
        </el-form-item>

        <div class="form-grid cols-2">
          <el-form-item label="Username">
            <el-input v-model="authForm.username" autocomplete="username" />
          </el-form-item>
          <el-form-item label="Password">
            <el-input
              v-model="authForm.password"
              type="password"
              show-password
              autocomplete="current-password"
              @keyup.enter="login"
            />
          </el-form-item>
        </div>

        <div class="action-row">
          <el-button type="primary" :loading="loading.login" @click="login">Sign In</el-button>
        </div>
      </el-form>
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
  width: min(620px, 100%);
  border-radius: 20px;
  border: 1px solid rgba(255, 178, 216, 0.82);
  background: rgba(255, 245, 252, 0.9);
  backdrop-filter: blur(10px);
  box-shadow: 0 18px 36px rgba(154, 65, 118, 0.22);
  padding: clamp(14px, 2.2vw, 20px);
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

@media (max-width: 760px) {
  .login-stage {
    padding: 12px;
  }
}
</style>
