import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'

const STORAGE_KEY = 'theme'
const ThemeContext = createContext(null)

function readSystemTheme() {
  if (typeof window === 'undefined' || !window.matchMedia) return 'dark'
  return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark'
}

function readStoredTheme() {
  try {
    const value = window.localStorage.getItem(STORAGE_KEY)
    return value === 'light' || value === 'dark' ? value : null
  } catch {
    return null
  }
}

function applyTheme(theme) {
  const root = document.documentElement
  root.setAttribute('data-theme', theme)
  root.style.colorScheme = theme
}

export function ThemeProvider({ children }) {
  const [theme, setThemeState] = useState(() => readStoredTheme() || readSystemTheme())
  const [explicit, setExplicit] = useState(() => readStoredTheme() != null)

  useEffect(() => {
    applyTheme(theme)
  }, [theme])

  // Keep following the OS preference until the user makes an explicit choice.
  useEffect(() => {
    if (explicit || typeof window === 'undefined' || !window.matchMedia) return undefined
    const query = window.matchMedia('(prefers-color-scheme: light)')
    const handleChange = (event) => setThemeState(event.matches ? 'light' : 'dark')
    query.addEventListener('change', handleChange)
    return () => query.removeEventListener('change', handleChange)
  }, [explicit])

  const setTheme = useCallback((next) => {
    setThemeState(next)
    setExplicit(true)
    try {
      window.localStorage.setItem(STORAGE_KEY, next)
    } catch {
      /* storage unavailable — keep the in-memory preference */
    }
  }, [])

  const toggleTheme = useCallback(() => {
    setThemeState((current) => {
      const next = current === 'light' ? 'dark' : 'light'
      setExplicit(true)
      try {
        window.localStorage.setItem(STORAGE_KEY, next)
      } catch {
        /* ignore */
      }
      return next
    })
  }, [])

  const value = useMemo(() => ({ theme, setTheme, toggleTheme }), [theme, setTheme, toggleTheme])

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme() {
  const context = useContext(ThemeContext)
  if (!context) {
    throw new Error('useTheme must be used within a ThemeProvider')
  }
  return context
}
