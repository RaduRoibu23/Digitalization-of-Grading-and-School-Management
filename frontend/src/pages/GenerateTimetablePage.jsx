import { useEffect, useMemo, useState } from 'react'
import { apiDelete, apiGet, apiPost } from '../services/apiService'
import { loadViewState, saveViewState } from '../services/viewState'
import ConfirmDialog from '../components/ui/ConfirmDialog'

const WEEKDAY_LABELS = ['Luni', 'Marti', 'Miercuri', 'Joi', 'Vineri']
const TIME_LABELS = [
  { slot: 1, label: '08:00-08:50' },
  { slot: 2, label: '09:00-09:50' },
  { slot: 3, label: '10:00-10:50' },
  { slot: 4, label: '11:00-11:50' },
  { slot: 5, label: '12:00-12:50' },
  { slot: 6, label: '13:00-13:50' },
  { slot: 7, label: '14:00-14:50' },
]
const GENERATE_TIMETABLE_VIEW_STATE_KEY = 'generate-timetable'

function slotKey(weekday, indexInDay) {
  return `${weekday}-${indexInDay}`
}

function classLabel(schoolClass) {
  const name = schoolClass?.name ?? schoolClass?.class_name ?? `Clasa ${schoolClass?.id}`
  const profile = schoolClass?.profile ?? schoolClass?.class_profile
  return profile ? `${name} - ${profile}` : name
}

function slotLabel(weekday, indexInDay) {
  const dayLabel = WEEKDAY_LABELS[(weekday || 1) - 1] || 'Slot'
  const interval = TIME_LABELS.find((item) => item.slot === indexInDay)?.label || '-'
  return `${dayLabel} / ${interval}`
}

function statusLabel(status) {
  switch (status) {
    case 'valid':
      return 'Mutare valida'
    case 'warning':
      return 'Mutare cu warning'
    case 'blocked':
      return 'Mutare blocata'
    case 'same':
      return 'Slot curent'
    default:
      return 'Slot'
  }
}

function modeLabel(mode, hasTarget) {
  if (mode === 'swap-auto' || hasTarget) {
    return 'Swap automat'
  }
  return 'Mutare simpla'
}

function normalizeEntries(data) {
  const list = Array.isArray(data) ? data : []
  return list
    .filter(Boolean)
    .map((entry) => ({
      ...entry,
      weekday: entry.weekday,
      index_in_day: entry.index_in_day ?? entry.indexInDay,
      subject_name: entry.subject_name ?? entry.subjectName,
      teacher_name: entry.teacher_name ?? entry.teacherName,
      room_name: entry.room_name ?? entry.roomName,
      version: entry.version,
    }))
}

function normalizeMoveOptions(options) {
  const list = Array.isArray(options) ? options : []
  return list
    .filter(Boolean)
    .map((option) => ({
      ...option,
      index_in_day: option.index_in_day ?? option.indexInDay,
      target_entry_id: option.target_entry_id ?? option.targetEntryId,
      target_subject_name: option.target_subject_name ?? option.targetSubjectName,
      target_teacher_name: option.target_teacher_name ?? option.targetTeacherName,
      target_room_name: option.target_room_name ?? option.targetRoomName,
      blocked_reason: option.blocked_reason ?? option.blockedReason,
      warnings: Array.isArray(option.warnings) ? option.warnings : [],
    }))
}

