import { useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import Header from '../components/layout/Header'
import { apiGet, apiPatch } from '../services/apiService'
import { clearApiAuthRuntime, configureApiAuthRuntime } from '../services/apiClient'
import { clearSession, loadSession, refreshAccessToken, rolesFromToken, tokenExpiresAtMs } from '../services/authService'
import { NOTIFICATION_REFRESH_EVENT } from '../services/appEvents'
import AppRoutes from './routes'

const NOTIFICATION_POLL_MS = 5000
const REFRESH_LEEWAY_MS = 45000

function resolveModuleTheme(pathname) {
  if (pathname === '/app' || pathname === '/app/') return 'dashboard'
  if (pathname.startsWith('/app/catalog')) return 'catalog'
  if (pathname.startsWith('/app/documente')) return 'documents'
  if (pathname.startsWith('/app/feedback')) return 'feedback'
  if (pathname.startsWith('/app/profil')) return 'profile'
  if (pathname.startsWith('/app/orarul-meu') || pathname.startsWith('/app/orar-pe-clasa') || pathname.startsWith('/app/genereaza-orar')) {
    return 'timetable'
  }
  if (pathname.startsWith('/app/utilizatori') || pathname.startsWith('/app/creeaza-cont')) return 'admin'
  if (pathname.startsWith('/login')) return 'auth'
  return 'home'
}

function normalizeNotifications(payload) {
  return Array.isArray(payload) ? payload : []
}

function toastFromNotification(notification) {
  return {
    id: notification.id,
    title: notification.title,
    message: notification.message,
    category: notification.category,
    action_path: notification.action_path,
    read: notification.read,
  }
}

function App() {
  const location = useLocation()
  const navigate = useNavigate()
  const [session, setSession] = useState(() => loadSession())
  const [notifications, setNotifications] = useState([])
  const [notificationsError, setNotificationsError] = useState('')
  const [unreadCount, setUnreadCount] = useState(0)
  const [notificationsLoading, setNotificationsLoading] = useState(false)
  const [toasts, setToasts] = useState([])
  const seenNotificationIdsRef = useRef(new Set())
  const notificationsBootstrappedRef = useRef(false)

  const roles = useMemo(() => rolesFromToken(session?.accessToken), [session?.accessToken])
  const status = session?.accessToken ? 'autentificat' : 'neautentificat'
  const moduleTheme = resolveModuleTheme(location.pathname)
  const isCenteredPublicRoute = ['/login'].includes(location.pathname)
  const isLandingRoute = location.pathname === '/'
  const isPublicRoute = isCenteredPublicRoute || isLandingRoute
  const canViewAudit = roles.includes('admin') || roles.includes('sysadmin')

  const resetNotificationState = () => {
    setNotifications([])
    setUnreadCount(0)
    setNotificationsLoading(false)
    setNotificationsError('')
    setToasts([])
    seenNotificationIdsRef.current = new Set()
    notificationsBootstrappedRef.current = false
  }

  const handleLogout = () => {
    clearSession()
    clearApiAuthRuntime()
    setSession(null)
    resetNotificationState()
  }

  const handleLogin = (tokens) => {
    setSession(tokens)
  }

  const refreshSessionSafely = async (refreshTokenOverride) => {
    const refreshToken = refreshTokenOverride || session?.refreshToken || loadSession()?.refreshToken
    if (!refreshToken) {
      throw new Error('Nu exista refresh token activ.')
    }

    const nextTokens = await refreshAccessToken(refreshToken)
    setSession(nextTokens)
    return nextTokens
  }

  useEffect(() => {
    configureApiAuthRuntime({
      getSession: () => loadSession() || session,
      refreshSession: refreshSessionSafely,
      onAuthFailure: handleLogout,
    })

    return () => {
      clearApiAuthRuntime()
    }
  }, [session?.accessToken, session?.refreshToken])

  useEffect(() => {
    if (!session?.accessToken || !session?.refreshToken) {
      return undefined
    }

    const expiresAt = tokenExpiresAtMs(session.accessToken)
    if (!expiresAt) {
      return undefined
    }

    const delay = Math.max(0, expiresAt - Date.now() - REFRESH_LEEWAY_MS)
    const timeoutId = window.setTimeout(async () => {
      try {
        await refreshSessionSafely(session.refreshToken)
      } catch {
        handleLogout()
      }
    }, delay)

    return () => window.clearTimeout(timeoutId)
  }, [session?.accessToken, session?.refreshToken])

  async function refreshNotificationState({ showLoading = false } = {}) {
    if (!session?.accessToken) {
      resetNotificationState()
      return
    }

    if (showLoading) {
      setNotificationsLoading(true)
    }

    try {
      const [notificationPayload, countPayload] = await Promise.all([
        apiGet('/notifications/me?limit=24', session.accessToken),
        apiGet('/notifications/unread-count', session.accessToken),
      ])

      const nextNotifications = normalizeNotifications(notificationPayload)
      const nextUnreadCount = Number(countPayload?.unread_count ?? 0)

      setNotifications(nextNotifications)
      setUnreadCount(nextUnreadCount)
      setNotificationsError('')

      const seenIds = seenNotificationIdsRef.current
      const newUnreadNotifications = nextNotifications.filter((item) => !item.read && !seenIds.has(item.id))
      nextNotifications.forEach((item) => seenIds.add(item.id))

      if (notificationsBootstrappedRef.current && newUnreadNotifications.length > 0) {
        setToasts((current) => {
          const existingIds = new Set(current.map((item) => item.id))
          const appended = newUnreadNotifications
            .filter((item) => !existingIds.has(item.id))
            .map(toastFromNotification)
          return [...appended, ...current].slice(0, 5)
        })
      }

      if (!notificationsBootstrappedRef.current) {
        notificationsBootstrappedRef.current = true
      }
    } catch {
      setNotificationsError('Nu s-au putut incarca notificarile acum.')
    } finally {
      setNotificationsLoading(false)
    }
  }

  useEffect(() => {
    if (!session?.accessToken) {
      resetNotificationState()
      return undefined
    }

    refreshNotificationState({ showLoading: true })
    const intervalId = window.setInterval(() => {
      refreshNotificationState()
    }, NOTIFICATION_POLL_MS)

    function handleFocusRefresh() {
      refreshNotificationState()
    }

    function handleVisibilityChange() {
      if (document.visibilityState === 'visible') {
        refreshNotificationState()
      }
    }

    function handleNotificationRefreshRequest() {
      refreshNotificationState()
    }

    window.addEventListener('focus', handleFocusRefresh)
    window.addEventListener(NOTIFICATION_REFRESH_EVENT, handleNotificationRefreshRequest)
    document.addEventListener('visibilitychange', handleVisibilityChange)

    return () => {
      window.clearInterval(intervalId)
      window.removeEventListener('focus', handleFocusRefresh)
      window.removeEventListener(NOTIFICATION_REFRESH_EVENT, handleNotificationRefreshRequest)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [session?.accessToken])

  async function handleMarkNotificationRead(notificationId) {
    if (!session?.accessToken) return
    try {
      const updated = await apiPatch(`/notifications/${notificationId}/read`, {}, session.accessToken)
      setNotifications((current) =>
        current.map((item) => (item.id === notificationId ? { ...item, ...updated } : item))
      )
      setUnreadCount((current) => Math.max(0, current - 1))
      setToasts((current) => current.filter((item) => item.id !== notificationId))
    } catch {
    }
  }

  async function handleMarkAllNotificationsRead() {
    if (!session?.accessToken || unreadCount === 0) return
    try {
      await apiPatch('/notifications/read-all', {}, session.accessToken)
      setNotifications((current) =>
        current.map((item) => ({
          ...item,
          read: true,
          read_at: item.read_at || new Date().toISOString(),
        }))
      )
      setUnreadCount(0)
      setToasts([])
    } catch {
    }
  }

  async function handleOpenNotification(notification) {
    if (!notification) return
    if (!notification.read) {
      await handleMarkNotificationRead(notification.id)
    }
    if (notification.action_path) {
      navigate(notification.action_path)
    }
  }

  function handleDismissToast(toastId) {
    setToasts((current) => current.filter((item) => item.id !== toastId))
  }

  return (
    <div className={`appRoot ${isPublicRoute ? 'appRootAuth' : 'appRootDashboard'}`} data-module={moduleTheme}>
      <div className="siteBackdrop" aria-hidden="true">
        <span className="siteOrb orbA"></span>
        <span className="siteOrb orbB"></span>
        <span className="siteOrb orbC"></span>
        <span className="siteMesh"></span>
      </div>

      <div className="appFrame">
        {!isPublicRoute && (
          <Header
            accessToken={session?.accessToken}
            canViewAudit={canViewAudit}
            notificationsError={notificationsError}
            notifications={notifications}
            notificationsLoading={notificationsLoading}
            onDismissToast={handleDismissToast}
            onLogout={handleLogout}
            onMarkAllNotificationsRead={handleMarkAllNotificationsRead}
            onMarkNotificationRead={handleMarkNotificationRead}
            onOpenNotification={handleOpenNotification}
            onRefreshNotifications={() => refreshNotificationState({ showLoading: true })}
            status={status}
            toasts={toasts}
            unreadCount={unreadCount}
          />
        )}

        <div className={`routeShell ${isCenteredPublicRoute ? 'routeShellAuth' : isLandingRoute ? 'routeShellLanding' : 'routeShellApp'}`}>
          <AppRoutes
            session={session}
            roles={roles}
            onLogin={handleLogin}
          />
        </div>
      </div>
    </div>
  )
}

export default App
