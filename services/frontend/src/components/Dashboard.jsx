import { useEffect, useMemo, useState } from "react";
import { rolesFromToken, tokenExpiryText, loadSession, refreshAccessToken } from "../services/authService";
import Sidebar, { NAV_ITEMS } from "./Sidebar";
import TimetableScreen from "./TimetableScreen";
import GenerateTimetableScreen from "./GenerateTimetableScreen";
import NotificationPopup from "./NotificationPopup";
import ProfileScreen from "./ProfileScreen";
import StudentsScreen from "./StudentsScreen";

function hasAnyRole(userRoles, allowedRoles) {
  if (!allowedRoles || allowedRoles.length === 0) return true;
  return userRoles.some((role) => allowedRoles.includes(role));
}

function defaultActionForRoles(roles) {
  if (roles.includes("student") || roles.includes("professor")) return "my-timetable";
  return "class-timetable";
}

export default function Dashboard({ accessToken, idToken, onRefreshToken, onLogout }) {
  const roles = useMemo(() => rolesFromToken(accessToken), [accessToken]);
  const expiry = tokenExpiryText(accessToken);

  const visibleActionIds = useMemo(() => {
    return NAV_ITEMS
      .filter((item) => hasAnyRole(roles, item.allowedRoles))
      .map((item) => item.id);
  }, [roles]);

  const [active, setActive] = useState(() => defaultActionForRoles(roles));

  useEffect(() => {
    const nextActive = defaultActionForRoles(roles);
    if (!visibleActionIds.includes(active)) setActive(nextActive);
  }, [roles, visibleActionIds, active]);

  const handleRefreshToken = async () => {
    try {
      const session = loadSession();
      if (session?.refreshToken) {
        const tokens = await refreshAccessToken(session.refreshToken);
        onRefreshToken(tokens);
      }
    } catch (error) {
      console.error("Token refresh failed:", error);
    }
  };

  return (
    <div className="appShell">
      <NotificationPopup accessToken={accessToken} />
      <Sidebar roles={roles} activeId={active} onSelect={setActive} />

      <main className="content">
        <div className="topBar">
          <div className="topBarLeft">
            <div className="topTitle">Timetable Management</div>
            <div className="topSub">Token exp: {expiry}</div>
          </div>
          <div className="topBarRight">
            <button className="btn" onClick={handleRefreshToken}>Refresh token</button>
            <button className="btn danger" onClick={onLogout}>Logout</button>
          </div>
        </div>

        {active === "my-timetable" && (
          <TimetableScreen accessToken={accessToken} roles={roles} mode="my" />
        )}

        {active === "class-timetable" && (
          <TimetableScreen accessToken={accessToken} roles={roles} mode="class" />
        )}

        {active === "generate" && (
          <GenerateTimetableScreen accessToken={accessToken} roles={roles} />
        )}

        {active === "profile" && (
          <ProfileScreen accessToken={accessToken} roles={roles} />
        )}

        {active === "students" && (
          <StudentsScreen accessToken={accessToken} roles={roles} />
        )}
      </main>
    </div>
  );
}
