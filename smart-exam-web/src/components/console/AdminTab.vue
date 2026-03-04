<script setup>
import { computed, provide } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, Operation, Setting, User, Warning } from '@element-plus/icons-vue'
import { ADMIN_CONSOLE_KEY, useAdminConsole } from '../../composables/useAdminConsole'

const route = useRoute()
const router = useRouter()

const admin = useAdminConsole()
provide(ADMIN_CONSOLE_KEY, admin)

const adminSections = [
  {
    name: 'admin-users',
    path: '/admin/users',
    label: '用户治理',
    icon: User,
    summary: '检索用户、更新状态与角色、执行密码重置。',
  },
  {
    name: 'admin-roles',
    path: '/admin/roles',
    label: '角色权限',
    icon: Lock,
    summary: '维护角色权限矩阵与模块授权配置。',
  },
  {
    name: 'admin-configs',
    path: '/admin/configs',
    label: '系统配置',
    icon: Setting,
    summary: '集中维护后台配置项与分组策略。',
  },
  {
    name: 'admin-audits',
    path: '/admin/audits',
    label: '审计日志',
    icon: Operation,
    summary: '查询高风险操作记录与审计明细。',
  },
  {
    name: 'admin-risks',
    path: '/admin/risks',
    label: '风险监控',
    icon: Warning,
    summary: '查看考试会话风险评分与防作弊事件。',
  },
]

const isSectionActive = (section) => route.path === section.path
const activeSection = computed(() => adminSections.find((section) => isSectionActive(section)) || adminSections[0])

const goSection = (section) => {
  if (isSectionActive(section)) return
  router.push(section.path)
}
</script>

<template>
  <div class="console-stage stack-gap">
    <section class="console-block">
      <div class="block-head compact">
        <h3 class="block-title">管理员总览</h3>
        <el-button :loading="admin.loading.overview" @click="admin.loadOverview">刷新总览</el-button>
      </div>
      <div class="metrics-grid cols-3">
        <article class="metric-card">
          <span>平台用户</span>
          <strong>{{ admin.overview?.totalUsers || 0 }}</strong>
        </article>
        <article class="metric-card">
          <span>启用账号</span>
          <strong>{{ admin.overview?.enabledUsers || 0 }}</strong>
        </article>
        <article class="metric-card">
          <span>停用账号</span>
          <strong>{{ admin.overview?.disabledUsers || 0 }}</strong>
        </article>
        <article class="metric-card">
          <span>考试总量</span>
          <strong>{{ admin.overview?.totalExams || 0 }}</strong>
        </article>
        <article class="metric-card">
          <span>进行中考试</span>
          <strong>{{ admin.overview?.runningExams || 0 }}</strong>
        </article>
        <article class="metric-card">
          <span>24h 管理操作</span>
          <strong>{{ admin.overview?.operationsInLast24Hours || 0 }}</strong>
        </article>
      </div>
      <p class="hint-text">总览生成时间: {{ admin.overview?.generatedAt || '-' }}</p>
    </section>

    <section class="console-block admin-nav-block">
      <div class="block-head compact">
        <div>
          <h3 class="block-title">后台功能分区</h3>
          <p class="block-sub">{{ activeSection.summary }}</p>
        </div>
      </div>

      <div class="admin-nav-list">
        <button
          v-for="section in adminSections"
          :key="section.name"
          type="button"
          class="admin-nav-item"
          :class="{ active: isSectionActive(section) }"
          @click="goSection(section)"
        >
          <el-icon><component :is="section.icon" /></el-icon>
          <span>{{ section.label }}</span>
        </button>
      </div>
    </section>

    <router-view v-slot="{ Component }">
      <transition name="route-fade" mode="out-in">
        <component :is="Component" :key="route.path" />
      </transition>
    </router-view>
  </div>
</template>

<style scoped>
.admin-nav-block {
  padding-bottom: 10px;
}

.admin-nav-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 8px;
}

.admin-nav-item {
  appearance: none;
  border: 1px solid rgba(189, 210, 195, 0.9);
  border-radius: 10px;
  padding: 10px 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  color: #294535;
  background: rgba(246, 251, 247, 0.9);
  transition: all 0.2s ease;
}

.admin-nav-item:hover {
  border-color: rgba(40, 146, 113, 0.34);
  background: rgba(238, 248, 240, 0.95);
}

.admin-nav-item.active {
  color: #fff;
  border-color: transparent;
  background: linear-gradient(132deg, #17765d, #2ea079);
}

@media (max-width: 1080px) {
  .admin-nav-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .admin-nav-list {
    grid-template-columns: 1fr;
  }
}
</style>
