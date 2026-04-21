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

export default function GenerateTimetableScreen({ accessToken, roles = [] }) {
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
  const [subjects, setSubjects] = useState([])
  const [rooms, setRooms] = useState([])
  const [professors, setProfessors] = useState([])
  const [allowPartial, setAllowPartial] = useState(false)
  const [unassignedItems, setUnassignedItems] = useState([])
  const [manualEntry, setManualEntry] = useState({
    subject_id: '',
    teacher_username: '',
    room_id: '',
    weekday: '1',
    index_in_day: '1',
  })
  const [externalProfessor, setExternalProfessor] = useState({
    username: '',
    password: '',
    first_name: '',
    last_name: '',
    email: '',
    subject_name: '',
  })
  const [savingManualEntry, setSavingManualEntry] = useState(false)
  const [creatingExternalProfessor, setCreatingExternalProfessor] = useState(false)
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

  const availableTeachers = useMemo(() => {
    if (!manualEntry.subject_id) return professors
    const subject = subjects.find((item) => String(item.id) === String(manualEntry.subject_id))
    if (!subject?.name) return professors
    return professors.filter((profile) => Array.isArray(profile.subjects_taught) && profile.subjects_taught.includes(subject.name))
  }, [manualEntry.subject_id, professors, subjects])

  const activeInspectorOption = useMemo(() => {
    if (pendingMove) {
      return pendingMove
    }
    if (!inspectedSlotKey) {
      return null
    }
    return optionsBySlot.get(inspectedSlotKey) ?? null
  }, [inspectedSlotKey, optionsBySlot, pendingMove])
  const canCreateExternalProfessor = roles.some((role) => ['secretariat', 'director', 'sysadmin'].includes(role))

  useEffect(() => {
    ;(async () => {
      try {
        const [classData, subjectData, roomData, professorData] = await Promise.all([
          apiGet('/classes', accessToken),
          apiGet('/subjects', accessToken),
          apiGet('/rooms', accessToken),
          apiGet('/profiles?role=professor', accessToken).catch(() => []),
        ])
        const list = Array.isArray(classData) ? classData : []
        setClasses(list)
        setSubjects(Array.isArray(subjectData) ? subjectData : [])
        setRooms(Array.isArray(roomData) ? roomData : [])
        const nextProfessors = Array.isArray(professorData) ? professorData : []
        setProfessors(nextProfessors)
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
      const response = await apiPost('/timetables/generate', { class_id: Number(classId), allow_partial: allowPartial }, accessToken)
      setUnassignedItems(Array.isArray(response?.unassigned_items) ? response.unassigned_items : [])
      await loadTimetableForClass(classId)
      setBanner({
        type: response?.partial ? 'warn' : 'ok',
        text: response?.partial
          ? 'Orarul a fost generat partial. Verifica orele ramase nealocate.'
          : 'Orarul a fost generat cu succes.'
      })
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
      const response = await apiPost('/timetables/generate', { class_id: Number(classId), allow_partial: allowPartial }, accessToken)
      setUnassignedItems(Array.isArray(response?.unassigned_items) ? response.unassigned_items : [])
      await loadTimetableForClass(classId)
      setBanner({
        type: response?.partial ? 'warn' : 'ok',
        text: response?.partial ? 'Orarul a fost regenerat partial.' : 'Orarul a fost regenerat cu succes.'
      })
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setLoading(false)
      setConfirmMode('')
    }
  }

  async function addManualEntry() {
    if (!classId || !manualEntry.subject_id || !manualEntry.weekday || !manualEntry.index_in_day) return
    setSavingManualEntry(true)
    setBanner(null)
    try {
      await apiPost(
        `/timetables/classes/${classId}/entries`,
        {
          subject_id: Number(manualEntry.subject_id),
          teacher_username: manualEntry.teacher_username || null,
          room_id: manualEntry.room_id ? Number(manualEntry.room_id) : null,
          weekday: Number(manualEntry.weekday),
          index_in_day: Number(manualEntry.index_in_day),
        },
        accessToken
      )
      await loadTimetableForClass(classId)
      setBanner({ type: 'ok', text: 'Ora a fost adaugata manual in orar.' })
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setSavingManualEntry(false)
    }
  }

  async function createExternalProfessor() {
    if (!externalProfessor.username || !externalProfessor.password || !externalProfessor.first_name || !externalProfessor.last_name || !externalProfessor.email || !externalProfessor.subject_name) {
      return
    }
    setCreatingExternalProfessor(true)
    setBanner(null)
    try {
      const created = await apiPost(
        '/profiles/external-professors',
        {
          username: externalProfessor.username.trim().toLowerCase(),
          password: externalProfessor.password,
          first_name: externalProfessor.first_name.trim(),
          last_name: externalProfessor.last_name.trim(),
          email: externalProfessor.email.trim(),
          subjects_taught: [externalProfessor.subject_name],
        },
        accessToken
      )
      setProfessors((current) => [...current, created])
      setExternalProfessor({
        username: '',
        password: '',
        first_name: '',
        last_name: '',
        email: '',
        subject_name: externalProfessor.subject_name,
      })
      setBanner({ type: 'ok', text: 'Profesorul extern a fost creat si poate fi folosit in orar.' })
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setCreatingExternalProfessor(false)
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

          <label className="label" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <input type="checkbox" checked={allowPartial} onChange={(event) => setAllowPartial(event.target.checked)} />
            Permite orar incomplet
          </label>

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
          <div className="subtitle">Selecteaza un card, lasa-l sa leviteze si trage-l direct peste un alt slot. Verde inseamna valid, amber inseamna valid cu warning, gri inseamna blocat. Daca generatorul nu poate completa totul, poti continua manual cu orele ramase.</div>
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

        <aside className="timetableInspectorCard">
          <div className="title">Ore nealocate</div>
          <div className="subtitle">Apar dupa o generare partiala si te ajuta sa completezi manual ce a ramas in afara orarului.</div>
          {unassignedItems.length === 0 ? (
            <div className="mutedBlock">Nu exista ore nealocate pentru clasa selectata.</div>
          ) : (
            <div style={{ display: 'grid', gap: 10 }}>
              {unassignedItems.map((item) => (
                <button
                  key={`${item.subject_id}-${item.missing_hours}`}
                  type="button"
                  className="plannerEmptySlot"
                  onClick={() => setManualEntry((current) => ({ ...current, subject_id: String(item.subject_id) }))}
                >
                  <strong>{item.subject_name}</strong>
                  <span>{item.missing_hours} ore lipsa</span>
                  <small>{Array.isArray(item.reason_codes) && item.reason_codes.length > 0 ? item.reason_codes.join(', ') : 'Fara detalii suplimentare'}</small>
                </button>
              ))}
            </div>
          )}
        </aside>

        <aside className="timetableInspectorCard">
          <div className="title">Completeaza manual o ora</div>
          <div className="subtitle">Selectezi materia, profesorul si slotul ramas liber, apoi adaugi manual intrarea in orar.</div>
          <div className="feedbackFieldGrid">
            <div className="field">
              <label className="label">Materie</label>
              <select className="select" value={manualEntry.subject_id} onChange={(event) => setManualEntry((current) => ({ ...current, subject_id: event.target.value, teacher_username: '' }))}>
                <option value="">Selecteaza materia</option>
                {subjects.map((subject) => (
                  <option key={subject.id} value={String(subject.id)}>
                    {subject.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label className="label">Profesor</label>
              <select className="select" value={manualEntry.teacher_username} onChange={(event) => setManualEntry((current) => ({ ...current, teacher_username: event.target.value }))}>
                <option value="">Auto sau selecteaza profesorul</option>
                {availableTeachers.map((profile) => (
                  <option key={profile.username} value={profile.username}>
                    {(profile.last_name || '')} {(profile.first_name || '')}{profile.is_external ? ' (extern)' : ''}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label className="label">Sala</label>
              <select className="select" value={manualEntry.room_id} onChange={(event) => setManualEntry((current) => ({ ...current, room_id: event.target.value }))}>
                <option value="">Sala implicita</option>
                {rooms.map((room) => (
                  <option key={room.id} value={String(room.id)}>
                    {room.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label className="label">Zi</label>
              <select className="select" value={manualEntry.weekday} onChange={(event) => setManualEntry((current) => ({ ...current, weekday: event.target.value }))}>
                {WEEKDAY_LABELS.map((label, index) => (
                  <option key={label} value={String(index + 1)}>
                    {label}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label className="label">Interval</label>
              <select className="select" value={manualEntry.index_in_day} onChange={(event) => setManualEntry((current) => ({ ...current, index_in_day: event.target.value }))}>
                {TIME_LABELS.map((time) => (
                  <option key={time.slot} value={String(time.slot)}>
                    {time.label}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <button className="btn primary" type="button" onClick={addManualEntry} disabled={savingManualEntry || !manualEntry.subject_id}>
            {savingManualEntry ? 'Se adauga...' : 'Adauga ora manual'}
          </button>
        </aside>

        <aside className="timetableInspectorCard">
          <div className="title">Profesor extern</div>
          <div className="subtitle">Creezi rapid un profesor la plata cu ora, apoi il poti selecta direct la completarea manuala.</div>
          {!canCreateExternalProfessor && (
            <div className="mutedBlock">Rolul curent poate folosi profesorii existenti, dar nu poate crea profesori externi noi.</div>
          )}
          <div className="feedbackFieldGrid">
            <div className="field">
              <label className="label">Username</label>
              <input className="input" value={externalProfessor.username} onChange={(event) => setExternalProfessor((current) => ({ ...current, username: event.target.value }))} disabled={!canCreateExternalProfessor} />
            </div>
            <div className="field">
              <label className="label">Parola</label>
              <input className="input" type="password" value={externalProfessor.password} onChange={(event) => setExternalProfessor((current) => ({ ...current, password: event.target.value }))} disabled={!canCreateExternalProfessor} />
            </div>
            <div className="field">
              <label className="label">Prenume</label>
              <input className="input" value={externalProfessor.first_name} onChange={(event) => setExternalProfessor((current) => ({ ...current, first_name: event.target.value }))} disabled={!canCreateExternalProfessor} />
            </div>
            <div className="field">
              <label className="label">Nume</label>
              <input className="input" value={externalProfessor.last_name} onChange={(event) => setExternalProfessor((current) => ({ ...current, last_name: event.target.value }))} disabled={!canCreateExternalProfessor} />
            </div>
            <div className="field">
              <label className="label">Email</label>
              <input className="input" value={externalProfessor.email} onChange={(event) => setExternalProfessor((current) => ({ ...current, email: event.target.value }))} disabled={!canCreateExternalProfessor} />
            </div>
            <div className="field">
              <label className="label">Materie</label>
              <select className="select" value={externalProfessor.subject_name} onChange={(event) => setExternalProfessor((current) => ({ ...current, subject_name: event.target.value }))} disabled={!canCreateExternalProfessor}>
                <option value="">Selecteaza materia</option>
                {subjects.map((subject) => (
                  <option key={subject.id} value={subject.name}>
                    {subject.name}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <button className="btn" type="button" onClick={createExternalProfessor} disabled={creatingExternalProfessor || !canCreateExternalProfessor}>
            {creatingExternalProfessor ? 'Se creeaza...' : 'Adauga profesor extern'}
          </button>
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
