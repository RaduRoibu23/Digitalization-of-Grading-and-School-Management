import { useEffect, useState } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { loadSession, clearSession } from './services/authService'
import HomePage from './components/HomePage'
import Login from './components/Login'
import Register from './components/Register'
import Dashboard from './components/Dashboard'
import Header from './components/Header'

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

function App() {
  const location = useLocation()
  const [session, setSession] = useState(null)
  const [status, setStatus] = useState('neautentificat')

  useEffect(() => {
    const savedSession = loadSession()
    if (savedSession?.accessToken) {
      setSession(savedSession)
      setStatus('autentificat')
    }
  }, [])

  const handleLogin = (tokens) => {
    setSession(tokens)
    setStatus('autentificat')
  }

  const handleLogout = () => {
    clearSession()
    setSession(null)
    setStatus('neautentificat')
  }

  const handleRefreshToken = (tokens) => {
    setSession(tokens)
  }

  const isCenteredPublicRoute = location.pathname === '/login' || location.pathname === '/register'
  const isLandingRoute = location.pathname === '/'
  const isPublicRoute = isCenteredPublicRoute || isLandingRoute

  return (
    <div className={`appRoot ${isPublicRoute ? 'appRootAuth' : 'appRootDashboard'}`}>
      <div className="siteBackdrop" aria-hidden="true">
        <span className="siteOrb orbA"></span>
        <span className="siteOrb orbB"></span>
        <span className="siteOrb orbC"></span>
        <span className="siteMesh"></span>
      </div>

      <div className="appFrame">
        {!isPublicRoute && <Header status={status} />}

        <div className={`routeShell ${isCenteredPublicRoute ? 'routeShellAuth' : isLandingRoute ? 'routeShellLanding' : 'routeShellApp'}`}>
          <Routes>
            <Route path="/" element={session?.accessToken ? <Navigate to="/app" replace /> : <HomePage />} />
            <Route
              path="/login"
              element={(
                <GuestRoute session={session}>
                  <Login onLogin={handleLogin} />
                </GuestRoute>
              )}
            />
            <Route
              path="/register"
              element={(
                <GuestRoute session={session}>
                  <Register />
                </GuestRoute>
              )}
            />
            <Route
              path="/app/*"
              element={(
                <ProtectedRoute session={session}>
                  <Dashboard
                    accessToken={session?.accessToken}
                    idToken={session?.idToken}
                    onRefreshToken={handleRefreshToken}
                    onLogout={handleLogout}
                  />
                </ProtectedRoute>
              )}
            />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </div>
      </div>
    </div>
  )
}

export default App
