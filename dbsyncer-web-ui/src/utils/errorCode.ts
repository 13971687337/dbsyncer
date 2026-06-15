const errorCode: Record<string | number, string> = {
  '401': '登录状态已过期，请重新登录',
  '403': '没有操作权限',
  '500': '服务器内部错误',
  'default': '系统未知错误，请反馈给管理员',
}

export default errorCode
