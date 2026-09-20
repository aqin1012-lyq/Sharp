// 登录态：token / 用户名 存 localStorage
const TOKEN_KEY = 'sharp:token'
const USER_KEY = 'sharp:username'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}
export function getUsername() {
  return localStorage.getItem(USER_KEY) || ''
}
export function setAuth(token, username) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, username || '')
}
export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
export function isLoggedIn() {
  return !!getToken()
}
