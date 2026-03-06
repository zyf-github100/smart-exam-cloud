<script setup>
import { inject } from 'vue'
import { ADMIN_CONSOLE_KEY } from '../../../composables/useAdminConsole'

const admin = inject(ADMIN_CONSOLE_KEY)

const roleStatusTag = (value) => (Number(value) === 1 ? 'success' : 'info')
const roleStatusText = (value) => (Number(value) === 1 ? 'ENABLED' : 'DISABLED')
const systemRoleText = (value) => (Number(value) === 1 ? 'YES' : 'NO')
</script>

<template>
  <section v-if="admin" class="console-block">
    <div class="block-head">
      <div>
        <h3 class="block-title">角色权限矩阵</h3>
        <p class="block-sub">按角色配置权限点，支持细粒度授权。</p>
      </div>
      <el-button :loading="admin.loading.roles || admin.loading.permissions" @click="admin.loadRolesAndPermissions">
        刷新权限
      </el-button>
    </div>

    <div class="query-row">
      <el-select v-model="admin.selectedRoleCode" placeholder="选择角色">
        <el-option v-for="role in admin.roles" :key="role.roleCode" :label="role.roleName" :value="role.roleCode" />
      </el-select>
      <el-button type="primary" :loading="admin.loading.rolePermissions" @click="admin.applyRolePermissions">
        保存权限
      </el-button>
    </div>

    <div class="permission-panel">
      <section v-for="group in admin.permissionGroups" :key="group.moduleKey" class="permission-group">
        <p class="permission-group-title">{{ group.moduleKey }}</p>
        <el-checkbox-group v-model="admin.rolePermissionSelection">
          <el-checkbox v-for="item in group.items" :key="item.permissionCode" :label="item.permissionCode">
            {{ item.permissionName }}
          </el-checkbox>
        </el-checkbox-group>
      </section>
    </div>

    <template v-if="admin.selectedRoleDetail">
      <el-descriptions :column="2" border size="small" class="role-detail-panel">
        <el-descriptions-item label="Role Code">{{ admin.selectedRoleDetail.roleCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Role Name">{{ admin.selectedRoleDetail.roleName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="System Role">
          {{ systemRoleText(admin.selectedRoleDetail.isSystem) }}
        </el-descriptions-item>
        <el-descriptions-item label="Status">
          <el-tag :type="roleStatusTag(admin.selectedRoleDetail.status)">
            {{ roleStatusText(admin.selectedRoleDetail.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="Description" :span="2">
          {{ admin.selectedRoleDetail.description || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="Permission Count" :span="2">
          {{ (admin.selectedRoleDetail.permissions || []).length }}
        </el-descriptions-item>
      </el-descriptions>

      <el-table
        :data="admin.selectedRoleDetail.permissions || []"
        size="small"
        max-height="320"
        class="role-perm-table"
      >
        <el-table-column prop="permissionCode" label="Permission Code" min-width="180" />
        <el-table-column prop="permissionName" label="Permission Name" min-width="180" />
        <el-table-column prop="moduleKey" label="Module" min-width="110" />
        <el-table-column prop="description" label="Description" min-width="220" show-overflow-tooltip />
      </el-table>
    </template>
    <el-empty v-else description="Please select a role" />
  </section>
  <section v-else class="console-block">
    <p class="hint-text">管理上下文初始化失败，请返回“管理员总览”后重试。</p>
  </section>
</template>

<style scoped>
.permission-panel {
  display: grid;
  gap: 10px;
  margin-top: 8px;
  margin-bottom: 8px;
  max-height: 360px;
  overflow: auto;
  padding-right: 6px;
}

.permission-group {
  border: 1px solid rgba(199, 216, 204, 0.92);
  border-radius: 10px;
  background: rgba(249, 253, 250, 0.95);
  padding: 10px;
}

.permission-group-title {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  color: #3b5547;
}

.role-detail-panel {
  margin-top: 8px;
}

.role-perm-table {
  margin-top: 10px;
}
</style>
