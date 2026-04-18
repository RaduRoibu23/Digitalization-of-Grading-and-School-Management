import { Link, Navigate, Route, Routes } from 'react-router-dom'
import { NAV_ITEMS } from '../components/layout/Sidebar'
import AccountCreationPage from '../pages/AccountCreationPage'
import AppDashboardPage from '../pages/AppDashboardPage'
import CatalogPage from '../pages/CatalogPage'
import DashboardPage from '../pages/DashboardPage'
import DocumentsPage from '../pages/DocumentsPage'
import FeedbackPage from '../pages/FeedbackPage'
import GenerateTimetablePage from '../pages/GenerateTimetablePage'
import HomePage from '../pages/HomePage'
import LoginPage from '../pages/LoginPage'
import ProfilePage from '../pages/ProfilePage'
import StudentsPage from '../pages/StudentsPage'
import TimetablePage from '../pages/TimetablePage'

function ProtectedRoute({ session, children }) {
  if (!session?.accessToken) {
    return <Navigate to="/login" replace />
  }
  return children
}

function GuestRoute({ session, children }) {
  if (session?.accessToken) {
    return <Navigate to="/app" replace />
  }
  return children
}

function hasAnyRole(userRoles, allowedRoles) {
  if (!allowedRoles || allowedRoles.length === 0) return true
  return userRoles.some((role) => allowedRoles.includes(role))
}

function defaultPathForRoles(roles, visibleItems) {
  const navigableItems = visibleItems.filter((item) => item.path)
  const preferred = roles.includes('student') || roles.includes('parent') || roles.includes('professor') ? 'orarul-meu' : 'orar-pe-clasa'
  return navigableItems.some((item) => item.path === preferred) ? preferred : navigableItems[0]?.path || 'profil'
}

function RouteAccessNotice({ defaultPath }) {
  return (
    <section className="contentCard">
      <div className="contentHeader">
        <div>
          <div className="title">Ruta indisponibila</div>
          <div className="subtitle">Pagina ceruta nu exista pentru rolul tau sau nu este o ruta valida.</div>
        </div>
      </div>
      <div className="banner error" style={{ marginBottom: 16 }}>
        Nu ai acces la aceasta pagina.
      </div>
      <Link className="btn primary" to={`/app/${defaultPath}`}>
        Inapoi la modulul meu
      </Link>
    </section>
  )
}

export default function AppRoutes({ session, roles, onLogin }) {
  const visibleItems = NAV_ITEMS.filter((item) => hasAnyRole(roles, item.allowedRoles))
  const defaultPath = defaultPathForRoles(roles, visibleItems)

  return (
    <Routes>
      <Route path="/" element={session?.accessToken ? <Navigate to="/app" replace /> : <HomePage />} />
      <Route
        path="/login"
        element={(
          <GuestRoute session={session}>
            <LoginPage onLogin={onLogin} />
          </GuestRoute>
        )}
      />
      <Route
        path="/app"
        element={(
          <ProtectedRoute session={session}>
            <DashboardPage
              roles={roles}
            />
          </ProtectedRoute>
        )}
      >
        <Route index element={<AppDashboardPage accessToken={session?.accessToken} roles={roles} />} />
        {visibleItems.some((item) => item.path === 'orarul-meu') && (
          <Route path="orarul-meu" element={<TimetablePage accessToken={session?.accessToken} roles={roles} mode="my" />} />
        )}
        {visibleItems.some((item) => item.path === 'orar-pe-clasa') && (
          <Route path="orar-pe-clasa" element={<TimetablePage accessToken={session?.accessToken} roles={roles} mode="class" />} />
        )}
        {visibleItems.some((item) => item.path === 'genereaza-orar') && (
          <Route path="genereaza-orar" element={<GenerateTimetablePage accessToken={session?.accessToken} roles={roles} />} />
        )}
        {visibleItems.some((item) => item.path === 'utilizatori') && (
          <Route path="utilizatori" element={<StudentsPage accessToken={session?.accessToken} roles={roles} />} />
        )}
        {visibleItems.some((item) => item.path === 'creeaza-cont') && (
          <Route path="creeaza-cont" element={<AccountCreationPage accessToken={session?.accessToken} roles={roles} />} />
        )}
        {visibleItems.some((item) => item.path === 'catalog') && (
          <Route path="catalog" element={<CatalogPage accessToken={session?.accessToken} roles={roles} />} />
        )}
        {visibleItems.some((item) => item.path === 'documente') && (
          <Route path="documente" element={<DocumentsPage accessToken={session?.accessToken} roles={roles} />} />
        )}
        {visibleItems.some((item) => item.path === 'feedback') && (
          <Route path="feedback" element={<FeedbackPage accessToken={session?.accessToken} roles={roles} />} />
        )}
        {visibleItems.some((item) => item.path === 'feedback') && (
          <Route path="feedback/:feedbackId" element={<FeedbackPage accessToken={session?.accessToken} roles={roles} />} />
        )}
        {visibleItems.some((item) => item.path === 'profil') && (
          <Route path="profil" element={<ProfilePage accessToken={session?.accessToken} roles={roles} />} />
        )}
        <Route path="studenti" element={<Navigate to="/app/utilizatori" replace />} />
        <Route path="*" element={<RouteAccessNotice defaultPath={defaultPath} />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
