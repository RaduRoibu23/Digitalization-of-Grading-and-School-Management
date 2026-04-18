import { CONFIG } from '../config/config'
import { requestJson } from './apiClient'

const STORAGE_KEY = CONFIG.auth.storageKey
const APP_ROLES = ['student', 'parent', 'professor', 'secretariat', 'scheduler', 'admin', 'sysadmin']

function sessionStorageRef() {
  if (typeof window === 'undefined') return null
  try {
    return window.sessionStorage
  } catch {
    return null
  }
}

function decodeBase64Url(value) {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '=')
  return atob(padded)
}

export function decodeJwt(token) {
  try {
    const parts = token.split('.')
    const payload = parts[1]
    const json = decodeBase64Url(payload)
    return JSON.parse(json)
  } catch {
    return null
  }
}

export function tokenExpiresAtMs(token) {
  const decoded = token ? decodeJwt(token) : null
  return decoded?.exp ? decoded.exp * 1000 : null
}

export function persistSession(accessToken, idToken, refreshToken) {
  const storage = sessionStorageRef()
  if (!storage) return
  storage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      accessToken,
      idToken,
      refreshToken,
      savedAt: Date.now(),
    })
  )
}

export function clearSession() {
  const storage = sessionStorageRef()
  if (!storage) return
  storage.removeItem(STORAGE_KEY)
}

export function loadSession() {
  const storage = sessionStorageRef()
  if (!storage) return null
  const raw = storage.getItem(STORAGE_KEY)
  if (!raw) return null

  try {
    const data = JSON.parse(raw)
    return {
      accessToken: data.accessToken || null,
      idToken: data.idToken || null,
      refreshToken: data.refreshToken || null,
    }
  } catch {
    return null
  }
}

export function rolesFromToken(accessToken) {
  const token = accessToken ? decodeJwt(accessToken) : null
  const roles = token?.realm_access?.roles
  if (!Array.isArray(roles)) {
    return []
  }
  return roles.filter((role) => APP_ROLES.includes(role))
}

export function tokenExpiryText(token) {
  const expirationMs = tokenExpiresAtMs(token)
  if (!expirationMs) return '-'

  const expiration = new Date(expirationMs)
  const decoded = decodeJwt(token)
  return `${expiration.toLocaleString()} (exp=${decoded?.exp ?? '-'})`
}

export async function login(username, password) {
  const data = await requestJson('/login', {
    method: 'POST',
    body: { username, password },
    skipAuthRetry: true,
  })

  persistSession(data.access_token, data.id_token, data.refresh_token)

  return {
    accessToken: data.access_token,
    idToken: data.id_token,
    refreshToken: data.refresh_token,
  }
}

export async function refreshAccessToken(refreshToken) {
  const data = await requestJson('/refresh', {
    method: 'POST',
    body: { refreshToken },
    skipAuthRetry: true,
  })

  persistSession(data.access_token, data.id_token, data.refresh_token)

  return {
    accessToken: data.access_token,
    idToken: data.id_token,
    refreshToken: data.refresh_token,
  }
}
