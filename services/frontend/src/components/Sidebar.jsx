import React from 'react'
import { NavLink } from 'react-router-dom'

export const NAV_ITEMS = [
  { id: 'my-timetable', path: 'orarul-meu', label: 'Orarul meu', allowedRoles: ['student', 'professor'] },
  { id: 'class-timetable', path: 'orar-pe-clasa', label: 'Orar pe clasa', allowedRoles: ['secretariat', 'scheduler', 'admin', 'sysadmin'] },
  { id: 'generate', path: 'genereaza-orar', label: 'Genereaza orar', allowedRoles: ['secretariat', 'scheduler', 'admin', 'sysadmin'] },
  { id: 'students', path: 'studenti', label: 'Studenti', allowedRoles: ['secretariat', 'scheduler', 'admin', 'sysadmin'] },
  { id: 'catalog', path: 'catalog', label: 'Catalog', allowedRoles: ['student', 'professor', 'secretariat', 'admin', 'sysadmin'] },
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
        <div className="sidebarKicker">Navigation</div>
        <div className="sidebarTitle">Meniu principal</div>
        <div className="sidebarText">Modulele disponibile apar in functie de rolul autentificat.</div>
      </div>

      <div className="sidebarGroup">
        {visible.map((item, index) => (
          <NavLink
            key={item.id}
            className={({ isActive }) => `navBtn ${isActive ? 'active' : ''}`.trim()}
            to={`/app/${item.path}`}
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
