import { CONFIG } from '../config'

const STORAGE_KEY = CONFIG.auth.storageKey
const API_BASE = CONFIG.api.baseUrl
const KEYCLOAK_URL = CONFIG.keycloak.url
const REALM = CONFIG.keycloak.realm
const CLIENT_ID = CONFIG.keycloak.clientId
const APP_ROLES = ['student', 'professor', 'secretariat', 'scheduler', 'admin', 'sysadmin']

function parseJsonSafely(text) {
  if (!text) return null
  try {
    return JSON.parse(text)
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
  const payload = {
    accessToken,
    idToken,
    refreshToken,
    savedAt: Date.now(),
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
}

export function clearSession() {
  localStorage.removeItem(STORAGE_KEY)
}

export function loadSession() {
  const raw = localStorage.getItem(STORAGE_KEY)
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
  const response = await fetch(`${API_BASE}/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ username, password }),
  })

  const text = await response.text()
  const data = parseJsonSafely(text)

  if (!response.ok) {
    const message = data?.error_description || data?.error || data?.message || data?.detail || 'Login failed'
    throw new Error(message)
  }

  persistSession(data.access_token, data.id_token, data.refresh_token)

  return {
    accessToken: data.access_token,
    idToken: data.id_token,
    refreshToken: data.refresh_token,
  }
}

export async function registerAccount(payload) {
  const response = await fetch(`${API_BASE}/register`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })

  const text = await response.text()
  const data = parseJsonSafely(text)

  if (!response.ok) {
    const message = data?.detail || data?.message || data?.error || 'Register failed'
    throw new Error(message)
  }

  return data
}

export async function loadPublicClasses() {
  const response = await fetch(`${API_BASE}/public/classes`)
  const text = await response.text()
  const data = parseJsonSafely(text)

  if (!response.ok) {
    const message = data?.error || data?.message || data?.detail || 'Could not load classes'
    throw new Error(message)
  }

  return Array.isArray(data) ? data : []
}

export async function refreshAccessToken(refreshToken) {
  const response = await fetch(`${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams({
      grant_type: 'refresh_token',
      client_id: CLIENT_ID,
      refresh_token: refreshToken,
    }),
  })

  const text = await response.text()
  const data = parseJsonSafely(text)

  if (!response.ok) {
    const message = data?.error_description || data?.error || data?.message || data?.detail || 'Token refresh failed'
    throw new Error(message)
  }

  persistSession(data.access_token, data.id_token, data.refresh_token)

  return {
    accessToken: data.access_token,
    idToken: data.id_token,
    refreshToken: data.refresh_token,
  }
}
