import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiDelete, apiGet, apiPost } from '../services/apiService'

const SLOT_TIME_BOUNDS = {
  1: { startMinutes: 8 * 60, endMinutes: 8 * 60 + 50 },
  2: { startMinutes: 9 * 60, endMinutes: 9 * 60 + 50 },
  3: { startMinutes: 10 * 60, endMinutes: 10 * 60 + 50 },
  4: { startMinutes: 11 * 60, endMinutes: 11 * 60 + 50 },
  5: { startMinutes: 12 * 60, endMinutes: 12 * 60 + 50 },
  6: { startMinutes: 13 * 60, endMinutes: 13 * 60 + 50 },
  7: { startMinutes: 14 * 60, endMinutes: 14 * 60 + 50 },
}

async function fetchDashboardSummary(accessToken) {
  return apiGet('/dashboard/summary', accessToken)
}

function formatDate(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

function notificationCategoryLabel(category) {
  switch (category) {
    case 'catalog':
      return 'Catalog'
    case 'documents':
      return 'Documente'
    case 'feedback':
      return 'Asistenta'
    case 'timetable':
      return 'Orar'
    default:
      return 'Sistem'
  }
}

function feedbackStatusLabel(entry) {
  return entry?.status_label || entry?.status || '-'
}

function documentStatusLabel(status) {
  switch (status) {
    case 'PENDING':
      return 'In asteptare'
    case 'APPROVED':
      return 'Aprobata'
    case 'REJECTED':
      return 'Respinsa'
    default:
      return status || '-'
  }
}

function authorLabel(username) {
  return username || 'sistem'
}

function resolveLiveScheduleState(entries, now) {
  if (!Array.isArray(entries) || entries.length === 0) {
    return { kind: 'done', entry: null }
  }

  const nowMinutes = now.getHours() * 60 + now.getMinutes()
  const orderedEntries = [...entries].sort((left, right) => (left.index_in_day || 0) - (right.index_in_day || 0))

  for (const entry of orderedEntries) {
    const slot = SLOT_TIME_BOUNDS[entry.index_in_day]
    if (!slot) continue
    if (nowMinutes >= slot.startMinutes && nowMinutes <= slot.endMinutes) {
      return { kind: 'current', entry }
    }
    if (nowMinutes < slot.startMinutes) {
      return { kind: 'next', entry }
    }
  }

  return { kind: 'done', entry: null }
}

function MetricCard({ metric, index }) {
  return (
    <article className={`dashboardMetricCard tone-${metric.tone || 'neutral'}`} style={{ '--card-index': index }}>
      <div className="dashboardMetricLabel">{metric.label}</div>
      <div className="dashboardMetricValue">{metric.value}</div>
      <div className="dashboardMetricDetail">{metric.detail}</div>
    </article>
  )
}

function PanelEmpty({ title, text }) {
  return (
    <div className="dashboardPanelEmpty">
      <div className="dashboardPanelEmptyTitle">{title}</div>
      <div className="dashboardPanelEmptyText">{text}</div>
    </div>
  )
}

export default function AppDashboardPage({ accessToken, roles = [] }) {
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [summary, setSummary] = useState(null)
  const [clockNow, setClockNow] = useState(() => new Date())
  const [announcementDraft, setAnnouncementDraft] = useState({ title: '', message: '' })
  const [announcementSaving, setAnnouncementSaving] = useState(false)
  const [deletingAnnouncementId, setDeletingAnnouncementId] = useState(null)
  const [announcementBanner, setAnnouncementBanner] = useState(null)

  const isAcademicContext = summary?.role_context === 'academic'
  const todayEntries = Array.isArray(summary?.today_timetable) ? summary.today_timetable : []
  const announcements = Array.isArray(summary?.announcements) ? summary.announcements : []
  const notifications = Array.isArray(summary?.recent_notifications) ? summary.recent_notifications : []
  const pendingDocuments = Array.isArray(summary?.pending_documents) ? summary.pending_documents : []
  const recentFeedback = Array.isArray(summary?.recent_feedback) ? summary.recent_feedback : []
  const canPublishAnnouncements = Boolean(summary?.can_publish_announcements)
  const canDeleteAnnouncements = roles.includes('sysadmin')
  const liveScheduleState = useMemo(() => resolveLiveScheduleState(todayEntries, clockNow), [todayEntries, clockNow])
  const liveScheduleEntry = liveScheduleState.entry
  const liveScheduleLabel = liveScheduleState.kind === 'current' ? 'Intervalul curent' : 'Urmatorul interval'
  const liveScheduleValue = liveScheduleEntry ? liveScheduleEntry.time_label : 'Zi finalizata'
  const liveScheduleText = liveScheduleEntry ? liveScheduleEntry.subject_name : 'Nu mai exista alte ore programate astazi.'
  const metrics = useMemo(() => {
    const currentMetrics = Array.isArray(summary?.metrics) ? summary.metrics : []
    return currentMetrics.map((metric) => {
      if (metric.id !== 'next-slot') {
        return metric
      }
      if (!liveScheduleEntry) {
        return {
          ...metric,
          label: 'Programul zilei',
          value: 'Zi finalizata',
          detail: 'Nu mai exista alte ore programate astazi.',
          tone: 'neutral',
        }
      }
      return {
        ...metric,
        label: liveScheduleLabel,
        value: liveScheduleEntry.time_label,
        detail: liveScheduleEntry.subject_name,
        tone: 'accent',
      }
    })
  }, [summary, liveScheduleEntry, liveScheduleLabel])

  const roleLabel = useMemo(() => {
    if (roles.includes('student')) return 'Elev'
    if (roles.includes('parent')) return 'Parinte'
    if (roles.includes('professor')) return 'Profesor'
    if (roles.includes('sysadmin')) return 'Sysadmin'
    if (roles.includes('director')) return 'Director'
    if (roles.includes('secretariat')) return 'Secretariat'
    if (roles.includes('scheduler')) return 'Scheduler'
    return 'Utilizator autentificat'
  }, [roles])

  async function loadSummary() {
    if (!accessToken) {
      setSummary(null)
      setError('')
      setLoading(false)
      return
    }

    setLoading(true)
    try {
      const data = await fetchDashboardSummary(accessToken)
      setSummary(data && typeof data === 'object' ? data : null)
      setError('')
    } catch (nextError) {
      setError(String(nextError?.message || nextError))
    } finally {
      setLoading(false)
    }
  }

  async function submitAnnouncement() {
    if (!announcementDraft.message.trim()) {
      return
    }

    setAnnouncementSaving(true)
    setAnnouncementBanner(null)
    try {
      const created = await apiPost(
        '/announcements',
        {
          title: announcementDraft.title.trim(),
          message: announcementDraft.message.trim(),
        },
        accessToken
      )
      setSummary((current) => {
        if (!current) {
          return current
        }
        const currentAnnouncements = Array.isArray(current.announcements) ? current.announcements : []
        return {
          ...current,
          announcements: [created, ...currentAnnouncements].slice(0, 6),
        }
      })
      setAnnouncementDraft({ title: '', message: '' })
      setAnnouncementBanner({ type: 'ok', text: 'Anuntul a fost publicat.' })
    } catch (nextError) {
      setAnnouncementBanner({ type: 'error', text: String(nextError?.message || nextError) })
    } finally {
      setAnnouncementSaving(false)
    }
  }

  async function deleteAnnouncement(announcementId) {
    setDeletingAnnouncementId(announcementId)
    setAnnouncementBanner(null)
    try {
      await apiDelete(`/announcements/${announcementId}`, accessToken)
      setSummary((current) => {
        if (!current) {
          return current
        }
        const currentAnnouncements = Array.isArray(current.announcements) ? current.announcements : []
        return {
          ...current,
          announcements: currentAnnouncements.filter((announcement) => announcement.id !== announcementId),
        }
      })
      setAnnouncementBanner({ type: 'ok', text: 'Anuntul a fost sters.' })
    } catch (nextError) {
      setAnnouncementBanner({ type: 'error', text: String(nextError?.message || nextError) })
    } finally {
      setDeletingAnnouncementId(null)
    }
  }

  useEffect(() => {
    let cancelled = false

    async function run() {
      if (!accessToken) {
        setSummary(null)
        setError('')
        setLoading(false)
        return
      }

      setLoading(true)
      try {
        const data = await fetchDashboardSummary(accessToken)
        if (cancelled) return
        setSummary(data && typeof data === 'object' ? data : null)
        setError('')
      } catch (nextError) {
        if (cancelled) return
        setError(String(nextError?.message || nextError))
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    run()
    return () => {
      cancelled = true
    }
  }, [accessToken])

  useEffect(() => {
    const intervalId = window.setInterval(() => {
      setClockNow(new Date())
    }, 30000)

    return () => {
      window.clearInterval(intervalId)
    }
  }, [])

  return (
    <section className="contentCard dashboardHomePage">
      <div className="dashboardHero">
        <div>
          <div className="dashboardHeroKicker">{roleLabel}</div>
          <div className="title">{summary?.title || 'Panou principal'}</div>
          <div className="subtitle">
            {summary?.subtitle || 'Acceseaza rapid ce conteaza acum: program, notificari, cereri si actiuni utile pentru rolul tau.'}
          </div>
        </div>

        <div className="dashboardHeroActions">
          <button className="btn" type="button" onClick={loadSummary} disabled={loading}>
            {loading ? 'Se actualizeaza...' : 'Actualizeaza panoul'}
          </button>
          <Link className="btn primary" to={isAcademicContext ? '/app/orarul-meu' : '/app/genereaza-orar'}>
            {isAcademicContext ? 'Deschide orarul' : 'Deschide consola de orar'}
          </Link>
        </div>
      </div>

      {error && <div className="banner error">{error}</div>}

      {loading ? (
        <div className="dashboardPageState">
          <div className="dashboardStateTitle">Se pregateste panoul principal</div>
          <div className="dashboardStateText">Agregam datele relevante pentru rolul curent si refacem sumarul vizual.</div>
        </div>
      ) : !summary ? (
        <div className="dashboardPageState">
          <div className="dashboardStateTitle">Panoul nu a putut fi incarcat</div>
          <div className="dashboardStateText">Reincearca actualizarea sau continua din modulele din bara laterala.</div>
        </div>
      ) : (
        <div className="dashboardHomeGrid">
          <div className="dashboardHomeMain">
            <div className="dashboardMetricGrid">
              {metrics.map((metric, index) => (
                <MetricCard key={metric.id || metric.label} metric={metric} index={index} />
              ))}
            </div>

            {isAcademicContext ? (
              <article className="dashboardPanel dashboardSchedulePanel">
                <div className="dashboardPanelHeader">
                  <div>
                    <div className="dashboardPanelEyebrow">Programul zilei</div>
                    <div className="dashboardPanelTitle">Orele de astazi</div>
                    <div className="dashboardPanelText">
                      Program compact cu accent pe urmatorul interval si pe continuitatea zilei.
                    </div>
                  </div>

                  <div className="dashboardHighlightCard">
                    <div className="dashboardHighlightLabel">{liveScheduleLabel}</div>
                    <div className="dashboardHighlightValue">{liveScheduleValue}</div>
                    <div className="dashboardHighlightText">{liveScheduleText}</div>
                  </div>
                </div>

                {todayEntries.length === 0 ? (
                  <PanelEmpty
                    title="Zi fara ore programate"
                    text="Astazi nu exista intervale active in orar sau ziua de curs este deja incheiata."
                  />
                ) : (
                  <div className="dashboardScheduleList">
                    {todayEntries.map((entry) => (
                      <article key={entry.id} className="dashboardScheduleCard">
                        <div className="dashboardScheduleTime">{entry.time_label}</div>
                        <div className="dashboardScheduleBody">
                          <div className="dashboardScheduleSubject">{entry.subject_name}</div>
                          <div className="dashboardScheduleMeta">
                            {entry.class_name && <span>{entry.class_name}</span>}
                            {entry.teacher_name && <span>{entry.teacher_name}</span>}
                            {entry.room_name && <span>{entry.room_name}</span>}
                          </div>
                        </div>
                      </article>
                    ))}
                  </div>
                )}
              </article>
            ) : (
              <div className="dashboardOperationsGrid">
                <article className="dashboardPanel">
                  <div className="dashboardPanelHeader">
                    <div>
                      <div className="dashboardPanelEyebrow">Documente</div>
                      <div className="dashboardPanelTitle">Cereri in asteptare</div>
                      <div className="dashboardPanelText">Solicitarile cele mai recente care cer procesare manuala.</div>
                    </div>
                    <Link className="btn" to="/app/documente">Modul documente</Link>
                  </div>

                  {pendingDocuments.length === 0 ? (
                    <PanelEmpty
                      title="Nu exista cereri restante"
                      text="Fluxul de documente este la zi pentru rolul curent."
                    />
                  ) : (
                    <div className="dashboardList">
                      {pendingDocuments.map((request) => (
                        <Link key={request.id} className="dashboardListItem" to="/app/documente">
                          <div className="dashboardListPrimary">
                            <strong>{request.type_label || request.type}</strong>
                            <span>{request.student_username}</span>
                          </div>
                          <div className="dashboardListMeta">
                            <span className="dashboardChip warning">{documentStatusLabel(request.status)}</span>
                            <span>{formatDate(request.created_at)}</span>
                          </div>
                        </Link>
                      ))}
                    </div>
                  )}
                </article>

                <article className="dashboardPanel">
                  <div className="dashboardPanelHeader">
                    <div>
                      <div className="dashboardPanelEyebrow">Asistenta</div>
                      <div className="dashboardPanelTitle">Cereri active</div>
                      <div className="dashboardPanelText">Mesajele recente care nu sunt inca marcate ca rezolvate.</div>
                    </div>
                    <Link className="btn" to="/app/feedback">Modul asistenta</Link>
                  </div>

                  {recentFeedback.length === 0 ? (
                    <PanelEmpty
                      title="Flux curat"
                      text="Nu exista cereri de asistenta active pentru rolul curent."
                    />
                  ) : (
                    <div className="dashboardList">
                      {recentFeedback.map((entry) => (
                        <Link key={entry.id} className="dashboardListItem" to={`/app/feedback/${entry.id}`}>
                          <div className="dashboardListPrimary">
                            <strong>{entry.category_label || entry.category}</strong>
                            <span>{entry.submitted_by_username}</span>
                          </div>
                          <div className="dashboardListMeta">
                            <span className={`dashboardChip ${entry.status === 'UNOPENED' ? 'warning' : 'accent'}`.trim()}>
                              {feedbackStatusLabel(entry)}
                            </span>
                            <span>{formatDate(entry.submitted_at)}</span>
                          </div>
                        </Link>
                      ))}
                    </div>
                  )}
                </article>
              </div>
            )}
          </div>

          <aside className="dashboardHomeAside">
            <article className="dashboardPanel">
              <div className="dashboardPanelHeader">
                <div>
                  <div className="dashboardPanelEyebrow">Anunturi</div>
                  <div className="dashboardPanelTitle">Panoul de informare</div>
                  <div className="dashboardPanelText">Mesajele importante ale zilei, vizibile pentru toata platforma.</div>
                </div>
              </div>

              {canPublishAnnouncements && (
                <div className="dashboardAnnouncementComposer">
                  {announcementBanner && <div className={`banner ${announcementBanner.type}`}>{announcementBanner.text}</div>}
                  <div className="field">
                    <label className="label">Titlu scurt</label>
                    <input
                      className="input"
                      value={announcementDraft.title}
                      maxLength={160}
                      placeholder="ex: Sala 12 indisponibila"
                      onChange={(event) => setAnnouncementDraft((current) => ({ ...current, title: event.target.value }))}
                      disabled={announcementSaving}
                    />
                  </div>
                  <div className="field" style={{ marginBottom: 0 }}>
                    <label className="label">Mesaj anunt</label>
                    <textarea
                      className="input"
                      value={announcementDraft.message}
                      maxLength={1200}
                      rows={4}
                      placeholder="ex: Astazi, din cauza unei defectiuni, nu se vor efectua ore in sala 12."
                      onChange={(event) => setAnnouncementDraft((current) => ({ ...current, message: event.target.value }))}
                      disabled={announcementSaving}
                      style={{ resize: 'vertical', minHeight: 112 }}
                    />
                  </div>
                  <div className="dashboardAnnouncementActions">
                    <span className="mutedSmall">{announcementDraft.message.trim().length}/1200</span>
                    <button className="btn primary" type="button" onClick={submitAnnouncement} disabled={announcementSaving || !announcementDraft.message.trim()}>
                      {announcementSaving ? 'Se publica...' : 'Publica anuntul'}
                    </button>
                  </div>
                </div>
              )}

              {announcements.length === 0 ? (
                <PanelEmpty
                  title="Nu exista anunturi active"
                  text="Panoul este gol in acest moment. Urmatorul anunt publicat va aparea aici."
                />
              ) : (
                <div className="dashboardAnnouncementList">
                  {announcements.map((announcement) => (
                    <article key={announcement.id} className="dashboardAnnouncementItem">
                      <div className="dashboardAnnouncementTitle">{announcement.title || 'Anunt intern'}</div>
                      <div className="dashboardAnnouncementMessage">{announcement.message}</div>
                      <div className="dashboardAnnouncementMeta">
                        <div className="dashboardAnnouncementMetaDetails">
                          <span>{authorLabel(announcement.created_by_username)}</span>
                          <span>{formatDate(announcement.created_at)}</span>
                        </div>
                        {canDeleteAnnouncements && (
                          <div className="dashboardAnnouncementMetaActions">
                            <button
                              className="btn danger dashboardAnnouncementDelete"
                              type="button"
                              onClick={() => deleteAnnouncement(announcement.id)}
                              disabled={deletingAnnouncementId === announcement.id}
                            >
                              {deletingAnnouncementId === announcement.id ? 'Se sterge...' : 'Sterge'}
                            </button>
                          </div>
                        )}
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </article>

            <article className="dashboardPanel">
              <div className="dashboardPanelHeader">
                <div>
                  <div className="dashboardPanelEyebrow">Notificari</div>
                  <div className="dashboardPanelTitle">Ultimele actualizari</div>
                  <div className="dashboardPanelText">Acces rapid spre mesajele care pot schimba pasul de lucru de azi.</div>
                </div>
              </div>

              {notifications.length === 0 ? (
                <PanelEmpty
                  title="Notificari la zi"
                  text="Nu exista notificari recente pentru utilizatorul curent."
                />
              ) : (
                <div className="dashboardNotificationList">
                  {notifications.map((notification) => (
                    <Link key={notification.id} className="dashboardNotificationItem" to={notification.action_path || '/app'}>
                      <span className={`dashboardChip ${notification.read ? 'neutral' : 'accent'}`.trim()}>
                        {notificationCategoryLabel(notification.category)}
                      </span>
                      <strong>{notification.title || 'Notificare'}</strong>
                      <span>{notification.message}</span>
                      <small>{formatDate(notification.created_at)}</small>
                    </Link>
                  ))}
                </div>
              )}
            </article>
          </aside>
        </div>
      )}
    </section>
  )
}
