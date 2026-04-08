import { Outlet, useLocation } from 'react-router-dom'
import Sidebar from '../components/layout/Sidebar'

export default function DashboardPage({ roles }) {
  const location = useLocation()

  return (
    <div className="appShell">
      <Sidebar roles={roles} />

      <main key={location.pathname} className="content pageTransitionShell">
        <Outlet />
      </main>
    </div>
  )
}
