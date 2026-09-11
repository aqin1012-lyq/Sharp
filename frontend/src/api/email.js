import request from './request'

export function parseEmail(data) {
  return request.post('/email/parse', data)
}

export function saveEmail(data) {
  return request.post('/email/save', data)
}

export function listEmail(params) {
  return request.get('/email/list', { params })
}

export function deleteEmail(id) {
  return request.delete(`/email/${id}`)
}
