import request from './request'

// 取件比普通请求慢（换 token + IMAP 连接），单独放宽超时
export function fetchMail(data) {
  return request.post('/mail/fetch', data, { timeout: 30000 })
}
