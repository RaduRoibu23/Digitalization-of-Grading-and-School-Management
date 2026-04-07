import { CONFIG } from '../config/config'

const API_BASE = CONFIG.api.baseUrl.replace(/\/$/, '')

const authRuntime = {
  getSession: null,
  refreshSession: null,
  onAuthFailure: null,
  refreshPromise: null,
}

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
  const firstFieldError = data?.field_errors ? Object.values(data.field_errors)[0] : null
  const message = data?.error_description
    || data?.detail
    || data?.message
    || firstFieldError
    || data?.error
    || response.statusText
    || `Error ${response.status}`
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

async function executeRequest(path, { method = 'GET', accessToken, body, headers } = {}) {
  return fetch(buildApiUrl(path), {
    method,
    headers: buildHeaders(accessToken, body, headers),
    body: buildBody(body),
  })
}

async function refreshWithRuntimeSession() {
  const session = authRuntime.getSession?.()
  if (!session?.refreshToken || !authRuntime.refreshSession) {
    throw new Error('Nu mai exista o sesiune valida.')
  }

  if (!authRuntime.refreshPromise) {
    authRuntime.refreshPromise = authRuntime.refreshSession(session.refreshToken)
      .finally(() => {
        authRuntime.refreshPromise = null
      })
  }

  return authRuntime.refreshPromise
}

export function configureApiAuthRuntime({ getSession, refreshSession, onAuthFailure }) {
  authRuntime.getSession = getSession || null
  authRuntime.refreshSession = refreshSession || null
  authRuntime.onAuthFailure = onAuthFailure || null
}

export function clearApiAuthRuntime() {
  authRuntime.getSession = null
  authRuntime.refreshSession = null
  authRuntime.onAuthFailure = null
  authRuntime.refreshPromise = null
}

export function buildApiUrl(path) {
  return `${API_BASE}${path}`
}

export async function requestJson(
  path,
  {
    method = 'GET',
    accessToken,
    body,
    headers,
    requireAuth = false,
    skipAuthRetry = false,
  } = {}
) {
  if (requireAuth && !accessToken) {
    throw new Error('Nu esti autentificat.')
  }

  let response = await executeRequest(path, { method, accessToken, body, headers })
  if (response.status === 401 && requireAuth && !skipAuthRetry && authRuntime.refreshSession) {
    try {
      const refreshedSession = await refreshWithRuntimeSession()
      response = await executeRequest(path, {
        method,
        accessToken: refreshedSession?.accessToken || refreshedSession?.access_token || null,
        body,
        headers,
      })
    } catch (error) {
      authRuntime.onAuthFailure?.()
      throw error
    }
  }

  if (!response.ok) {
    if (response.status === 401) {
      authRuntime.onAuthFailure?.()
    }
    await createHttpError(response)
  }

  return parseResponse(response)
}

export async function requestRaw(
  path,
  {
    method = 'GET',
    accessToken,
    body,
    headers,
    requireAuth = false,
    skipAuthRetry = false,
  } = {}
) {
  if (requireAuth && !accessToken) {
    throw new Error('Nu esti autentificat.')
  }

  let response = await executeRequest(path, { method, accessToken, body, headers })
  if (response.status === 401 && requireAuth && !skipAuthRetry && authRuntime.refreshSession) {
    try {
      const refreshedSession = await refreshWithRuntimeSession()
      response = await executeRequest(path, {
        method,
        accessToken: refreshedSession?.accessToken || refreshedSession?.access_token || null,
        body,
        headers,
      })
    } catch (error) {
      authRuntime.onAuthFailure?.()
      throw error
    }
  }

  if (!response.ok) {
    if (response.status === 401) {
      authRuntime.onAuthFailure?.()
    }
    await createHttpError(response)
  }

  return response
}
