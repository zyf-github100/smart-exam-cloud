<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowLeft, ArrowRight, DataAnalysis, TrendCharts } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { consoleModules, recommendedFlow } from './router/consoleModules'
import { AUTH_CHANGED_EVENT, getSavedUser } from './api/client'
import { canAccessModule, getDefaultAccessiblePath } from './composables/accessControl'

const route = useRoute()
const router = useRouter()

const nowText = ref(new Date().toLocaleString())
const authUser = ref(getSavedUser())
const quickJumpPath = ref('')
let timer = null

const roleLabelMap = {
  ADMIN: '管理员',
  TEACHER: '教师',
  STUDENT: '学生',
}

const fallbackModule = {
  name: 'connection',
  path: '/connection',
  label: '环境连接',
  tagline: '网关与账号登录',
  description: '先登录并完成环境检查，再进入后续业务。',
  tips: ['先确认账号权限', '再进入业务模块', '关键操作前先保存'],
}

const isModuleRoute = (modulePath, currentPath = route.path) =>
  currentPath === modulePath || currentPath.startsWith(`${modulePath}/`)

const visibleModules = computed(() => consoleModules.filter((item) => canAccessModule(authUser.value, item)))
const activeModule = computed(
  () => visibleModules.value.find((item) => isModuleRoute(item.path)) || visibleModules.value[0] || fallbackModule
)
const activeIndex = computed(() => visibleModules.value.findIndex((item) => item.path === activeModule.value.path))
const progressPercent = computed(() => {
  const total = visibleModules.value.length || 1
  const current = activeIndex.value < 0 ? 1 : activeIndex.value + 1
  return Math.round((current / total) * 100)
})

const chapterText = computed(() => {
  const total = visibleModules.value.length || 1
  const current = activeIndex.value < 0 ? 1 : activeIndex.value + 1
  return `${current} / ${total}`
})

const currentRoleCode = computed(() => String(authUser.value?.role || '').trim().toUpperCase())
const currentRoleLabel = computed(() => roleLabelMap[currentRoleCode.value] || currentRoleCode.value || '访客')
const currentUserName = computed(() => authUser.value?.realName || authUser.value?.username || '未登录')

const activeTips = computed(() =>
  Array.isArray(activeModule.value?.tips) && activeModule.value.tips.length
    ? activeModule.value.tips
    : fallbackModule.tips
)

const recommendedVisibleFlow = computed(() =>
  visibleModules.value.length ? visibleModules.value.map((item) => item.label).join(' → ') : recommendedFlow
)
const isLoginRoute = computed(() => route.path === '/login')

const goTo = (path) => {
  if (!path) return
  if (route.path !== path) {
    router.push(path)
  }
}

const goPrev = () => {
  if (activeIndex.value <= 0) return
  goTo(visibleModules.value[activeIndex.value - 1].path)
}

const goNext = () => {
  if (activeIndex.value >= visibleModules.value.length - 1) return
  goTo(visibleModules.value[activeIndex.value + 1].path)
}

const syncAuthState = () => {
  authUser.value = getSavedUser()
}

const ensureRouteAccess = () => {
  if (!authUser.value) {
    goTo('/login')
    return
  }
  if (route.path === '/login') {
    goTo(getDefaultAccessiblePath(authUser.value))
    return
  }
  if (!visibleModules.value.length) {
    goTo('/connection')
    return
  }
  const matched = visibleModules.value.some((item) => isModuleRoute(item.path))
  if (!matched) {
    goTo(getDefaultAccessiblePath(authUser.value))
  }
}

watch(activeModule, (value) => {
  quickJumpPath.value = value?.path || ''
}, { immediate: true })

const handleQuickJump = (value) => {
  if (!value || route.path === value) return
  goTo(value)
}

onMounted(() => {
  window.addEventListener(AUTH_CHANGED_EVENT, syncAuthState)
  timer = window.setInterval(() => {
    nowText.value = new Date().toLocaleString()
  }, 1000)
})

onBeforeUnmount(() => {
  window.removeEventListener(AUTH_CHANGED_EVENT, syncAuthState)
  if (timer) {
    window.clearInterval(timer)
  }
})

watch([() => route.path, visibleModules], ensureRouteAccess, { immediate: true })
</script>

