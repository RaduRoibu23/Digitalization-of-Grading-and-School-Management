export const NOTIFICATION_REFRESH_EVENT = 'app:notifications:refresh'

export function requestNotificationRefresh() {
  window.dispatchEvent(new CustomEvent(NOTIFICATION_REFRESH_EVENT))
}
