import request from './request'

export function loginApi(data) {
  return request.post('/auth/login', data)
}
export function registerApi(data) {
  return request.post('/auth/register', data)
}
export function resetPasswordApi(data) {
  return request.post('/auth/reset-password', data)
}
