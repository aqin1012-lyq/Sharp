import request from './request'

export function parseSupplier(data) {
  return request.post('/supplier/parse', data)
}

export function checkSupplier(data) {
  return request.post('/supplier/check', data)
}

export function saveSupplier(data) {
  return request.post('/supplier/save', data)
}

export function listSupplier(params) {
  return request.get('/supplier/list', { params })
}

export function deleteSupplier(id) {
  return request.delete(`/supplier/${id}`)
}
