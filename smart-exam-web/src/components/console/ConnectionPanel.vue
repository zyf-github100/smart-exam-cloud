<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  api,
  clearAuth,
  getApiBase,
  getSavedUser,
  getToken,
  setApiBase,
  setSavedUser,
  setToken,
} from '../../api/client'

const apiBaseInput = ref(getApiBase())
const currentApiBase = ref(getApiBase())
const loading = reactive({})
const authForm = reactive({
  username: 'teacher1',
  password: '123456',
})
const authState = reactive({
  token: getToken(),
  user: getSavedUser(),
})
const meData = ref(null)
const userList = ref([])

const isAuthenticated = computed(() => Boolean(authState.token))

const setLoading = (key, value) => {
  loading[key] = value
}

const execute = async (loadingKey, action, successMessage) => {
  setLoading(loadingKey, true)
  try {
    const result = await action()
    if (successMessage) {
      ElMessage.success(successMessage)
    }
    return result
  } catch (error) {
    ElMessage.error(error.message || 'Request failed')
    return null
  } finally {
    setLoading(loadingKey, false)
  }
}

const toPretty = (value) => {
  if (value === null || value === undefined || value === '') {
    return '暂无数据'
  }
  return JSON.stringify(value, null, 2)
}

const applyBase = () => {
  setApiBase(apiBaseInput.value)
  currentApiBase.value = getApiBase()
  ElMessage.success('API 地址已更新')
}

const fetchMe = async () => {
  const data = await execute('me', () => api.getMe())
  if (!data) return
  meData.value = data
  const mergedUser = {
    ...(authState.user || {}),
    id: data.id || data.profile?.id,
    role: data.role || data.profile?.role,
    nickname: data.profile?.nickname,
    status: data.profile?.status,
  }
  authState.user = mergedUser
  setSavedUser(mergedUser)
}

const fetchUsers = async () => {
  const data = await execute('users', () => api.listUsers())
  if (data) {
    userList.value = Array.isArray(data) ? data : []
  }
}

const login = async () => {
  const data = await execute('login', () => api.login(authForm), '登录成功')
  if (!data) return
  authState.token = data.token
  authState.user = data.user
  setToken(data.token)
  setSavedUser(data.user)
  await fetchMe()
  await fetchUsers()
}

const logout = async () => {
  await execute('logout', () => api.logout(), '已退出登录')
  authState.token = ''
  authState.user = null
  meData.value = null
  userList.value = []
  clearAuth()
}

onMounted(async () => {
  if (authState.token) {
    await fetchMe()
    await fetchUsers()
  }
})
</script>

<template>
  <el-card class="panel-card" shadow="hover">
    <template #header>
      <strong>连接与账号</strong>
    </template>
    <el-form label-position="top">
      <el-form-item label="API Base">
        <el-input v-model="apiBaseInput" placeholder="/api/v1 或 http://localhost:9000/api/v1">
          <template #append>
            <el-button @click="applyBase">应用</el-button>
          </template>
        </el-input>
        <div class="hint">当前: {{ currentApiBase }}</div>
      </el-form-item>
      <el-form-item label="用户名">
        <el-input v-model="authForm.username" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="authForm.password" type="password" show-password />
      </el-form-item>
      <el-space wrap>
        <el-button type="primary" :loading="loading.login" @click="login">登录</el-button>
        <el-button :loading="loading.logout" @click="logout">退出</el-button>
        <el-button :loading="loading.me" @click="fetchMe">刷新我的信息</el-button>
        <el-button :loading="loading.users" @click="fetchUsers">刷新用户列表</el-button>
      </el-space>
      <div class="state-line">
        <el-tag :type="isAuthenticated ? 'success' : 'warning'">
          {{ isAuthenticated ? '已登录' : '未登录' }}
        </el-tag>
        <span>角色: {{ authState.user?.role || '-' }}</span>
      </div>
    </el-form>
  </el-card>

  <el-card class="panel-card" shadow="hover">
    <template #header>
      <strong>我的信息</strong>
    </template>
    <pre class="json-block">{{ toPretty(meData || authState.user) }}</pre>
  </el-card>

  <el-card class="panel-card" shadow="hover">
    <template #header>
      <strong>用户列表</strong>
    </template>
    <el-table :data="userList" size="small" height="250">
      <el-table-column prop="id" label="ID" min-width="120" />
      <el-table-column prop="username" label="用户名" min-width="100" />
      <el-table-column prop="role" label="角色" min-width="90" />
      <el-table-column prop="status" label="状态" min-width="90" />
    </el-table>
  </el-card>
</template>

<style scoped>
.hint {
  margin-top: 6px;
  color: #61727d;
  font-size: 12px;
}

.state-line {
  margin-top: 12px;
  display: flex;
  gap: 12px;
  align-items: center;
  color: #355160;
}
</style>
