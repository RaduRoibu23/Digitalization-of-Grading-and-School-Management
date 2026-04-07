import AuditConsolePage from '../../pages/AuditConsolePage'
import NotificationCenter, { NotificationToastStack } from '../ui/NotificationCenter'

export default function Header({
  accessToken,
  canViewAudit,
  notifications,
  notificationsLoading,
  onDismissToast,
  onLogout,
  onMarkAllNotificationsRead,
  onMarkNotificationRead,
  onOpenNotification,
  onRefreshNotifications,
  status,
  toasts,
  unreadCount,
}) {
  return (
    <>
      <header className="header">
        <div className="brand">
          <div className="logo" aria-hidden="true"></div>
          <div>
            <div className="eyebrow">Academic Workspace</div>
            <h1>Digitalization of Grading and School Management</h1>
            <div className="sub">Spatiu academic fluid pentru orar, catalog, documente si asistenta scolara.</div>
          </div>
        </div>

        <div className="headerInfoRail">
          <div id="status-badge" className="badge statusBadge">
            <span className="statusDot"></span>
            Status: {status}
          </div>

          <NotificationCenter
            notifications={notifications}
            unreadCount={unreadCount}
            loading={notificationsLoading}
            onRefresh={onRefreshNotifications}
            onMarkRead={onMarkNotificationRead}
            onMarkAllRead={onMarkAllNotificationsRead}
            onOpenNotification={onOpenNotification}
          />

          <div className="headerUtilityButtons">
            {canViewAudit && <AuditConsolePage accessToken={accessToken} />}

            <button className="btn danger" type="button" onClick={onLogout}>
              Logout
            </button>
          </div>
        </div>
      </header>

      <NotificationToastStack
        items={toasts}
        onDismiss={onDismissToast}
        onOpenNotification={onOpenNotification}
      />
    </>
  )
}
