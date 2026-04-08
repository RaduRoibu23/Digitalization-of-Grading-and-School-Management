const VIEW_STATE_PREFIX = 'timetable.view'

function sessionStorageRef() {
  if (typeof window === 'undefined') return null
  try {
    return window.sessionStorage
  } catch {
    return null
  }
}

function storageKey(viewKey) {
  return `${VIEW_STATE_PREFIX}:${viewKey}`
}

export function loadViewState(viewKey, defaults = {}) {
  const storage = sessionStorageRef()
  if (!storage) {
    return { ...defaults }
  }

  const raw = storage.getItem(storageKey(viewKey))
  if (!raw) {
    return { ...defaults }
  }

  try {
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return { ...defaults }
    }
    return {
      ...defaults,
      ...parsed,
    }
  } catch {
    return { ...defaults }
  }
}

export function saveViewState(viewKey, value) {
  const storage = sessionStorageRef()
  if (!storage) return

  try {
    storage.setItem(storageKey(viewKey), JSON.stringify(value))
  } catch {
  }
}

export function clearViewState(viewKey) {
  const storage = sessionStorageRef()
  if (!storage) return

  storage.removeItem(storageKey(viewKey))
}
