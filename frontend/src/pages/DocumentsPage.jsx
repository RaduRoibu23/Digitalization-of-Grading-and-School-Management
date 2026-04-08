import React, { useEffect, useMemo, useState } from 'react'
import TextPromptDialog from '../components/ui/TextPromptDialog'
import { apiDownload, apiGet, apiPatch, apiPost } from '../services/apiService'
import { loadViewState, saveViewState } from '../services/viewState'

const DOCUMENT_TYPES = [
  {
    value: 'student_certificate',
    label: 'Adeverinta de elev',
    helper: 'PDF oficial pentru statutul de elev. Descarcarea foloseste numele elevului in fisier.',
  },
  {
    value: 'transcript',
    label: 'Situatie Scolara',
    helper: 'PDF cu materiile si notele elevului existente in momentul aprobarii documentului.',
  },
]
const DOCUMENTS_VIEW_STATE_KEY = 'documents'

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
  const directHeader = response.headers.get('x-download-filename')
  if (directHeader) {
    return directHeader
  }
  const contentDisposition = response.headers.get('content-disposition') || ''
  const quotedMatch = contentDisposition.match(/filename="([^"]+)"/i)
  if (quotedMatch?.[1]) {
    return quotedMatch[1]
  }
  const plainMatch = contentDisposition.match(/filename=([^;]+)/i)
  return plainMatch?.[1]?.trim() || 'document.pdf'
}

export default function DocumentsScreen({ accessToken, roles = [] }) {
  const canRequest = roles.includes('student')
  const canReview = roles.includes('secretariat') || roles.includes('sysadmin')
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [banner, setBanner] = useState(null)
  const [requests, setRequests] = useState([])
  const [documentType, setDocumentType] = useState(() =>
    loadViewState(DOCUMENTS_VIEW_STATE_KEY, { documentType: DOCUMENT_TYPES[0].value }).documentType
  )
  const [purpose, setPurpose] = useState('')
  const [rejectDialog, setRejectDialog] = useState({ open: false, requestId: null, reason: '' })

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
  const selectedDocument = DOCUMENT_TYPES.find((option) => option.value === documentType) || DOCUMENT_TYPES[0]

  useEffect(() => {
    saveViewState(DOCUMENTS_VIEW_STATE_KEY, {
      documentType,
    })
  }, [documentType])

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
        { type: documentType, purpose: purpose.trim() },
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

  async function rejectRequest(requestId, reason) {
    setSaving(true)
    setBanner(null)
    try {
      await apiPatch(`/documents/requests/${requestId}/reject`, { reason: reason.trim() }, accessToken)
      setRejectDialog({ open: false, requestId: null, reason: '' })
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
              <div style={{ fontWeight: 700, marginBottom: 6 }}>Solicita un document</div>
              <div className="mutedSmall">{selectedDocument.helper}</div>
            </div>
            <div className="field">
              <label className="label">Tip document</label>
              <select
                className="select"
                value={documentType}
                onChange={(event) => setDocumentType(event.target.value)}
                disabled={saving}
              >
                {DOCUMENT_TYPES.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
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
                {saving ? 'Se trimite...' : `Solicita ${selectedDocument.label}`}
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
                          <button
                            className="btn danger"
                            onClick={() => setRejectDialog({ open: true, requestId: request.id, reason: '' })}
                            disabled={saving}
                          >
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

      <TextPromptDialog
        open={rejectDialog.open}
        title="Respinge cererea"
        description="Introdu motivul respingerii. Acesta va fi salvat in istoric si afisat pentru cererea procesata."
        label="Motiv"
        placeholder="ex: lipsesc datele necesare pentru procesare"
        confirmLabel="Respinge cererea"
        value={rejectDialog.reason}
        onValueChange={(reason) => setRejectDialog((current) => ({ ...current, reason }))}
        loading={saving}
        onCancel={() => setRejectDialog({ open: false, requestId: null, reason: '' })}
        onConfirm={(reason) => rejectRequest(rejectDialog.requestId, reason)}
      />
    </section>
  )
}
