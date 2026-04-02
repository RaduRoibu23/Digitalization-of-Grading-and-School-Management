import React, { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { apiGet, apiPatch, apiPost } from '../services/apiService'

const CATEGORY_OPTIONS = [
  { value: 'general', label: 'General' },
  { value: 'orar', label: 'Orar' },
  { value: 'catalog', label: 'Catalog' },
  { value: 'documente', label: 'Documente' },
  { value: 'cont', label: 'Cont' },
]

const SATISFACTION_OPTIONS = [
  { value: 'pozitiva', label: 'Pozitiva' },
  { value: 'neutra', label: 'Neutra' },
  { value: 'negativa', label: 'Negativa' },
]

const STATUS_OPTIONS = [
  { value: 'UNOPENED', label: 'Nedeschis' },
  { value: 'IN_PROGRESS', label: 'In curs de rezolvare' },
  { value: 'RESOLVED', label: 'Rezolvat' },
]

const STATUS_FILTER_OPTIONS = [
  { value: 'ALL', label: 'Toate mesajele' },
  { value: 'UNOPENED', label: 'Active' },
  { value: 'IN_PROGRESS', label: 'In curs de rezolvare' },
  { value: 'RESOLVED', label: 'Rezolvate' },
]

function formatDate(value) {
  if (!value) return '-'
  try {
    return new Date(value).toLocaleString()
  } catch {
    return value
  }
}

function previewText(value, limit = 180) {
  const normalized = String(value || '').replace(/\s+/g, ' ').trim()
  if (normalized.length <= limit) {
    return normalized || '-'
  }
  return `${normalized.slice(0, limit - 3)}...`
}

function statusTone(status) {
  switch (status) {
    case 'RESOLVED':
      return 'is-resolved'
    case 'IN_PROGRESS':
      return 'is-progress'
    default:
      return 'is-unopened'
  }
}

function statusLabel(entryOrStatus) {
  if (typeof entryOrStatus === 'string') {
    return STATUS_OPTIONS.find((option) => option.value === entryOrStatus)?.label || entryOrStatus
  }
  return entryOrStatus?.status_label || statusLabel(entryOrStatus?.status)
}

function matchesStatusFilter(entry, filterValue) {
  if (!entry) {
    return false
  }
  return filterValue === 'ALL' || entry.status === filterValue
}

function ticketCode(id) {
  return `FBK-${String(id || 0).padStart(5, '0')}`
}

function threadTitle(entry) {
  if (!entry) return '-'
  return ticketCode(entry.id)
}

function threadMeta(entry) {
  if (!entry) return '-'
  const parts = []
  parts.push(entry.category_label || entry.category || '-')
  parts.push(entry.satisfaction_label || entry.satisfaction || '-')
  if (entry.wants_contact) {
    parts.push('Solicita contact')
  }
  return parts.join(' / ')
}

function detailTitle(entry) {
  if (!entry) return '-'
  return ticketCode(entry.id)
}

function updateEntries(currentEntries, updatedEntry) {
  let found = false
  const nextEntries = currentEntries.map((entry) => {
    if (entry.id !== updatedEntry.id) {
      return entry
    }
    found = true
    return updatedEntry
  })

  if (!found) {
    return [updatedEntry, ...nextEntries]
  }

  return nextEntries
}

function FeedbackStatusPill({ status, label }) {
  return (
    <span className={`feedbackStatusPill ${statusTone(status)}`.trim()}>
      {label || statusLabel(status)}
    </span>
  )
}

export default function FeedbackScreen({ accessToken, roles = [] }) {
  const navigate = useNavigate()
  const { feedbackId } = useParams()
  const canReview = roles.some((role) => ['secretariat', 'admin', 'sysadmin'].includes(role))

  const [listLoading, setListLoading] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [banner, setBanner] = useState(null)
  const [detailError, setDetailError] = useState(null)
  const [entries, setEntries] = useState([])
  const [activeEntry, setActiveEntry] = useState(null)
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [statusDraft, setStatusDraft] = useState('UNOPENED')
  const [replyDraft, setReplyDraft] = useState('')
  const [form, setForm] = useState({
    category: 'general',
    satisfaction: 'pozitiva',
    wants_contact: false,
    message: '',
  })

  async function loadEntries(nextBanner = null) {
    setListLoading(true)
    if (nextBanner) {
      setBanner(nextBanner)
    }
    try {
      const data = await apiGet('/feedback', accessToken)
      setEntries(Array.isArray(data) ? data : [])
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setListLoading(false)
    }
  }

  useEffect(() => {
    if (!accessToken) {
      return
    }
    loadEntries()
  }, [accessToken])

  useEffect(() => {
    if (!feedbackId || !accessToken) {
      setActiveEntry(null)
      setDetailError(null)
      setStatusDraft('UNOPENED')
      setReplyDraft('')
      return
    }

    let ignore = false

    ;(async () => {
      setDetailLoading(true)
      setDetailError(null)
      try {
        const data = await apiGet(`/feedback/${feedbackId}`, accessToken)
        if (ignore) {
          return
        }
        setActiveEntry(data)
        setStatusDraft(data?.status || 'UNOPENED')
        setReplyDraft(data?.reply_message || '')
      } catch (error) {
        if (ignore) {
          return
        }
        setActiveEntry(null)
        setDetailError(String(error?.message || error))
      } finally {
        if (!ignore) {
          setDetailLoading(false)
        }
      }
    })()

    return () => {
      ignore = true
    }
  }, [accessToken, feedbackId])

  const selectedEntryId = feedbackId ? Number(feedbackId) : null

  useEffect(() => {
    if (!selectedEntryId || listLoading || entries.length === 0) {
      return
    }

    const entryExistsInInbox = entries.some((entry) => entry.id === selectedEntryId)
    if (!entryExistsInInbox) {
      return
    }

    const entryVisibleInFilter = entries.some(
      (entry) => entry.id === selectedEntryId && matchesStatusFilter(entry, statusFilter)
    )
    if (!entryVisibleInFilter) {
      navigate('/app/feedback', { replace: true })
    }
  }, [entries, listLoading, navigate, selectedEntryId, statusFilter])

  function updateField(field, value) {
    setForm((current) => ({
      ...current,
      [field]: value,
    }))
  }

  async function submitFeedback() {
    setSaving(true)
    setBanner(null)
    try {
      const created = await apiPost(
        '/feedback',
        {
          category: form.category,
          satisfaction: form.satisfaction,
          wants_contact: form.wants_contact,
          message: form.message.trim(),
        },
        accessToken
      )

      setForm({
        category: 'general',
        satisfaction: 'pozitiva',
        wants_contact: false,
        message: '',
      })
      await loadEntries({ type: 'ok', text: 'Feedback-ul a fost trimis.' })
      navigate(`/app/feedback/${created.id}`)
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setSaving(false)
    }
  }

  async function saveStatus() {
    if (!feedbackId) {
      return
    }

    setSaving(true)
    setBanner(null)
    try {
      const updated = await apiPatch(`/feedback/${feedbackId}/status`, { status: statusDraft }, accessToken)
      setActiveEntry(updated)
      setStatusDraft(updated.status || 'UNOPENED')
      setEntries((current) => updateEntries(current, updated))
      setBanner({ type: 'ok', text: 'Statusul feedback-ului a fost actualizat.' })
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setSaving(false)
    }
  }

  async function sendReply() {
    if (!feedbackId) {
      return
    }

    setSaving(true)
    setBanner(null)
    try {
      const updated = await apiPatch(
        `/feedback/${feedbackId}/reply`,
        { message: replyDraft.trim() },
        accessToken
      )
      setActiveEntry(updated)
      setReplyDraft(updated.reply_message || '')
      setStatusDraft(updated.status || 'UNOPENED')
      setEntries((current) => updateEntries(current, updated))
      setBanner({ type: 'ok', text: 'Reply-ul a fost trimis catre elev.' })
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setSaving(false)
    }
  }

  const filteredEntries = entries.filter((entry) => matchesStatusFilter(entry, statusFilter))
  const visibleEntries = filteredEntries.length
  const contactRequests = entries.filter((entry) => entry.wants_contact).length
  const openEntries = entries.filter((entry) => entry.status !== 'RESOLVED').length
  const resolvedEntries = entries.filter((entry) => entry.status === 'RESOLVED').length
  const canSubmit = form.message.trim().length > 0 && form.message.trim().length <= 2000
  const canSaveStatus = Boolean(
    activeEntry?.can_update_status && statusDraft && statusDraft !== activeEntry?.status && !saving
  )
  const canSendReply = Boolean(
    activeEntry?.can_reply && replyDraft.trim().length > 0 && replyDraft.trim().length <= 2000 && !saving
  )
  const scopeLabel = canReview ? 'Toate mesajele din platforma' : 'Mesajele mele'
  const emptyListMessage =
    statusFilter === 'ALL'
      ? 'Nu exista feedback trimis inca.'
      : 'Nu exista mesaje pentru filtrul selectat.'

  return (
    <section className="contentCard">
      <div className="contentHeader">
        <div>
          <div className="title">Feedback</div>
          <div className="subtitle">
            Inbox de lucru pentru feedback, statusuri si reply catre elevi.
          </div>
        </div>
      </div>

      {banner && <div className={`banner ${banner.type}`}>{banner.text}</div>}

      <div className="feedbackWorkspaceShell">
        <div className="feedbackIntroCard">
          <div className="feedbackKicker">Flux feedback</div>
          <div className="feedbackHeroTitle">Trimite un mesaj nou si urmareste raspunsul in acelasi loc.</div>
          <div className="feedbackHeroText">
            {canReview
              ? 'Secretariatul si administratorii pot parcurge mesajele ca intr-un inbox, pot schimba statusul si pot raspunde direct elevului.'
              : 'Mesajele tale apar in inbox cu ruta proprie, iar daca ai cerut contact, reply-ul primit apare direct in detaliul mesajului si in notificari.'}
          </div>

          <div className="feedbackHeroStats">
            <div className="feedbackHeroStat">
              <div className="feedbackHeroStatValue">{visibleEntries}</div>
              <div className="feedbackHeroStatLabel">Mesaje vizibile</div>
            </div>
            <div className="feedbackHeroStat">
              <div className="feedbackHeroStatValue">{contactRequests}</div>
              <div className="feedbackHeroStatLabel">Cereri de contact</div>
            </div>
            <div className="feedbackHeroStat">
              <div className="feedbackHeroStatValue">{openEntries}</div>
              <div className="feedbackHeroStatLabel">Active</div>
            </div>
            <div className="feedbackHeroStat">
              <div className="feedbackHeroStatValue">{resolvedEntries}</div>
              <div className="feedbackHeroStatLabel">Rezolvate</div>
            </div>
          </div>
        </div>

        <div className="feedbackComposerCard">
          <div className="feedbackComposerHeader">
            <div>
              <div className="feedbackPanelTitle">Mesaj nou</div>
              <div className="mutedSmall">Completeaza feedback-ul si intra direct in conversatia lui.</div>
            </div>
          </div>

          <div className="feedbackFormGrid">
            <div className="feedbackFieldGrid">
              <div className="field">
                <label className="label">Categorie</label>
                <select
                  className="select"
                  value={form.category}
                  onChange={(event) => updateField('category', event.target.value)}
                  disabled={saving}
                >
                  {CATEGORY_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </div>

              <div className="field">
                <label className="label">Tip feedback</label>
                <div className="feedbackRadioWrap">
                  {SATISFACTION_OPTIONS.map((option) => (
                    <label key={option.value} className="feedbackInlineChoice">
                      <input
                        type="radio"
                        name="feedback-satisfaction"
                        value={option.value}
                        checked={form.satisfaction === option.value}
                        onChange={(event) => updateField('satisfaction', event.target.value)}
                        disabled={saving}
                      />
                      <span>{option.label}</span>
                    </label>
                  ))}
                </div>
              </div>
            </div>

            <label className="feedbackInlineChoice feedbackContactChoice">
              <input
                type="checkbox"
                checked={form.wants_contact}
                onChange={(event) => updateField('wants_contact', event.target.checked)}
                disabled={saving}
              />
              <span>Doresc sa fiu contactat pentru acest feedback</span>
            </label>

            <div className="field">
              <label className="label">Mesaj</label>
              <textarea
                className="input"
                value={form.message}
                onChange={(event) => updateField('message', event.target.value)}
                maxLength={2000}
                rows={6}
                disabled={saving}
                placeholder="Descrie clar ce functioneaza bine sau ce ar trebui imbunatatit."
                style={{ minHeight: 150, resize: 'vertical' }}
              />
              <div className="feedbackTextareaMeta">
                <span>Mesajele lungi vor fi afisate cu wrapping automat in inbox si in detaliu.</span>
                <strong>{form.message.trim().length}/2000</strong>
              </div>
            </div>

            <div className="feedbackComposerActions">
              <button className="btn primary" onClick={submitFeedback} disabled={!canSubmit || saving}>
                {saving ? 'Se trimite...' : 'Trimite feedback'}
              </button>
            </div>
          </div>
        </div>
      </div>

      <div className="feedbackInboxLayout">
        <aside className="feedbackInboxPanel">
          <div className="feedbackPanelHeader">
            <div>
              <div className="feedbackPanelTitle">Inbox</div>
              <div className="mutedSmall">{scopeLabel}</div>
            </div>
            <div className="feedbackFilterControl">
              <div className="mutedSmall">Filtru status</div>
              <select
                className="select"
                value={statusFilter}
                onChange={(event) => setStatusFilter(event.target.value)}
                disabled={listLoading}
                aria-label="Filtreaza feedback-ul dupa status"
              >
                {STATUS_FILTER_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {listLoading ? (
            <div className="mutedBlock">Se incarca mesajele...</div>
          ) : visibleEntries === 0 ? (
            <div className="mutedBlock">{emptyListMessage}</div>
          ) : (
            <div className="feedbackThreadList">
              {filteredEntries.map((entry) => (
                <button
                  key={entry.id}
                  type="button"
                  className={`feedbackThreadItem ${selectedEntryId === entry.id ? 'active' : ''}`.trim()}
                  onClick={() => navigate(`/app/feedback/${entry.id}`)}
                >
                  <div className="feedbackThreadTop">
                    <div className="feedbackThreadPrimary">
                      <div className="feedbackThreadTitle">{threadTitle(entry)}</div>
                      <div className="feedbackThreadMetaLine">{threadMeta(entry)}</div>
                    </div>
                    <FeedbackStatusPill status={entry.status} label={statusLabel(entry)} />
                  </div>

                  <div className="feedbackThreadBadges">
                    {entry.wants_contact && <span className="pill">Contact</span>}
                    {entry.reply_message && <span className="pill">Reply trimis</span>}
                  </div>

                  <div className="feedbackThreadPreview">{previewText(entry.message)}</div>
                  <div className="feedbackThreadDate">{formatDate(entry.submitted_at)}</div>
                </button>
              ))}
            </div>
          )}
        </aside>

        <section className="feedbackInboxPanel feedbackDetailPanel">
          <div className="feedbackPanelHeader">
            <div>
              <div className="feedbackPanelTitle">Detaliu mesaj</div>
              <div className="mutedSmall">Deschide fiecare mesaj pe ruta lui si lucreaza direct din panoul din dreapta.</div>
            </div>
            <Link className="btn" to="/app/feedback">
              Inbox
            </Link>
          </div>

          {!feedbackId ? (
            <div className="mutedBlock">
              {visibleEntries > 0
                ? 'Selecteaza un mesaj din inbox pentru a vedea continutul complet si eventualul reply.'
                : emptyListMessage}
              {filteredEntries[0] && (
                <div style={{ marginTop: 12 }}>
                  <Link className="btn primary" to={`/app/feedback/${filteredEntries[0].id}`}>
                    Deschide primul mesaj
                  </Link>
                </div>
              )}
            </div>
          ) : detailLoading ? (
            <div className="mutedBlock">Se incarca mesajul selectat...</div>
          ) : detailError ? (
            <div className="mutedBlock">
              <div style={{ fontWeight: 700, marginBottom: 8 }}>Mesaj indisponibil</div>
              <div className="mutedSmall">{detailError}</div>
            </div>
          ) : activeEntry ? (
            <div className="feedbackDetailBody">
              <div className="feedbackDetailHeader">
                <div>
                  <div className="feedbackDetailEyebrow">Ticket</div>
                  <div className="feedbackDetailTitle">{detailTitle(activeEntry)}</div>
                  <div className="mutedSmall">
                    {activeEntry.category_label || activeEntry.category} / {activeEntry.satisfaction_label || activeEntry.satisfaction}
                  </div>
                  <div className="mutedSmall">
                    Trimis la {formatDate(activeEntry.submitted_at)}
                    {activeEntry.submitted_by_username ? ` de ${activeEntry.submitted_by_username}` : ''}
                  </div>
                </div>
                <FeedbackStatusPill status={activeEntry.status} label={statusLabel(activeEntry)} />
              </div>

              <div className="feedbackDetailMetaGrid">
                <div className="feedbackMetaCard">
                  <div className="feedbackMetaLabel">Expeditor</div>
                  <div className="feedbackMetaValue">{activeEntry.submitted_by_username || '-'}</div>
                </div>
                <div className="feedbackMetaCard">
                  <div className="feedbackMetaLabel">Cerere de contact</div>
                  <div className="feedbackMetaValue">{activeEntry.wants_contact ? 'Da' : 'Nu'}</div>
                </div>
                <div className="feedbackMetaCard">
                  <div className="feedbackMetaLabel">Status actualizat de</div>
                  <div className="feedbackMetaValue">
                    {activeEntry.status_updated_by_username
                      ? `${activeEntry.status_updated_by_username} / ${formatDate(activeEntry.status_updated_at)}`
                      : '-'}
                  </div>
                </div>
                <div className="feedbackMetaCard">
                  <div className="feedbackMetaLabel">Ultimul reply</div>
                  <div className="feedbackMetaValue">
                    {activeEntry.reply_message
                      ? `${activeEntry.replied_by_username || '-'} / ${formatDate(activeEntry.replied_at)}`
                      : 'Fara reply'}
                  </div>
                </div>
              </div>

              <div className="feedbackSectionBlock">
                <div className="feedbackSectionTitle">Mesajul trimis</div>
                <div className="feedbackMessageBody">{activeEntry.message}</div>
              </div>

              {activeEntry.reply_message && (
                <div className="feedbackSectionBlock feedbackReplyBlock">
                  <div className="feedbackSectionTitle">Reply trimis elevului</div>
                  <div className="feedbackReplyMeta">
                    {activeEntry.replied_by_username || '-'} / {formatDate(activeEntry.replied_at)}
                  </div>
                  <div className="feedbackMessageBody">{activeEntry.reply_message}</div>
                </div>
              )}

              {activeEntry.can_update_status && (
                <div className="mutedBlock feedbackActionBlock">
                  <div className="feedbackSectionTitle">Status mesaj</div>
                  <div className="feedbackActionRow">
                    <select
                      className="select"
                      value={statusDraft}
                      onChange={(event) => setStatusDraft(event.target.value)}
                      disabled={saving}
                    >
                      {STATUS_OPTIONS.map((option) => (
                        <option key={option.value} value={option.value}>
                          {option.label}
                        </option>
                      ))}
                    </select>
                    <button className="btn primary" onClick={saveStatus} disabled={!canSaveStatus}>
                      {saving ? 'Se salveaza...' : 'Actualizeaza status'}
                    </button>
                  </div>
                </div>
              )}

              <div className="mutedBlock feedbackActionBlock">
                <div className="feedbackSectionTitle">Optiuni de contact</div>
                <div className="mutedSmall" style={{ marginBottom: activeEntry.can_reply ? 12 : 0 }}>
                  {activeEntry.wants_contact
                    ? 'Elevul a cerut sa fie contactat. Reply-ul trimis aici ajunge si in notificari.'
                    : 'Elevul nu a cerut contact pentru acest mesaj.'}
                </div>

                {activeEntry.can_reply && (
                  <>
                    <textarea
                      className="input"
                      value={replyDraft}
                      onChange={(event) => setReplyDraft(event.target.value)}
                      maxLength={2000}
                      rows={5}
                      disabled={saving}
                      placeholder="Scrie reply-ul care va fi trimis elevului."
                      style={{ minHeight: 130, resize: 'vertical' }}
                    />
                    <div className="feedbackTextareaMeta">
                      <span>Reply-ul ramane asociat mesajului si este trimis si ca notificare.</span>
                      <strong>{replyDraft.trim().length}/2000</strong>
                    </div>
                    <div className="feedbackActionRow">
                      <button className="btn primary" onClick={sendReply} disabled={!canSendReply}>
                        {saving ? 'Se trimite...' : (activeEntry.reply_message ? 'Actualizeaza reply' : 'Trimite reply')}
                      </button>
                    </div>
                  </>
                )}
              </div>
            </div>
          ) : (
            <div className="mutedBlock">Selecteaza un mesaj din inbox.</div>
          )}
        </section>
      </div>
    </section>
  )
}
