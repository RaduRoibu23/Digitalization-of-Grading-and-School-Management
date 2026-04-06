import { useEffect, useMemo, useState } from 'react'
import { useLocation } from 'react-router-dom'
import Header from '../components/layout/Header'
import { clearSession, loadSession, rolesFromToken } from '../services/authService'
import AppRoutes from './routes'

function App() {
  const location = useLocation()
  const [session, setSession] = useState(null)

  useEffect(() => {
    const savedSession = loadSession()
    if (savedSession?.accessToken) {
      setSession(savedSession)
    }
  }, [])

  const roles = useMemo(() => rolesFromToken(session?.accessToken), [session?.accessToken])
  const status = session?.accessToken ? 'autentificat' : 'neautentificat'

  const handleLogin = (tokens) => {
    setSession(tokens)
  }

  const handleLogout = () => {
    clearSession()
    setSession(null)
  }

  const handleRefreshToken = (tokens) => {
    setSession(tokens)
  }

  const isCenteredPublicRoute = ['/login'].includes(location.pathname)
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
          <AppRoutes
            session={session}
            roles={roles}
            onLogin={handleLogin}
            onRefreshToken={handleRefreshToken}
            onLogout={handleLogout}
          />
        </div>
      </div>
    </div>
  )
}

export default App
