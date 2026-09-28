import request from './request'

/* —— Apple ID —— */
export function listAppleAccounts(params) {
  return request.get('/apple/accounts', { params })
}

export function saveAppleAccount(data) {
  return request.post('/apple/accounts', data)
}

export function deleteAppleAccount(id) {
  return request.delete(`/apple/accounts/${id}`)
}

/* —— 子模块一：转发邮箱 —— */
export function listForwardEmails(params) {
  return request.get('/apple/forward-emails', { params })
}

export function listForwardEmailsOf(appleAccountId) {
  return request.get(`/apple/forward-emails/of/${appleAccountId}`)
}

export function saveForwardEmail(data) {
  return request.post('/apple/forward-emails', data)
}

export function setCurrentForward(id) {
  return request.post(`/apple/forward-emails/${id}/set-current`)
}

export function updateForwardEmail(id, data) {
  return request.put(`/apple/forward-emails/${id}`, data)
}

export function deleteForwardEmail(id) {
  return request.delete(`/apple/forward-emails/${id}`)
}

/* —— 子模块二：隐藏邮箱 —— */
export function listHideEmails(params) {
  return request.get('/apple/hide-emails', { params })
}

export function saveHideEmail(data) {
  return request.post('/apple/hide-emails', data)
}

export function saveHideEmailBatch(data) {
  return request.post('/apple/hide-emails/batch', data)
}

export function updateHideEmail(id, data) {
  return request.put(`/apple/hide-emails/${id}`, data)
}

export function deleteHideEmail(id) {
  return request.delete(`/apple/hide-emails/${id}`)
}

/* —— 子模块三：黑客邮箱 —— */
export function listHackerEmails(params) {
  return request.get('/apple/hacker-emails', { params })
}

export function saveHackerEmail(data) {
  return request.post('/apple/hacker-emails', data)
}

export function saveHackerEmailBatch(data) {
  return request.post('/apple/hacker-emails/batch', data)
}

export function updateHackerEmail(id, data) {
  return request.put(`/apple/hacker-emails/${id}`, data)
}

export function deleteHackerEmail(id) {
  return request.delete(`/apple/hacker-emails/${id}`)
}

/* —— 模块四 / 五：倒推接码 —— */
export function fetchAppleCode(data) {
  return request.post('/apple/code/fetch', data)
}

export function checkForwardEmail(data) {
  return request.post('/apple/code/check', data || {})
}

export function listCodeLogs(params) {
  return request.get('/apple/code/logs', { params })
}
