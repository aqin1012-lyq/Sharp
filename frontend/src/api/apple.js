import request from './request'

export function listAppleAccounts(params) {
  return request.get('/apple/accounts', { params })
}

export function saveAppleAccount(data) {
  return request.post('/apple/accounts', data)
}

export function deleteAppleAccount(id) {
  return request.delete(`/apple/accounts/${id}`)
}

export function listHideEmails(params) {
  return request.get('/apple/hide-emails', { params })
}

export function saveHideEmail(data) {
  return request.post('/apple/hide-emails', data)
}

export function updateHideEmail(id, data) {
  return request.put(`/apple/hide-emails/${id}`, data)
}

export function deleteHideEmail(id) {
  return request.delete(`/apple/hide-emails/${id}`)
}
