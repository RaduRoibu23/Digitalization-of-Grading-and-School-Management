import { CONFIG } from '../config/config'
import { requestJson } from './apiClient'

const STORAGE_KEY = CONFIG.auth.storageKey
const APP_ROLES = ['student', 'professor', 'secretariat', 'scheduler', 'admin', 'sysadmin']

function sessionStorageRef() {
  if (typeof window === 'undefined') return null
  try {
    return window.sessionStorage
  } catch {
    return null
  }
}

export function decodeJwt(token) {
  try {
    const parts = token.split('.')
    const payload = parts[1]
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    return JSON.parse(decodeURIComponent(escape(json)))
  } catch {
    return null
  }
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
  const decoded = token ? decodeJwt(token) : null
  if (!decoded?.exp) return '-'

  const expiration = new Date(decoded.exp * 1000)
  return `${expiration.toLocaleString()} (exp=${decoded.exp})`
}

export async function login(username, password) {
  const data = await requestJson('/login', {
    method: 'POST',
    body: { username, password },
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
  })

  persistSession(data.access_token, data.id_token, data.refresh_token)

  return {
    accessToken: data.access_token,
    idToken: data.id_token,
    refreshToken: data.refresh_token,
  }
}
