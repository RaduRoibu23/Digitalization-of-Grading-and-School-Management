import { requestJson, requestRaw } from './apiClient'

export async function apiGet(path, accessToken) {
  return requestJson(path, { accessToken, requireAuth: true })
}

export async function apiPost(path, body, accessToken) {
  return requestJson(path, { method: 'POST', body, accessToken, requireAuth: true })
}

export async function apiPut(path, body, accessToken) {
  return requestJson(path, { method: 'PUT', body, accessToken, requireAuth: true })
}

export async function apiPatch(path, body, accessToken) {
  return requestJson(path, { method: 'PATCH', body, accessToken, requireAuth: true })
}

export async function apiDelete(path, accessToken) {
  return requestJson(path, { method: 'DELETE', accessToken, requireAuth: true })
}

export async function apiDownload(path, accessToken) {
  return requestRaw(path, { accessToken, requireAuth: true })
}