<template>
  <div v-if="isLoginRoute" class="login-route-shell">
    <router-view v-slot="{ Component }">
      <transition name="route-fade" mode="out-in">
        <component :is="Component" :key="route.path" />
      </transition>
    </router-view>
  </div>

  <div v-else class="app-shell">
    <div class="bg-orb orb-a"></div>
    <div class="bg-orb orb-b"></div>
    <div class="bg-orb orb-c"></div>

    <header class="command-deck reveal-up">
      <div class="deck-brand">
        <div class="brand-mark notranslate" translate="no" aria-label="Smart Exam">
          <span class="brand-mark-ring"></span>
          <span class="brand-mark-dot"></span>
          <span class="brand-mark-text">SC</span>
        </div>
        <div class="brand-copy">
          <p class="brand-kicker">Smart Exam Cloud</p>
          <h1 class="brand-title">学园考试指挥中心</h1>
          <p class="brand-sub">从题库、组卷到考试与阅卷的一体化流程台，按角色自动切换可访问章节。</p>
          <div class="brand-tags">
            <span class="brand-tag">角色：{{ currentRoleLabel }}</span>
            <span class="brand-tag">用户：{{ currentUserName }}</span>
          </div>
        </div>
      </div>

      <div class="deck-controls">
        <article class="deck-chip">
          <span>系统时间</span>
          <strong>{{ nowText }}</strong>
        </article>
        <article class="deck-chip">
          <span>当前章节</span>
          <strong>{{ activeModule.label }}</strong>
        </article>
        <article class="deck-chip">
          <span>流程进度</span>
          <strong>{{ chapterText }}</strong>
        </article>
        <article class="deck-chip deck-chip-progress">
          <span>完成度</span>
          <strong>{{ progressPercent }}%</strong>
          <el-progress :percentage="progressPercent" :show-text="false" :stroke-width="6" />
        </article>
      </div>
    </header>

    <div class="layout-grid reveal-up delay-1">
      <aside class="chapter-rail card-surface">
        <div class="rail-head">
          <p class="rail-kicker">Storyline</p>
          <h2>章节导航</h2>
        </div>

        <div class="rail-jump">
          <p>快速跳转</p>
          <el-select
            v-model="quickJumpPath"
            placeholder="选择章节"
            style="width: 100%"
            @change="handleQuickJump"
          >
            <el-option
              v-for="module in visibleModules"
              :key="module.path"
              :label="module.label"
              :value="module.path"
            />
          </el-select>
        </div>

        <div class="rail-list">
          <button
            v-for="(module, index) in visibleModules"
            :key="module.path"
            type="button"
            class="rail-item"
            :class="{ active: isModuleRoute(module.path) }"
            @click="goTo(module.path)"
          >
            <span class="rail-order">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="rail-content">
              <span class="rail-label">{{ module.label }}</span>
              <span class="rail-tagline">{{ module.tagline }}</span>
            </span>
          </button>
        </div>

        <div class="rail-actions">
          <el-button plain :disabled="activeIndex <= 0" @click="goPrev">
            <el-icon><ArrowLeft /></el-icon>
            上一章
          </el-button>
          <el-button type="primary" :disabled="activeIndex >= visibleModules.length - 1" @click="goNext">
            下一章
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
      </aside>

      <main class="mission-zone">
        <section class="mission-hero card-surface">
          <div class="hero-head">
            <p class="hero-kicker">Mission Stage</p>
            <h2>{{ activeModule.label }}</h2>
            <p class="hero-desc">{{ activeModule.description }}</p>
          </div>
        </section>

        <section class="mission-canvas card-surface">
          <router-view v-slot="{ Component }">
            <transition name="route-fade" mode="out-in">
              <component :is="Component" :key="route.path" />
            </transition>
          </router-view>
        </section>
      </main>

      <aside class="context-dock">
        <el-card class="dock-card" shadow="never">
          <template #header>
            <div class="dock-head">
              <el-icon><TrendCharts /></el-icon>
              <strong>本章提示</strong>
            </div>
          </template>
          <ol class="dock-list">
            <li v-for="(tip, index) in activeTips" :key="`${activeModule.name}-${index}`">{{ tip }}</li>
          </ol>
        </el-card>

        <el-card class="dock-card" shadow="never">
          <template #header>
            <div class="dock-head">
              <el-icon><DataAnalysis /></el-icon>
              <strong>推荐路径</strong>
            </div>
          </template>
          <p class="dock-path">{{ recommendedVisibleFlow }}</p>
        </el-card>
      </aside>
    </div>
  </div>
</template>
