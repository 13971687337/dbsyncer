import { ElMessage, ElMessageBox } from 'element-plus'

const modal = {
  confirm(message, title = '提示', options = {}) {
    return ElMessageBox.confirm(message, title, {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      ...options,
    })
  },

  msgSuccess(message) {
    ElMessage.success(message)
  },

  msgError(message) {
    ElMessage.error(message)
  },

  msgWarning(message) {
    ElMessage.warning(message)
  },

  msgInfo(message) {
    ElMessage.info(message)
  },
}

export default modal
