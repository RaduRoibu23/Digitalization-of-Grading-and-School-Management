import { useEffect, useState } from 'react'
import { apiGet } from '../services/apiService'

const POLL_MS = 20000

function formatTimestamp(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

export default function AuditConsole({ accessToken }) {
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [entries, setEntries] = useState([])

  async function loadAuditEntries() {
    if (!accessToken) return
    setLoading(true)
    try {
      const data = await apiGet('/audit-logs?limit=200', accessToken)
      setEntries(Array.isArray(data) ? data : [])
      setError('')
    } catch (nextError) {
      setError(String(nextError?.message || nextError))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (!open) return undefined

    loadAuditEntries()
    const intervalId = window.setInterval(loadAuditEntries, POLL_MS)
    return () => window.clearInterval(intervalId)
  }, [open, accessToken])

  return (
    <>
      <button className="auditConsoleToggle" type="button" onClick={() => setOpen((current) => !current)}>
        {open ? 'Inchide audit' : 'Audit'}
      </button>

      {open && (
        <section className="auditConsolePanel">
          <div className="auditConsoleHeader">
            <div>
              <div className="auditConsoleTitle">Consola audit</div>
              <div className="auditConsoleSubtitle">Istoric pentru actiunile importante din aplicatie.</div>
            </div>

            <div className="auditConsoleActions">
              <button className="btn" type="button" onClick={loadAuditEntries} disabled={loading}>
                {loading ? 'Se incarca...' : 'Refresh'}
              </button>
              <button className="btn" type="button" onClick={() => setOpen(false)}>
                Inchide
              </button>
            </div>
          </div>

          {error && <div className="banner error">{error}</div>}

          <div className="auditConsoleMeta">
            <span className="statPill">Intrari: <strong>{entries.length}</strong></span>
            <span className="statPill">Actualizare: <strong>la 20s</strong></span>
          </div>

          {loading && entries.length === 0 ? (
            <div className="mutedBlock">Se incarca istoricul de audit...</div>
          ) : entries.length === 0 ? (
            <div className="mutedBlock">Nu exista inca actiuni inregistrate.</div>
          ) : (
            <div className="auditConsoleTableWrap">
              <table className="tbl auditConsoleTable">
                <thead>
                  <tr>
                    <th>Nr de ordine</th>
                    <th>Actiune</th>
                    <th>De catre cine</th>
                    <th>Ce efect</th>
                    <th>Ce ora</th>
                  </tr>
                </thead>
                <tbody>
                  {entries.map((entry) => (
                    <tr key={entry.id}>
                      <td>{entry.id}</td>
                      <td>{entry.action || '-'}</td>
                      <td>{entry.actor_username || '-'}</td>
                      <td>{entry.effect || '-'}</td>
                      <td>{formatTimestamp(entry.created_at)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}
    </>
  )
}
