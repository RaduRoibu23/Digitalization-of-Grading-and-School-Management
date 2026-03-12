import React, { useEffect, useState, useCallback } from "react";
import { apiPatch, apiGet } from "../services/apiService";

export default function NotificationPopup({ accessToken }) {
  const [notifications, setNotifications] = useState([]);

  const loadNotifications = useCallback(async () => {
    if (!accessToken) return;

    try {
      const data = await apiGet("/notifications/me?unread_only=true", accessToken);
      setNotifications(Array.isArray(data) ? data : []);
    } catch {
    }
  }, [accessToken]);

  useEffect(() => {
    if (!accessToken) return;

    loadNotifications();
    const interval = setInterval(loadNotifications, 3000);

    return () => clearInterval(interval);
  }, [accessToken, loadNotifications]);

  const dismissNotification = async (id) => {
    try {
      await apiPatch(`/notifications/${id}/read`, {}, accessToken);
      setNotifications((prev) => prev.filter((notification) => notification.id !== id));
    } catch {
    }
  };

  if (notifications.length === 0) return null;

  return (
    <div className="notificationContainer">
      {notifications.map((notification) => (
        <div key={notification.id} className="notificationPopup">
          <div className="notificationContent">
            <div className="notificationMessage">{notification.message}</div>
            <div className="notificationTime">
              {new Date(notification.created_at).toLocaleString()}
            </div>
          </div>
          <button
            className="notificationClose"
            onClick={() => dismissNotification(notification.id)}
            title="Inchide notificarea"
          >
            X
          </button>
        </div>
      ))}
    </div>
  );
}
