import { Outlet } from 'react-router-dom'
import Sidebar from '../components/layout/Sidebar'
import NotificationPopup from '../components/ui/NotificationPopup'
import { loadSession, refreshAccessToken } from '../services/authService'
import AuditConsolePage from './AuditConsolePage'

export default function DashboardPage({ accessToken, roles, onRefreshToken, onLogout }) {
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
      <AuditConsolePage accessToken={accessToken} />
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

        <Outlet />
      </main>
    </div>
  )
}