export default function GenerateTimetableScreen({ accessToken }) {
  const initialViewState = useMemo(
    () => loadViewState(GENERATE_TIMETABLE_VIEW_STATE_KEY, { classId: '' }),
    []
  )
  const [classes, setClasses] = useState([])
  const [classId, setClassId] = useState(initialViewState.classId)
  const [entries, setEntries] = useState([])
  const [loading, setLoading] = useState(false)
  const [boardLoading, setBoardLoading] = useState(false)
  const [banner, setBanner] = useState(null)
  const [confirmMode, setConfirmMode] = useState('')
  const [selectedEntry, setSelectedEntry] = useState(null)
  const [draggingEntryId, setDraggingEntryId] = useState(null)
  const [moveOptions, setMoveOptions] = useState([])
  const [inspectedSlotKey, setInspectedSlotKey] = useState('')
  const [pendingMove, setPendingMove] = useState(null)

  const selectedClass = useMemo(
    () => classes.find((item) => String(item.id) === String(classId)) ?? null,
    [classes, classId]
  )

  const entriesBySlot = useMemo(() => {
    const map = new Map()
    entries.forEach((entry) => {
      map.set(slotKey(entry.weekday, entry.index_in_day), entry)
    })
    return map
  }, [entries])

  const optionsBySlot = useMemo(() => {
    const map = new Map()
    moveOptions.forEach((option) => {
      map.set(slotKey(option.weekday, option.index_in_day), option)
    })
    return map
  }, [moveOptions])

  const moveSummary = useMemo(() => ({
    valid: moveOptions.filter((option) => option.status === 'valid').length,
    warning: moveOptions.filter((option) => option.status === 'warning').length,
    blocked: moveOptions.filter((option) => option.status === 'blocked').length,
  }), [moveOptions])

  const activeInspectorOption = useMemo(() => {
    if (pendingMove) {
      return pendingMove
    }
    if (!inspectedSlotKey) {
      return null
    }
    return optionsBySlot.get(inspectedSlotKey) ?? null
  }, [inspectedSlotKey, optionsBySlot, pendingMove])

  useEffect(() => {
    ;(async () => {
      try {
        const data = await apiGet('/classes', accessToken)
        const list = Array.isArray(data) ? data : []
        setClasses(list)
        setClassId((current) => {
          if (list.length === 0) {
            return ''
          }
          return list.some((item) => String(item.id) === String(current))
            ? String(current)
            : String(list[0].id)
        })
      } catch (error) {
        setBanner({ type: 'error', text: String(error?.message || error) })
      }
    })()
  }, [accessToken])

  useEffect(() => {
    if (!classId) return
    loadTimetableForClass(classId)
  }, [accessToken, classId])

  useEffect(() => {
    saveViewState(GENERATE_TIMETABLE_VIEW_STATE_KEY, { classId })
  }, [classId])

  async function loadTimetableForClass(nextClassId) {
    setBoardLoading(true)
    setBanner(null)
    setSelectedEntry(null)
    setDraggingEntryId(null)
    setMoveOptions([])
    setInspectedSlotKey('')
    setPendingMove(null)
    try {
      const data = await apiGet(`/timetables/classes/${nextClassId}`, accessToken)
      setEntries(normalizeEntries(data))
    } catch (error) {
      setEntries([])
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setBoardLoading(false)
    }
  }

  async function generate() {
    if (!classId) return
    setLoading(true)
    setBanner(null)
    try {
      await apiPost('/timetables/generate', { class_id: Number(classId) }, accessToken)
      await loadTimetableForClass(classId)
      setBanner({ type: 'ok', text: 'Orarul a fost generat cu succes.' })
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setLoading(false)
    }
  }

  async function deleteTimetable() {
    if (!classId) return
    setLoading(true)
    setBanner(null)
    try {
      await apiDelete(`/timetables/classes/${classId}`, accessToken)
      setEntries([])
      setSelectedEntry(null)
      setMoveOptions([])
      setInspectedSlotKey('')
      setPendingMove(null)
      setBanner({ type: 'ok', text: 'Orarul a fost sters.' })
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setLoading(false)
      setConfirmMode('')
    }
  }

  async function deleteAndRegenerate() {
    if (!classId) return
    setLoading(true)
    setBanner(null)
    try {
      await apiDelete(`/timetables/classes/${classId}`, accessToken)
      await apiPost('/timetables/generate', { class_id: Number(classId) }, accessToken)
      await loadTimetableForClass(classId)
      setBanner({ type: 'ok', text: 'Orarul a fost regenerat cu succes.' })
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setLoading(false)
      setConfirmMode('')
    }
  }

  async function focusEntry(entry, { toggle = false } = {}) {
    if (!entry?.id) return
    if (toggle && selectedEntry?.id === entry.id && moveOptions.length > 0) {
      setSelectedEntry(null)
      setMoveOptions([])
      setInspectedSlotKey('')
      setPendingMove(null)
      return
    }

    setSelectedEntry(entry)
    setPendingMove(null)
    setInspectedSlotKey(slotKey(entry.weekday, entry.index_in_day))
    try {
      const data = await apiPost(
        `/timetables/entries/${entry.id}/move-options`,
        { entry_version: entry.version },
        accessToken
      )
      setMoveOptions(normalizeMoveOptions(data?.slot_options))
    } catch (error) {
      setMoveOptions([])
      setBanner({ type: 'error', text: String(error?.message || error) })
    }
  }

  async function executeMove(option) {
    if (!selectedEntry?.id || !option) return

    try {
      const response = await apiPost(
        `/timetables/entries/${selectedEntry.id}/move`,
        {
          entry_version: selectedEntry.version,
          target_weekday: option.weekday,
          target_index_in_day: option.index_in_day,
          mode: option.mode,
        },
        accessToken
      )
      await loadTimetableForClass(classId)
      setBanner({
        type: response?.warnings?.length > 0 ? 'warn' : 'ok',
        text: response?.warnings?.length > 0
          ? `Mutarea a fost aplicata cu avertizari: ${response.warnings.join(' | ')}`
          : 'Mutarea manuala a fost aplicata.'
      })
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    }
  }

  function canDrop(option) {
    return Boolean(option) && option.status !== 'blocked' && option.status !== 'same'
  }

  function inspectOption(option) {
    if (!option) return
    if (pendingMove && slotKey(pendingMove.weekday, pendingMove.index_in_day) === slotKey(option.weekday, option.index_in_day)) {
      return
    }
    setInspectedSlotKey(slotKey(option.weekday, option.index_in_day))
  }

  function handleDrop(option) {
    if (!canDrop(option)) {
      inspectOption(option)
      return
    }
    setInspectedSlotKey(slotKey(option.weekday, option.index_in_day))
    if (option.warnings?.length > 0) {
      setPendingMove(option)
      return
    }
    setPendingMove(null)
    executeMove(option)
  }

  function handleEntryDragStart(event, entry) {
    setDraggingEntryId(entry.id)
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', String(entry.id))
    focusEntry(entry)
  }

  function handleEntryDragEnd() {
    setDraggingEntryId(null)
  }

  function handleSlotDragOver(event, option) {
    inspectOption(option)
    if (!canDrop(option)) {
      return
    }
    event.preventDefault()
    event.dataTransfer.dropEffect = 'move'
  }

  function handleSlotDrop(event, option) {
    event.preventDefault()
    setDraggingEntryId(null)
    handleDrop(option)
  }

  return (
    <section className="contentCard timetableAdminPage">
      <div className="contentHeader">
        <div>
          <div className="title">Consola de administrare orar</div>
          <div className="subtitle">Generezi, regenerezi si ajustezi manual un orar direct din grid. Cand treci peste un slot, inspectorul din dreapta iti spune exact daca mutarea e sigura, blocata sau are warning.</div>
        </div>

        <div className="headerActions timetableAdminActions">
          <label className="label">Clasa</label>
          <select className="select" value={classId} onChange={(event) => setClassId(event.target.value)} disabled={loading || boardLoading}>
            {classes.map((schoolClass) => (
              <option key={schoolClass.id} value={String(schoolClass.id)}>
                {classLabel(schoolClass)}
              </option>
            ))}
          </select>

          <button className="btn primary" type="button" onClick={generate} disabled={loading || !classId}>
            Genereaza
          </button>
          <button className="btn" type="button" onClick={() => setConfirmMode('delete')} disabled={loading || !classId}>
            Sterge
          </button>
          <button className="btn" type="button" onClick={() => setConfirmMode('regenerate')} disabled={loading || !classId}>
            Regenereaza
          </button>
        </div>
      </div>

      {banner && <div className={`banner ${banner.type}`}>{banner.text}</div>}

      <div className="timetableAdminHero">
        <div>
          <div className="title">{classLabel(selectedClass)}</div>
          <div className="subtitle">Selecteaza un card, lasa-l sa leviteze si trage-l direct peste un alt slot. Verde inseamna valid, amber inseamna valid cu warning, gri inseamna blocat.</div>
        </div>

        <div className="timetableLegend">
          <span className="legendItem"><span className="legendSwatch is-valid"></span>Mutare valida</span>
          <span className="legendItem"><span className="legendSwatch is-warning"></span>Valida cu warning</span>
          <span className="legendItem"><span className="legendSwatch is-blocked"></span>Blocata</span>
        </div>
      </div>

      <div className="timetableAdminLayout">
        <section className="timetableBoardCard">
          {boardLoading ? (
            <div className="mutedBlock">Se incarca orarul clasei...</div>
          ) : (
            <div className={`timetablePlannerGrid ${selectedEntry ? 'has-active-entry' : ''}`.trim()}>
              <div className="plannerCorner">Interval</div>
              {WEEKDAY_LABELS.map((weekday) => (
                <div key={weekday} className="plannerDayHeader">{weekday}</div>
              ))}

              {TIME_LABELS.map((time) => (
                <div key={time.slot} className="plannerRow">
                  <div className="plannerTimeCell">
                    <strong>Ora {time.slot}</strong>
                    <span>{time.label}</span>
                  </div>

                  {WEEKDAY_LABELS.map((_, dayIndex) => {
                    const weekday = dayIndex + 1
                    const cellEntry = entriesBySlot.get(slotKey(weekday, time.slot))
                    const option = optionsBySlot.get(slotKey(weekday, time.slot))
                    const optionStatus = selectedEntry ? (option?.status || 'blocked') : ''
                    const inspected = activeInspectorOption
                      && slotKey(activeInspectorOption.weekday, activeInspectorOption.index_in_day) === slotKey(weekday, time.slot)
                    const slotClassName = [
                      'plannerSlot',
                      optionStatus ? `slot-${optionStatus}` : '',
                      selectedEntry?.id === cellEntry?.id ? 'slot-source' : '',
                      draggingEntryId ? 'is-drop-mode' : '',
                      inspected ? 'is-inspected' : '',
                    ].filter(Boolean).join(' ')
                    const entryClassName = [
                      'plannerEntry',
                      selectedEntry?.id === cellEntry?.id ? 'is-selected' : '',
                      draggingEntryId === cellEntry?.id ? 'is-dragging' : '',
                      optionStatus ? `tone-${optionStatus}` : '',
                    ].filter(Boolean).join(' ')
                    const emptyClassName = [
                      'plannerEmptySlot',
                      optionStatus ? `tone-${optionStatus}` : '',
                    ].filter(Boolean).join(' ')

                    return (
                      <div
                        key={`${weekday}-${time.slot}`}
                        className={slotClassName}
                        onMouseEnter={() => inspectOption(option)}
                        onDragOver={(event) => handleSlotDragOver(event, option)}
                        onDrop={(event) => handleSlotDrop(event, option)}
                      >
                        {cellEntry ? (
                          <button
                            className={entryClassName}
                            type="button"
                            draggable
                            aria-grabbed={selectedEntry?.id === cellEntry?.id}
                            onMouseDown={() => focusEntry(cellEntry)}
                            onMouseEnter={() => inspectOption(option)}
                            onClick={() => focusEntry(cellEntry, { toggle: true })}
                            onDragStart={(event) => handleEntryDragStart(event, cellEntry)}
                            onDragEnd={handleEntryDragEnd}
                            onDragOver={(event) => handleSlotDragOver(event, option)}
                            onDrop={(event) => handleSlotDrop(event, option)}
                          >
                            <strong>{cellEntry.subject_name}</strong>
                            <span>{cellEntry.teacher_name}</span>
                            <small>{cellEntry.room_name}</small>
                          </button>
                        ) : (
                          <button
                            className={emptyClassName}
                            type="button"
                            onMouseEnter={() => inspectOption(option)}
                            onDragOver={(event) => handleSlotDragOver(event, option)}
                            onDrop={(event) => handleSlotDrop(event, option)}
                            onClick={() => inspectOption(option)}
                            disabled={!selectedEntry || !canDrop(option)}
                          >
                            {option?.status === 'valid' ? 'Slot liber valid' : option?.status === 'warning' ? 'Slot liber cu warning' : 'Slot liber'}
                          </button>
                        )}
                      </div>
                    )
                  })}
                </div>
              ))}
            </div>
          )}
        </section>

        <aside className="timetableInspectorCard">
          <div className="title">Inspector mutare</div>
          <div className="subtitle">In dreapta vezi doar contextul slotului peste care treci acum, nu o lista separata de mutari posibile.</div>

          {selectedEntry ? (
            <>
              <div className={`inspectorEntry ${draggingEntryId === selectedEntry.id ? 'is-active' : ''}`.trim()}>
                <strong>{selectedEntry.subject_name}</strong>
                <span>{selectedEntry.teacher_name}</span>
                <small>{selectedEntry.room_name}</small>
                <small>{slotLabel(selectedEntry.weekday, selectedEntry.index_in_day)}</small>
              </div>

              <div className="inspectorSummaryGrid">
                <div className="inspectorSummaryCard valid">
                  <strong>{moveSummary.valid}</strong>
                  <span>sloturi verzi</span>
                </div>
                <div className="inspectorSummaryCard warning">
                  <strong>{moveSummary.warning}</strong>
                  <span>sloturi cu warning</span>
                </div>
                <div className="inspectorSummaryCard blocked">
                  <strong>{moveSummary.blocked}</strong>
                  <span>sloturi blocate</span>
                </div>
              </div>

              <div className="mutedBlock timetableDragHint">
                {draggingEntryId === selectedEntry.id
                  ? 'Cardul este in modul drag. Muta-l peste un alt slot si urmareste inspectorul din dreapta pentru motivul exact.'
                  : 'Selecteaza sau trage cardul. Cand treci peste un slot, inspectorul se actualizeaza cu detaliile mutarii.'}
              </div>

              {activeInspectorOption ? (
                <div className={`inspectorOptionDetail status-${activeInspectorOption.status}`.trim()}>
                  <div className="inspectorOptionHeader">
                    <span className={`inspectorStatusPill status-${activeInspectorOption.status}`.trim()}>
                      {statusLabel(activeInspectorOption.status)}
                    </span>
                    <strong>{slotLabel(activeInspectorOption.weekday, activeInspectorOption.index_in_day)}</strong>
                    <small>{modeLabel(activeInspectorOption.mode, Boolean(activeInspectorOption.target_entry_id))}</small>
                  </div>

                  {activeInspectorOption.target_entry_id ? (
                    <div className="inspectorTargetCard">
                      <span className="inspectorTargetLabel">Peste acest card</span>
                      <strong>{activeInspectorOption.target_subject_name}</strong>
                      <span>{activeInspectorOption.target_teacher_name || '-'}</span>
                      <small>{activeInspectorOption.target_room_name || '-'}</small>
                    </div>
                  ) : (
                    <div className="inspectorTargetCard is-empty">
                      <span className="inspectorTargetLabel">Slot tinta</span>
                      <strong>Slot liber</strong>
                      <small>Mutarea se aplica fara swap daca trece regulile serverului.</small>
                    </div>
                  )}

                  {activeInspectorOption.status === 'warning' && activeInspectorOption.warnings?.length > 0 && (
                    <div className="inspectorReasonList">
                      {activeInspectorOption.warnings.map((warning) => (
                        <div key={warning} className="inspectorReasonItem warning">
                          {warning}
                        </div>
                      ))}
                    </div>
                  )}

                  {activeInspectorOption.status === 'blocked' && (
                    <div className="inspectorReasonList">
                      <div className="inspectorReasonItem blocked">
                        {activeInspectorOption.blocked_reason || 'Acest slot nu respecta regulile hard pentru profesor, sala sau structura orarului.'}
                      </div>
                    </div>
                  )}

                  {activeInspectorOption.status === 'valid' && (
                    <div className="inspectorReasonList">
                      <div className="inspectorReasonItem valid">
                        Slotul este compatibil. Daca lasi cardul aici, mutarea sau swap-ul se poate aplica direct.
                      </div>
                    </div>
                  )}

                  {pendingMove && (
                    <div className="inspectorActionRow">
                      <button className="btn primary" type="button" onClick={() => executeMove(pendingMove)}>
                        Aplica mutarea cu warning
                      </button>
                      <button className="btn" type="button" onClick={() => setPendingMove(null)}>
                        Renunta
                      </button>
                    </div>
                  )}
                </div>
              ) : (
                <div className="mutedBlock">Selecteaza o ora din grid pentru a vedea unde poate fi mutata.</div>
              )}
            </>
          ) : (
            <div className="mutedBlock">Selecteaza o ora din grid pentru a vedea unde poate fi mutata.</div>
          )}
        </aside>
      </div>

      <ConfirmDialog
        open={confirmMode === 'delete'}
        title="Sterge orarul"
        description={`Esti sigur ca vrei sa stergi orarul pentru ${classLabel(selectedClass)}?`}
        confirmLabel="Sterge"
        onConfirm={deleteTimetable}
        onCancel={() => setConfirmMode('')}
        loading={loading}
      />

      <ConfirmDialog
        open={confirmMode === 'regenerate'}
        title="Regenereaza orarul"
        description={`Esti sigur ca vrei sa regenerezi complet orarul pentru ${classLabel(selectedClass)}?`}
        confirmLabel="Regenereaza"
        tone="primary"
        onConfirm={deleteAndRegenerate}
        onCancel={() => setConfirmMode('')}
        loading={loading}
      />
    </section>
  )
}
