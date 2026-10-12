import request from './request'

export function parseClaude(data) {
  return request.post('/claude/parse', data)
}

export function checkClaude(data) {
  return request.post('/claude/check', data)
}

export function saveClaude(data) {
  return request.post('/claude/save', data)
}

export function listClaude(params) {
  return request.get('/claude/list', { params })
}

export function deleteClaude(id) {
  return request.delete(`/claude/${id}`)
}
