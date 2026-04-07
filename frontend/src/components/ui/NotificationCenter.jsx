import { useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

function formatDate(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

function categoryLabel(category) {
  switch (category) {
    case 'catalog':
      return 'Catalog'
    case 'documents':
      return 'Documente'
    case 'feedback':
      return 'Help'
    case 'timetable':
      return 'Orar'
    default:
      return 'Sistem'
  }
}

export function NotificationToastStack({ items, onDismiss, onOpenNotification }) {
  if (!Array.isArray(items) || items.length === 0) {
    return null
  }

  return createPortal(
    <div className="notificationToastStack" aria-live="polite">
      {items.map((item) => (
        <article key={item.id} className="notificationToast">
          <button
            className="notificationToastBody"
            type="button"
            onClick={() => onOpenNotification(item)}
          >
            <span className={`notificationCategory notificationCategory-${item.category || 'system'}`}>
              {categoryLabel(item.category)}
            </span>
            <strong>{item.title || 'Notificare'}</strong>
            <span>{item.message}</span>
          </button>
          <button
            className="notificationToastDismiss"
            type="button"
            onClick={() => onDismiss(item.id)}
            aria-label="Inchide notificarea"
          >
            x
          </button>
        </article>
      ))}
    </div>,
    document.body
  )
}

export default function NotificationCenter({
  notifications,
  unreadCount,
  loading,
  onRefresh,
  onMarkRead,
  onMarkAllRead,
  onOpenNotification,
}) {
  const [open, setOpen] = useState(false)
  const [filter, setFilter] = useState('all')
  const triggerRef = useRef(null)
  const panelRef = useRef(null)
  const [panelStyle, setPanelStyle] = useState(null)

  const visibleNotifications = useMemo(() => {
    const list = Array.isArray(notifications) ? notifications : []
    return filter === 'unread' ? list.filter((item) => !item.read) : list
  }, [filter, notifications])

  useEffect(() => {
    if (!open) {
      return undefined
    }

    function updatePanelPosition() {
      const trigger = triggerRef.current
      if (!trigger) {
        return
      }

      const rect = trigger.getBoundingClientRect()
      const panelWidth = Math.min(448, window.innerWidth - 32)
      const left = Math.max(16, Math.min(window.innerWidth - panelWidth - 16, rect.right - panelWidth))
      setPanelStyle({
        top: rect.bottom + 12,
        left,
        width: panelWidth,
      })
    }

    function handlePointerDown(event) {
      if (
        triggerRef.current
        && !triggerRef.current.contains(event.target)
        && panelRef.current
        && !panelRef.current.contains(event.target)
      ) {
        setOpen(false)
      }
    }

    function handleEscape(event) {
      if (event.key === 'Escape') {
        setOpen(false)
      }
    }

    updatePanelPosition()
    window.addEventListener('mousedown', handlePointerDown)
    window.addEventListener('keydown', handleEscape)
    window.addEventListener('resize', updatePanelPosition)
    window.addEventListener('scroll', updatePanelPosition, true)
    return () => {
      window.removeEventListener('mousedown', handlePointerDown)
      window.removeEventListener('keydown', handleEscape)
      window.removeEventListener('resize', updatePanelPosition)
      window.removeEventListener('scroll', updatePanelPosition, true)
    }
  }, [open])

  return (
    <div className={`notificationCenter ${open ? 'is-open' : ''}`.trim()}>
      <button
        ref={triggerRef}
        className="notificationBell"
        type="button"
        onClick={() => setOpen((current) => !current)}
        aria-expanded={open}
      >
        <span className="notificationBellIcon" aria-hidden="true"></span>
        <span className="notificationBellLabel">Notificari</span>
        <span className={`notificationBellCount ${unreadCount > 0 ? 'has-unread' : ''}`.trim()}>
          {unreadCount}
        </span>
      </button>

      {open && panelStyle && createPortal(
        <section
          ref={panelRef}
          className="notificationPanel"
          role="dialog"
          aria-modal="false"
          style={panelStyle}
        >
          <div className="notificationPanelHeader">
            <div>
              <div className="notificationPanelTitle">Centrul de notificari</div>
              <div className="notificationPanelSubtitle">Inbox global cu acces rapid la actualizarile relevante.</div>
            </div>

            <div className="notificationPanelActions">
              <button className="btn btnSmall" type="button" onClick={onRefresh} disabled={loading}>
                {loading ? 'Se incarca...' : 'Refresh'}
              </button>
              <button className="btn btnSmall" type="button" onClick={onMarkAllRead} disabled={loading || unreadCount === 0}>
                Marcheaza tot
              </button>
            </div>
          </div>

          <div className="notificationFilters">
            <button
              className={`notificationFilter ${filter === 'all' ? 'active' : ''}`.trim()}
              type="button"
              onClick={() => setFilter('all')}
            >
              Toate
            </button>
            <button
              className={`notificationFilter ${filter === 'unread' ? 'active' : ''}`.trim()}
              type="button"
              onClick={() => setFilter('unread')}
            >
              Necitite
            </button>
          </div>

          <div className="notificationPanelBody">
            {visibleNotifications.length === 0 ? (
              <div className="notificationEmptyState">
                Nu exista notificari pentru filtrul selectat.
              </div>
            ) : (
              visibleNotifications.map((notification) => (
                <article
                  key={notification.id}
                  className={`notificationItem ${notification.read ? 'is-read' : 'is-unread'}`.trim()}
                >
                  <button
                    className="notificationItemMain"
                    type="button"
                    onClick={() => {
                      onOpenNotification(notification)
                      setOpen(false)
                    }}
                  >
                    <span className={`notificationCategory notificationCategory-${notification.category || 'system'}`}>
                      {categoryLabel(notification.category)}
                    </span>
                    <strong>{notification.title || 'Notificare'}</strong>
                    <span className="notificationItemMessage">{notification.message}</span>
                    <span className="notificationItemMeta">
                      {formatDate(notification.created_at)}
                    </span>
                  </button>

                  {!notification.read && (
                    <button
                      className="btn btnSmall"
                      type="button"
                      onClick={() => onMarkRead(notification.id)}
                    >
                      Citit
                    </button>
                  )}
                </article>
              ))
            )}
          </div>
        </section>,
        document.body
      )}
    </div>
  )
}
