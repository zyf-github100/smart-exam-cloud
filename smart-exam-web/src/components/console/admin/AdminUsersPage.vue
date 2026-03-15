<script setup>
import { inject } from 'vue'
import { ADMIN_CONSOLE_KEY } from '../../../composables/useAdminConsole'

const admin = inject(ADMIN_CONSOLE_KEY)

const handleUserPageChange = (value) => {
  admin.userQuery.page = value
  admin.loadUsers()
}
</script>

<template>
  <section v-if="admin" class="console-block">
    <div class="block-head">
      <div>
        <h3 class="block-title">用户治理</h3>
        <p class="block-sub">账号检索、状态切换、角色更新和密码重置。</p>
      </div>
      <el-button :loading="admin.loading.users" @click="admin.loadUsers">刷新列表</el-button>
    </div>

    <div class="form-grid cols-3">
      <el-input v-model="admin.userQuery.keyword" placeholder="用户名 / 姓名" />
      <el-select v-model="admin.userQuery.roleCode" clearable placeholder="角色">
        <el-option label="ADMIN" value="ADMIN" />
        <el-option label="TEACHER" value="TEACHER" />
        <el-option label="STUDENT" value="STUDENT" />
      </el-select>
      <el-select v-model="admin.userQuery.status" clearable placeholder="状态">
        <el-option label="ENABLED" :value="1" />
        <el-option label="DISABLED" :value="0" />
      </el-select>
    </div>

    <div class="action-row">
      <el-button type="primary" :loading="admin.loading.users" @click="admin.loadUsers">查询用户</el-button>
    </div>

    <el-table :data="admin.userPage.records" size="small" max-height="360" @row-click="admin.selectUser">
      <el-table-column prop="id" label="ID" min-width="120" />
      <el-table-column prop="username" label="用户名" min-width="110" />
      <el-table-column prop="realName" label="姓名" min-width="110" />
      <el-table-column prop="role" label="角色" width="100" />
      <el-table-column prop="statusLabel" label="状态" width="100" />
    </el-table>

    <el-pagination
      class="pagination-row"
      layout="prev, pager, next, total"
      :total="admin.userPage.total"
      :page-size="admin.userQuery.size"
      :current-page="admin.userQuery.page"
      @current-change="handleUserPageChange"
    />

    <div class="mutate-panel">
      <p class="hint-text">
        当前选中: {{ admin.selectedUser ? `${admin.selectedUser.username} (${admin.selectedUser.id})` : '未选择用户' }}
      </p>

      <div class="form-grid cols-2">
        <el-form-item label="状态">
          <el-select v-model="admin.userMutation.status">
            <el-option label="ENABLED" :value="1" />
            <el-option label="DISABLED" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="admin.userMutation.roleCode">
            <el-option v-for="role in admin.roleOptions" :key="role" :label="role" :value="role" />
          </el-select>
        </el-form-item>
      </div>

      <el-form-item label="变更说明">
        <el-input v-model="admin.userMutation.reason" placeholder="建议填写原因，方便审计追踪" />
      </el-form-item>

      <div class="action-row">
        <el-button type="primary" :loading="admin.loading.userStatus" @click="admin.applyUserStatus">更新状态</el-button>
        <el-button type="primary" plain :loading="admin.loading.userRole" @click="admin.applyUserRole">更新角色</el-button>
      </div>

      <el-form-item label="重置密码">
        <div class="password-row">
          <el-input
            v-model="admin.userMutation.newPassword"
            type="password"
            show-password
            placeholder="输入新密码（8~64 位，含大小写字母、数字、特殊字符）"
          />
          <el-button type="danger" :loading="admin.loading.userPassword" @click="admin.resetUserPassword">
            执行重置
          </el-button>
        </div>
      </el-form-item>
    </div>
  </section>
  <section v-else class="console-block">
    <p class="hint-text">管理上下文初始化失败，请返回“管理员总览”后重试。</p>
  </section>
</template>

<style scoped>
.pagination-row {
  margin-top: 10px;
}

.mutate-panel {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed rgba(175, 192, 180, 0.9);
}

.password-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}

@media (max-width: 900px) {
  .password-row {
    grid-template-columns: 1fr;
  }
}
</style>
