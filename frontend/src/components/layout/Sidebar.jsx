import React from 'react'
import { NavLink } from 'react-router-dom'

export const NAV_ITEMS = [
  { id: 'dashboard-home', path: '', label: 'Panou', allowedRoles: [] },
  { id: 'my-timetable', path: 'orarul-meu', label: 'Orarul meu', allowedRoles: ['student', 'parent', 'professor'] },
  { id: 'class-timetable', path: 'orar-pe-clasa', label: 'Orar pe clasa', allowedRoles: ['secretariat', 'scheduler', 'admin', 'sysadmin'] },
  { id: 'generate', path: 'genereaza-orar', label: 'Genereaza orar', allowedRoles: ['secretariat', 'scheduler', 'admin', 'sysadmin'] },
  { id: 'users', path: 'utilizatori', label: 'Utilizatori', allowedRoles: ['secretariat', 'sysadmin'] },
  { id: 'create-account', path: 'creeaza-cont', label: 'Creeaza cont', allowedRoles: ['sysadmin'] },
  { id: 'catalog', path: 'catalog', label: 'Catalog', allowedRoles: ['student', 'parent', 'professor', 'secretariat', 'admin', 'sysadmin'] },
  { id: 'documents', path: 'documente', label: 'Documente', allowedRoles: ['student', 'parent', 'secretariat', 'sysadmin'] },
  { id: 'feedback', path: 'feedback', label: 'Asistenta', allowedRoles: [] },
  { id: 'profile', path: 'profil', label: 'Date personale', allowedRoles: [] },
]

function hasAnyRole(userRoles, allowedRoles) {
  if (!allowedRoles || allowedRoles.length === 0) return true
  return userRoles.some((role) => allowedRoles.includes(role))
}

export default function Sidebar({ roles }) {
  const visible = NAV_ITEMS.filter((item) => hasAnyRole(roles, item.allowedRoles))

  return (
    <aside className="sidebar">
      <div className="sidebarIntro">
        <div className="sidebarKicker">Navigare</div>
        <div className="sidebarTitle">Meniu principal</div>
        <div className="sidebarText">Modulele disponibile apar in functie de rolul autentificat.</div>
      </div>

      <div className="sidebarGroup">
        {visible.map((item, index) => (
          <NavLink
            key={item.id}
            className={({ isActive }) => `navBtn ${isActive ? 'active' : ''}`.trim()}
            to={item.path ? `/app/${item.path}` : '/app'}
            end={!item.path}
          >
            <span className="navIndex">{String(index + 1).padStart(2, '0')}</span>
            <span className="navText">{item.label}</span>
          </NavLink>
        ))}
      </div>

      <div className="sidebarFooter">
        <div className="mutedSmall">Roluri active</div>
        <div className="rolesWrap">
          {roles.length === 0 ? (
            <span className="pill muted">-</span>
          ) : (
            roles.map((role) => (
              <span key={role} className="pill">
                {role}
              </span>
            ))
          )}
        </div>
      </div>
    </aside>
  )
}
