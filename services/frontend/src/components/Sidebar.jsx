import React from "react";

export const NAV_ITEMS = [
  { id: "my-timetable", label: "Orarul meu", allowedRoles: ["student", "professor"] },
  { id: "class-timetable", label: "Orar pe clasa", allowedRoles: ["secretariat", "scheduler", "admin", "sysadmin"] },
  { id: "generate", label: "Genereaza orar", allowedRoles: ["secretariat", "scheduler", "admin", "sysadmin"] },
  { id: "students", label: "Studenti", allowedRoles: ["secretariat", "scheduler", "admin", "sysadmin"] },
  { id: "catalog", label: "Catalog", allowedRoles: ["student", "professor", "secretariat", "admin", "sysadmin"] },
  { id: "profile", label: "Date personale", allowedRoles: [] },
];

function hasAnyRole(userRoles, allowedRoles) {
  if (!allowedRoles || allowedRoles.length === 0) return true;
  return userRoles.some((role) => allowedRoles.includes(role));
}

export default function Sidebar({ roles, activeId, onSelect }) {
  const visible = NAV_ITEMS.filter((item) => hasAnyRole(roles, item.allowedRoles));

  return (
    <aside className="sidebar">
      <div className="sidebarTitle">Meniu</div>

      <div className="sidebarGroup">
        {visible.map((item) => (
          <button
            key={item.id}
            className={`navBtn ${activeId === item.id ? "active" : ""}`}
            onClick={() => onSelect(item.id)}
          >
            {item.label}
          </button>
        ))}
      </div>

      <div className="sidebarFooter">
        <div className="mutedSmall">Roluri:</div>
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
  );
}
