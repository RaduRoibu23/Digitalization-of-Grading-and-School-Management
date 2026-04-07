import { Outlet } from 'react-router-dom'
import Sidebar from '../components/layout/Sidebar'

export default function DashboardPage({ roles }) {
  return (
    <div className="appShell">
      <Sidebar roles={roles} />

      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
