import { CONFIG } from '../config/config'

const API_BASE = CONFIG.api.baseUrl.replace(/\/$/, '')

function parseJsonSafely(text) {
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

function buildHeaders(accessToken, body, extraHeaders = {}) {
  const headers = { ...extraHeaders }

  if (body != null && !(body instanceof FormData) && typeof body !== 'string' && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json'
  }

  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`
  }

  return headers
}

function buildBody(body) {
  if (body == null || body instanceof FormData || typeof body === 'string') {
    return body
  }

  return JSON.stringify(body)
}

async function createHttpError(response) {
  const text = await response.text()
  const data = parseJsonSafely(text)
  const message = data?.error_description || data?.error || data?.message || data?.detail || response.statusText || `Error ${response.status}`
  const error = new Error(message)
  error.status = response.status
  error.payload = data
  throw error
}

async function parseResponse(response) {
  if (response.status === 204) {
    return { detail: 'Deleted' }
  }

  const text = await response.text()
  const data = parseJsonSafely(text)
  return data ?? text
}

export function buildApiUrl(path) {
  return `${API_BASE}${path}`
}

export async function requestJson(path, { method = 'GET', accessToken, body, headers, requireAuth = false } = {}) {
  if (requireAuth && !accessToken) {
    throw new Error('Nu esti autentificat.')
  }

  const response = await fetch(buildApiUrl(path), {
    method,
    headers: buildHeaders(accessToken, body, headers),
    body: buildBody(body),
  })

  if (!response.ok) {
    await createHttpError(response)
  }

  return parseResponse(response)
}

export async function requestRaw(path, { method = 'GET', accessToken, body, headers, requireAuth = false } = {}) {
  if (requireAuth && !accessToken) {
    throw new Error('Nu esti autentificat.')
  }

  const response = await fetch(buildApiUrl(path), {
    method,
    headers: buildHeaders(accessToken, body, headers),
    body: buildBody(body),
  })

  if (!response.ok) {
    await createHttpError(response)
  }

  return response
}
