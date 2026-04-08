import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
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
  const [search, setSearch] = useState('')
  const [actorFilter, setActorFilter] = useState('all')
  const [panelStyle, setPanelStyle] = useState(null)
  const triggerRef = useRef(null)
  const panelRef = useRef(null)

  const actorOptions = Array.from(new Set(entries.map((entry) => entry.actor_username).filter(Boolean)))
  const visibleEntries = entries.filter((entry) => {
    if (actorFilter !== 'all' && entry.actor_username !== actorFilter) {
      return false
    }
    const haystack = `${entry.action || ''} ${entry.actor_username || ''} ${entry.effect || ''}`.toLowerCase()
    return haystack.includes(search.trim().toLowerCase())
  })

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

    function updatePanelPosition() {
      const trigger = triggerRef.current
      if (!trigger) {
        return
      }

      const rect = trigger.getBoundingClientRect()
      const panelWidth = Math.min(832, window.innerWidth - 32)
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
    loadAuditEntries()
    const intervalId = window.setInterval(loadAuditEntries, POLL_MS)
    window.addEventListener('mousedown', handlePointerDown)
    window.addEventListener('keydown', handleEscape)
    window.addEventListener('resize', updatePanelPosition)
    window.addEventListener('scroll', updatePanelPosition, true)
    return () => {
      window.clearInterval(intervalId)
      window.removeEventListener('mousedown', handlePointerDown)
      window.removeEventListener('keydown', handleEscape)
      window.removeEventListener('resize', updatePanelPosition)
      window.removeEventListener('scroll', updatePanelPosition, true)
    }
  }, [open, accessToken])

  return (
    <>
      <button
        ref={triggerRef}
        className="auditConsoleToggle btn primary"
        type="button"
        onClick={() => setOpen((current) => !current)}
      >
        {open ? 'Inchide audit' : 'Audit'}
      </button>

      {open && panelStyle && createPortal(
        <section
          ref={panelRef}
          className="auditConsolePanel contentCard"
          style={panelStyle}
        >
          <div className="auditConsoleHeader contentHeader">
            <div>
              <div className="title">Consola audit</div>
              <div className="subtitle">Istoric pentru actiunile importante din aplicatie.</div>
            </div>

            <div className="auditConsoleActions headerActions">
              <button className="btn" type="button" onClick={loadAuditEntries} disabled={loading}>
                {loading ? 'Se incarca...' : 'Actualizeaza'}
              </button>
              <button className="btn" type="button" onClick={() => setOpen(false)}>
                Inchide
              </button>
            </div>
          </div>

          {error && <div className="banner error">{error}</div>}

          <div className="auditConsoleFilters">
            <input
              className="input"
              type="search"
              placeholder="Cauta dupa actiune, utilizator sau efect"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
            <select
              className="select"
              value={actorFilter}
              onChange={(event) => setActorFilter(event.target.value)}
            >
              <option value="all">Toti utilizatorii</option>
              {actorOptions.map((actor) => (
                <option key={actor} value={actor}>{actor}</option>
              ))}
            </select>
          </div>

          <div className="auditConsoleMeta catalogStats">
            <span className="statPill">Intrari vizibile: <strong>{visibleEntries.length}</strong></span>
            <span className="statPill">Actualizare: <strong>la 20s</strong></span>
          </div>

          {loading && entries.length === 0 ? (
            <div className="mutedBlock">Se incarca istoricul de audit...</div>
          ) : visibleEntries.length === 0 ? (
            <div className="mutedBlock">Nu exista inca actiuni inregistrate.</div>
          ) : (
            <div className="auditConsoleTableWrap tableWrap">
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
                  {visibleEntries.map((entry) => (
                    <tr key={entry.id}>
                      <td className="auditConsoleOrderCell">{entry.id}</td>
                      <td className="auditConsoleActionCell">{entry.action || '-'}</td>
                      <td className="auditConsoleActorCell">{entry.actor_username || '-'}</td>
                      <td className="auditConsoleEffectCell">{entry.effect || '-'}</td>
                      <td className="auditConsoleTimeCell">{formatTimestamp(entry.created_at)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>,
        document.body
      )}
    </>
  )
}
