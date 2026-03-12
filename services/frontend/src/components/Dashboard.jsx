import { useMemo } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { rolesFromToken, loadSession, refreshAccessToken } from '../services/authService'
import Sidebar, { NAV_ITEMS } from './Sidebar'
import TimetableScreen from './TimetableScreen'
import GenerateTimetableScreen from './GenerateTimetableScreen'
import NotificationPopup from './NotificationPopup'
import ProfileScreen from './ProfileScreen'
import StudentsScreen from './StudentsScreen'
import CatalogScreen from './CatalogScreen'

function hasAnyRole(userRoles, allowedRoles) {
  if (!allowedRoles || allowedRoles.length === 0) return true
  return userRoles.some((role) => allowedRoles.includes(role))
}

function defaultPathForRoles(roles, visibleItems) {
  const preferred = roles.includes('student') || roles.includes('professor') ? 'orarul-meu' : 'orar-pe-clasa'
  return visibleItems.some((item) => item.path === preferred) ? preferred : visibleItems[0]?.path || 'profil'
}

export default function Dashboard({ accessToken, onRefreshToken, onLogout }) {
  const roles = useMemo(() => rolesFromToken(accessToken), [accessToken])

  const visibleItems = useMemo(() => {
    return NAV_ITEMS.filter((item) => hasAnyRole(roles, item.allowedRoles))
  }, [roles])

  const defaultPath = useMemo(() => defaultPathForRoles(roles, visibleItems), [roles, visibleItems])

  const handleRefreshToken = async () => {
    try {
      const session = loadSession()
      if (session?.refreshToken) {
        const tokens = await refreshAccessToken(session.refreshToken)
        onRefreshToken(tokens)
      }
    } catch (error) {
      console.error('Token refresh failed:', error)
    }
  }

  return (
    <div className="appShell">
      <NotificationPopup accessToken={accessToken} />
      <Sidebar roles={roles} />

      <main className="content">
        <section className="topBar topBarCompact">
          <div className="topBarLeft">
            <div className="topTitle">Digitalization of Grading</div>
            <div className="topSub">Spatiu de lucru pentru orar, catalog, notificari si administrare scolara.</div>
          </div>

          <div className="topBarRight">
            <button className="btn" onClick={handleRefreshToken}>Refresh token</button>
            <button className="btn danger" onClick={onLogout}>Logout</button>
          </div>
        </section>

        <Routes>
          <Route index element={<Navigate to={defaultPath} replace />} />
          {visibleItems.some((item) => item.path === 'orarul-meu') && (
            <Route path="orarul-meu" element={<TimetableScreen accessToken={accessToken} roles={roles} mode="my" />} />
          )}
          {visibleItems.some((item) => item.path === 'orar-pe-clasa') && (
            <Route path="orar-pe-clasa" element={<TimetableScreen accessToken={accessToken} roles={roles} mode="class" />} />
          )}
          {visibleItems.some((item) => item.path === 'genereaza-orar') && (
            <Route path="genereaza-orar" element={<GenerateTimetableScreen accessToken={accessToken} roles={roles} />} />
          )}
          {visibleItems.some((item) => item.path === 'studenti') && (
            <Route path="studenti" element={<StudentsScreen accessToken={accessToken} roles={roles} />} />
          )}
          {visibleItems.some((item) => item.path === 'catalog') && (
            <Route path="catalog" element={<CatalogScreen accessToken={accessToken} roles={roles} />} />
          )}
          {visibleItems.some((item) => item.path === 'profil') && (
            <Route path="profil" element={<ProfileScreen accessToken={accessToken} roles={roles} />} />
          )}
          <Route path="*" element={<Navigate to={defaultPath} replace />} />
        </Routes>
      </main>
    </div>
  )
}
