import React, { useEffect, useMemo, useState } from 'react'
import { apiDownload, apiGet, apiPatch, apiPost } from '../services/apiService'

const DOCUMENT_TYPE = 'student_certificate'

function statusLabel(status) {
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

function typeLabel(request) {
  return request.type_label || request.type || '-'
}

function formatDate(value) {
  if (!value) return '-'
  try {
    return new Date(value).toLocaleString()
  } catch {
    return value
  }
}

function extractFilename(response) {
  const contentDisposition = response.headers.get('content-disposition') || ''
  const match = contentDisposition.match(/filename="([^"]+)"/i)
  return match?.[1] || 'document.pdf'
}

export default function DocumentsScreen({ accessToken, roles = [] }) {
  const canRequest = roles.includes('student')
  const canReview = roles.includes('secretariat') || roles.includes('sysadmin')
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [banner, setBanner] = useState(null)
  const [requests, setRequests] = useState([])
  const [purpose, setPurpose] = useState('')

  useEffect(() => {
    if (!canRequest && !canReview) {
      return
    }

    ;(async () => {
      setLoading(true)
      try {
        const data = await apiGet('/documents/requests', accessToken)
        setRequests(Array.isArray(data) ? data : [])
      } catch (error) {
        setBanner({ type: 'error', text: String(error?.message || error) })
      } finally {
        setLoading(false)
      }
    })()
  }, [accessToken, canRequest, canReview])

  const pendingRequests = useMemo(
    () => requests.filter((request) => request.status === 'PENDING'),
    [requests]
  )

  const approvedRequests = useMemo(
    () => requests.filter((request) => request.status === 'APPROVED'),
    [requests]
  )

  async function refreshRequests(nextBanner = null) {
    setLoading(true)
    if (nextBanner) {
      setBanner(nextBanner)
    }
    try {
      const data = await apiGet('/documents/requests', accessToken)
      setRequests(Array.isArray(data) ? data : [])
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setLoading(false)
    }
  }

  async function submitRequest() {
    setSaving(true)
    setBanner(null)
    try {
      await apiPost(
        '/documents/requests',
        { type: DOCUMENT_TYPE, purpose: purpose.trim() },
        accessToken
      )
      setPurpose('')
      await refreshRequests({ type: 'ok', text: 'Cererea a fost trimisa catre secretariat si sysadmin.' })
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setSaving(false)
    }
  }

  async function approveRequest(requestId) {
    setSaving(true)
    setBanner(null)
    try {
      await apiPatch(`/documents/requests/${requestId}/approve`, {}, accessToken)
      await refreshRequests({ type: 'ok', text: 'Cererea a fost aprobata.' })
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setSaving(false)
    }
  }

  async function rejectRequest(requestId) {
    const reason = window.prompt('Motivul respingerii:', '')
    if (reason == null) {
      return
    }

    setSaving(true)
    setBanner(null)
    try {
      await apiPatch(`/documents/requests/${requestId}/reject`, { reason: reason.trim() }, accessToken)
      await refreshRequests({ type: 'ok', text: 'Cererea a fost respinsa.' })
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setSaving(false)
    }
  }

  async function downloadDocument(requestId) {
    setSaving(true)
    setBanner(null)
    try {
      const response = await apiDownload(`/documents/requests/${requestId}/download`, accessToken)
      const blob = await response.blob()
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = extractFilename(response)
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setSaving(false)
    }
  }

  const canSubmit = canRequest && purpose.trim().length > 0 && purpose.trim().length <= 20

  if (!canRequest && !canReview) {
    return (
      <section className="contentCard">
        <div className="contentHeader">
          <div>
            <div className="title">Documente</div>
            <div className="subtitle">Modul disponibil doar pentru elevi, secretariat si sysadmin.</div>
          </div>
        </div>
        <div className="banner error">Nu ai acces la aceasta sectiune.</div>
      </section>
    )
  }

  return (
    <section className="contentCard">
      <div className="contentHeader">
        <div>
          <div className="title">Documente</div>
          <div className="subtitle">Cereri, aprobari si descarcare PDF fara stocarea fisierelor in aplicatie.</div>
        </div>
      </div>

      <div className="catalogStats studentStats">
        <div className="statPill">Total cereri: <strong>{requests.length}</strong></div>
        <div className="statPill">In asteptare: <strong>{pendingRequests.length}</strong></div>
        <div className="statPill">Aprobate: <strong>{approvedRequests.length}</strong></div>
      </div>

      {banner && <div className={`banner ${banner.type}`}>{banner.text}</div>}

      {canRequest && (
        <div className="mutedBlock" style={{ marginBottom: 18 }}>
          <div style={{ display: 'grid', gap: 14, gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', alignItems: 'end' }}>
            <div>
              <div style={{ fontWeight: 700, marginBottom: 6 }}>Adeverinta de elev</div>
              <div className="mutedSmall">Disponibil acum. Urmeaza: foaie matricola curenta si adeverinta de transport.</div>
            </div>
            <div className="field">
              <label className="label">Scop (maxim 20 caractere)</label>
              <input
                className="input"
                value={purpose}
                onChange={(event) => setPurpose(event.target.value)}
                maxLength={20}
                placeholder="ex: bursa sociala"
                disabled={saving}
              />
            </div>
            <div>
              <button className="btn primary" onClick={submitRequest} disabled={!canSubmit || saving}>
                {saving ? 'Se trimite...' : 'Solicita document'}
              </button>
            </div>
          </div>
        </div>
      )}

      {canReview && (
        <div className="mutedBlock" style={{ marginBottom: 18 }}>
          <div style={{ fontWeight: 700, marginBottom: 10 }}>Cereri de aprobat</div>
          {pendingRequests.length === 0 ? (
            <div className="mutedSmall">Nu exista cereri in asteptare.</div>
          ) : (
            <div className="tableWrap">
              <table className="tbl">
                <thead>
                  <tr>
                    <th>Elev</th>
                    <th>Document</th>
                    <th>Scop</th>
                    <th>Solicitata la</th>
                    <th>Actiuni</th>
                  </tr>
                </thead>
                <tbody>
                  {pendingRequests.map((request) => (
                    <tr key={request.id}>
                      <td>{request.student_username}</td>
                      <td>{typeLabel(request)}</td>
                      <td>{request.purpose}</td>
                      <td>{formatDate(request.created_at)}</td>
                      <td>
                        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                          <button className="btn primary" onClick={() => approveRequest(request.id)} disabled={saving}>
                            Aproba
                          </button>
                          <button className="btn danger" onClick={() => rejectRequest(request.id)} disabled={saving}>
                            Respinge
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {loading ? (
        <div className="mutedBlock">Se incarca cererile...</div>
      ) : requests.length === 0 ? (
        <div className="mutedBlock">Nu exista cereri de documente.</div>
      ) : (
        <div className="tableWrap">
          <table className="tbl">
            <thead>
              <tr>
                <th>Document</th>
                <th>Status</th>
                <th>Elev</th>
                <th>Scop</th>
                <th>Serie/Nr.</th>
                <th>Solicitata la</th>
                <th>Procesata de</th>
                <th>Actiuni</th>
              </tr>
            </thead>
            <tbody>
              {requests.map((request) => (
                <tr key={request.id}>
                  <td>{typeLabel(request)}</td>
                  <td><span className="studentClassBadge">{statusLabel(request.status)}</span></td>
                  <td>{request.student_username}</td>
                  <td>{request.purpose}</td>
                  <td>{request.series && request.document_number ? `${request.series} ${String(request.document_number).padStart(5, '0')}` : '-'}</td>
                  <td>{formatDate(request.created_at)}</td>
                  <td>{request.reviewed_by_username || request.resolution_note || '-'}</td>
                  <td>
                    {request.can_download ? (
                      <button className="btn" onClick={() => downloadDocument(request.id)} disabled={saving}>
                        Descarca PDF
                      </button>
                    ) : (
                      <span className="mutedSmall">-</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
