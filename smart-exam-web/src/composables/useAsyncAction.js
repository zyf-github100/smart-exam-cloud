import { reactive } from 'vue'
import { ElMessage } from 'element-plus'

export const useAsyncAction = () => {
  const loading = reactive({})

  const run = async (key, action, options = {}) => {
    loading[key] = true
    try {
      const result = await action()
      if (options.successMessage) {
        ElMessage.success(options.successMessage)
      }
      return result
    } catch (error) {
      ElMessage.error(error?.message || options.errorMessage || 'Request failed')
      return null
    } finally {
      loading[key] = false
    }
  }

  return { loading, run }
}

export const prettyJson = (value) => {
  if (value === null || value === undefined || value === '') {
    return '暂无数据'
  }
  return JSON.stringify(value, null, 2)
}

