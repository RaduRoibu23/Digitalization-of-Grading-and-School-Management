import React, { useEffect, useMemo, useRef, useState } from 'react'
import TextPromptDialog from '../components/ui/TextPromptDialog'
import { apiDownload, apiGet, apiPatch, apiPost } from '../services/apiService'
import { loadViewState, saveViewState } from '../services/viewState'

const PAGE_SIZE = 6
const CATALOG_VIEW_STATE_KEY = 'catalog'
const GRADE_COMMENT_LIMIT = 1000
const ABSENCE_REASON_LIMIT = 1000

function studentLabel(student) {
  if (!student) return ''
  const name = `${student.last_name || ''} ${student.first_name || ''}`.trim()
  const className = student.class_name || ''
  return className ? `${name} - ${className}` : name
}

function formatDate(value) {
  if (!value) return '-'
  const parts = String(value).split('-')
  if (parts.length !== 3) return value
  return `${parts[2]}.${parts[1]}.${parts[0]}`
}

function formatAverage(value) {
  if (value === null || value === undefined) return ''
  return Number(value).toFixed(2)
}

function gradeTone(value) {
  if (value >= 9) return 'gradeBadge excellent'
  if (value >= 7) return 'gradeBadge good'
  return 'gradeBadge warn'
}

function normalizeOptionalText(value) {
  const normalized = String(value || '').trim()
  return normalized.length > 0 ? normalized : null
}

function extractFilename(response, fallbackName) {
  const contentDisposition = response.headers.get('content-disposition') || ''
  const quotedMatch = contentDisposition.match(/filename="([^"]+)"/i)
  if (quotedMatch?.[1]) {
    return quotedMatch[1]
  }
  return fallbackName
}

export default function CatalogScreen({ accessToken, roles }) {
  const initialViewState = useMemo(
    () => loadViewState(CATALOG_VIEW_STATE_KEY, {
      selectedStudent: '',
      search: '',
      page: 1,
    }),
    []
  )
  const [loading, setLoading] = useState(false)
  const [banner, setBanner] = useState(null)
  const [students, setStudents] = useState([])
  const [selectedStudent, setSelectedStudent] = useState(initialViewState.selectedStudent)
  const [catalog, setCatalog] = useState(null)
  const [drafts, setDrafts] = useState({})
  const [newGrades, setNewGrades] = useState({})
  const [newAbsences, setNewAbsences] = useState({})
  const [addingSubject, setAddingSubject] = useState(null)
  const [addingAbsenceSubject, setAddingAbsenceSubject] = useState(null)
  const [motivatingAbsenceId, setMotivatingAbsenceId] = useState(null)
  const [motivationDialog, setMotivationDialog] = useState({ open: false, absence: null, reason: '' })
  const [gradeRequestDialog, setGradeRequestDialog] = useState({ open: false, grade: null, mode: 'update', reason: '' })
  const [requestingGradeId, setRequestingGradeId] = useState(null)
  const [changeRequests, setChangeRequests] = useState([])
  const [loadingRequests, setLoadingRequests] = useState(false)
  const [reviewingRequestId, setReviewingRequestId] = useState(null)
  const [exportClasses, setExportClasses] = useState([])
  const [exportClassId, setExportClassId] = useState('')
  const [exportingCatalog, setExportingCatalog] = useState(false)
  const [search, setSearch] = useState(initialViewState.search)
  const [page, setPage] = useState(initialViewState.page)
  const catalogRequestRef = useRef(0)

  const canBrowseStudents = useMemo(
    () => roles.some((role) => ['professor', 'secretariat', 'director', 'sysadmin'].includes(role)),
    [roles]
  )
  const requiresMotivationReason = useMemo(() => roles.includes('parent'), [roles])
  const canReviewGradeRequests = useMemo(
    () => roles.some((role) => ['secretariat', 'director'].includes(role)),
    [roles]
  )

  useEffect(() => {
    if (canBrowseStudents) {
      loadStudents()
      return
    }
    loadMyCatalog()
  }, [accessToken, canBrowseStudents])

  useEffect(() => {
    if (!canBrowseStudents || !selectedStudent) return
    loadStudentCatalog(selectedStudent)
  }, [accessToken, canBrowseStudents, selectedStudent])

  useEffect(() => {
    if (!accessToken || !(canReviewGradeRequests || roles.includes('professor'))) return
    loadChangeRequests()
  }, [accessToken, canReviewGradeRequests, roles])

  useEffect(() => {
    if (!accessToken || !(roles.includes('director') || roles.includes('sysadmin') || roles.includes('professor'))) return
    loadExportableClasses()
  }, [accessToken, roles])

  useEffect(() => {
    setPage(1)
  }, [search, selectedStudent])

  async function loadStudents() {
    setLoading(true)
    setBanner(null)
    try {
      const data = await apiGet('/catalog/students', accessToken)
      const list = Array.isArray(data) ? data : []
      setStudents(list)
      setSelectedStudent((current) => {
        if (list.length === 0) {
          return ''
        }
        return list.some((item) => item.username === current)
          ? current
          : list[0].username
      })
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setLoading(false)
    }
  }

  async function loadMyCatalog() {
    const requestId = ++catalogRequestRef.current
    setLoading(true)
    setBanner(null)
    try {
      const data = await apiGet('/catalog/me', accessToken)
      if (catalogRequestRef.current !== requestId) return
      applyCatalog(data)
    } catch (error) {
      if (catalogRequestRef.current !== requestId) return
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      if (catalogRequestRef.current === requestId) {
        setLoading(false)
      }
    }
  }

  async function loadStudentCatalog(username) {
    const requestId = ++catalogRequestRef.current
    setLoading(true)
    setBanner(null)
    try {
      const data = await apiGet(`/catalog/students/${username}`, accessToken)
      if (catalogRequestRef.current !== requestId) return
      applyCatalog(data)
    } catch (error) {
      if (catalogRequestRef.current !== requestId) return
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      if (catalogRequestRef.current === requestId) {
        setLoading(false)
      }
    }
  }

  async function loadChangeRequests() {
    setLoadingRequests(true)
    try {
      const data = await apiGet('/catalog/grade-change-requests', accessToken)
      setChangeRequests(Array.isArray(data) ? data : [])
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setLoadingRequests(false)
    }
  }

  async function loadExportableClasses() {
    try {
      const data = await apiGet('/catalog/exportable-classes', accessToken)
      const list = Array.isArray(data) ? data : []
      setExportClasses(list)
      setExportClassId((current) => {
        if (list.length === 0) return ''
        return list.some((item) => String(item.id) === String(current)) ? current : String(list[0].id)
      })
    } catch {
      setExportClasses([])
      setExportClassId('')
    }
  }

  function applyCatalog(data) {
    const nextCatalog = data && typeof data === 'object' ? data : null
    setCatalog(nextCatalog)

    const rows = Array.isArray(nextCatalog?.subjects) ? nextCatalog.subjects : []
    const nextDrafts = {}
    const nextNewGrades = {}
    const nextNewAbsences = {}

    rows.forEach((row) => {
      const grades = Array.isArray(row.grades) ? row.grades : []
      grades.forEach((grade) => {
        nextDrafts[grade.id] = {
          grade_value: grade.grade_value,
          grade_date: grade.grade_date,
          comment: grade.comment || '',
          version: grade.version,
        }
      })
      nextNewGrades[row.subject_name] = {
        grade_value: '',
        grade_date: '',
        comment: '',
      }
      nextNewAbsences[row.subject_name] = {
        absence_date: '',
      }
    })

    setDrafts(nextDrafts)
    setNewGrades(nextNewGrades)
    setNewAbsences(nextNewAbsences)
  }

  function updateDraft(gradeId, field, value) {
    setDrafts((current) => ({
      ...current,
      [gradeId]: {
        ...current[gradeId],
        [field]: field === 'grade_value' ? (value === '' ? '' : Number(value)) : value,
      },
    }))
  }

  function updateNewGrade(subjectName, field, value) {
    setNewGrades((current) => ({
      ...current,
      [subjectName]: {
        ...current[subjectName],
        [field]: field === 'grade_value' ? (value === '' ? '' : Number(value)) : value,
      },
    }))
  }

  function updateNewAbsence(subjectName, field, value) {
    setNewAbsences((current) => ({
      ...current,
      [subjectName]: {
        ...current[subjectName],
        [field]: value,
      },
    }))
  }

  async function reloadCurrentCatalog() {
    if (canBrowseStudents) {
      if (!selectedStudent) return
      await loadStudentCatalog(selectedStudent)
    } else {
      await loadMyCatalog()
    }
    if (canReviewGradeRequests || roles.includes('professor')) {
      await loadChangeRequests()
    }
  }

  async function addGrade(row) {
    const draft = newGrades[row.subject_name]
    const student = catalog?.student
    if (!draft || !student) return

    setAddingSubject(row.subject_name)
    setBanner(null)
    try {
      await apiPost(
        '/catalog/grades',
        {
          student_username: student.username,
          subject_name: row.subject_name,
          grade_value: Number(draft.grade_value),
          grade_date: draft.grade_date,
          comment: normalizeOptionalText(draft.comment),
        },
        accessToken
      )
      await reloadCurrentCatalog()
      setBanner({ type: 'ok', text: 'Nota a fost adaugata.' })
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setAddingSubject(null)
    }
  }

  async function addAbsence(row) {
    const draft = newAbsences[row.subject_name]
    const student = catalog?.student
    if (!draft || !student) return

    setAddingAbsenceSubject(row.subject_name)
    setBanner(null)
    try {
      await apiPost(
        '/catalog/absences',
        {
          student_username: student.username,
          subject_name: row.subject_name,
          absence_date: draft.absence_date,
        },
        accessToken
      )
      await reloadCurrentCatalog()
      setBanner({ type: 'ok', text: 'Absenta a fost adaugata.' })
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setAddingAbsenceSubject(null)
    }
  }

  function openMotivationDialog(absence) {
    setMotivationDialog({
      open: true,
      absence,
      reason: absence?.motivation_reason || '',
    })
  }

  async function motivateAbsence(reason) {
    if (!motivationDialog.absence) return

    setMotivatingAbsenceId(motivationDialog.absence.id)
    setBanner(null)
    try {
      await apiPatch(
        `/catalog/absences/${motivationDialog.absence.id}/motivate`,
        {
          version: motivationDialog.absence.version,
          reason: normalizeOptionalText(reason),
        },
        accessToken
      )
      setMotivationDialog({ open: false, absence: null, reason: '' })
      await reloadCurrentCatalog()
      setBanner({ type: 'ok', text: 'Absenta a fost motivata.' })
    } catch (error) {
      if ([409, 412, 423].includes(error?.status)) {
        setBanner({ type: 'error', text: 'Absenta a fost modificata intre timp. Actualizeaza catalogul si incearca din nou.' })
      } else {
        setBanner({ type: 'error', text: String(error?.message || error) })
      }
    } finally {
      setMotivatingAbsenceId(null)
    }
  }


  function openGradeRequestDialog(grade, mode) {
    setGradeRequestDialog({
      open: true,
      grade,
      mode,
      reason: '',
    })
  }

  async function submitGradeRequest(reason) {
    const grade = gradeRequestDialog.grade
    if (!grade) return

    const draft = drafts[grade.id] || {
      grade_value: grade.grade_value,
      grade_date: grade.grade_date,
      comment: grade.comment || '',
    }
    setRequestingGradeId(grade.id)
    setBanner(null)
    try {
      await apiPost(
        `/catalog/grades/${grade.id}/change-requests`,
        {
          request_type: gradeRequestDialog.mode === 'delete' ? 'DELETE' : 'UPDATE',
          proposed_grade_value: gradeRequestDialog.mode === 'delete' ? null : Number(draft.grade_value),
          proposed_grade_date: gradeRequestDialog.mode === 'delete' ? null : draft.grade_date,
          proposed_comment: gradeRequestDialog.mode === 'delete' ? null : normalizeOptionalText(draft.comment),
          reason: normalizeOptionalText(reason),
        },
        accessToken
      )
      setGradeRequestDialog({ open: false, grade: null, mode: 'update', reason: '' })
      await reloadCurrentCatalog()
      setBanner({ type: 'ok', text: 'Cererea a fost trimisa pentru aprobare.' })
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setRequestingGradeId(null)
    }
  }

  async function reviewGradeRequest(requestId, action) {
    setReviewingRequestId(requestId)
    setBanner(null)
    try {
      await apiPatch(`/catalog/grade-change-requests/${requestId}/${action}`, { resolution_note: null }, accessToken)
      await reloadCurrentCatalog()
      setBanner({ type: 'ok', text: action === 'approve' ? 'Cererea a fost aprobata.' : 'Cererea a fost respinsa.' })
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setReviewingRequestId(null)
    }
  }

  async function downloadClassCatalog() {
    if (!exportClassId) return
    setExportingCatalog(true)
    setBanner(null)
    try {
      const response = await apiDownload(`/catalog/classes/${exportClassId}/export`, accessToken)
      const blob = await response.blob()
      const objectUrl = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = objectUrl
      link.download = extractFilename(response, `catalog-${exportClassId}.xlsx`)
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(objectUrl)
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setExportingCatalog(false)
    }
  }

  const student = catalog?.student || null
  const subjects = Array.isArray(catalog?.subjects) ? catalog.subjects : []
  const totalGrades = subjects.reduce((total, row) => total + (Array.isArray(row.grades) ? row.grades.length : 0), 0)
  const totalAbsences = subjects.reduce((total, row) => total + (Array.isArray(row.absences) ? row.absences.length : 0), 0)
  const subjectsWithAverage = subjects.filter((row) => row.average !== null && row.average !== undefined).length
  const filteredSubjects = useMemo(() => {
    const query = search.trim().toLowerCase()
    if (!query) return subjects
    return subjects.filter((row) => {
      const teachers = Array.isArray(row.teacher_names) ? row.teacher_names.join(' ') : ''
      return `${row.subject_name || ''} ${teachers}`.toLowerCase().includes(query)
    })
  }, [search, subjects])

  const totalPages = Math.max(1, Math.ceil(filteredSubjects.length / PAGE_SIZE))
  const currentPage = Math.min(page, totalPages)
  const paginatedSubjects = filteredSubjects.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE)

  useEffect(() => {
    saveViewState(CATALOG_VIEW_STATE_KEY, {
      selectedStudent,
      search,
      page: currentPage,
    })
  }, [currentPage, search, selectedStudent])

  useEffect(() => {
    if (page !== currentPage) {
      setPage(currentPage)
    }
  }, [currentPage, page])

  return (
    <section className="contentCard">
      <div className="contentHeader">
        <div>
          <div className="title">Catalog</div>
          <div className="subtitle">Media se afiseaza doar daca exista minim numarul de note cerut pentru materia respectiva. Comentariile profesorului apar doar utilizatorilor autorizati.</div>
        </div>
        <div className="headerActions">
          {canBrowseStudents && (
            <>
              <label className="label" htmlFor="catalog-student-select">Elev</label>
              <select
                id="catalog-student-select"
                className="select"
                value={selectedStudent}
                onChange={(event) => setSelectedStudent(event.target.value)}
                disabled={loading || students.length === 0}
              >
                {students.length === 0 ? (
                  <option value="">
                    {loading ? 'Se incarca elevii...' : 'Niciun elev disponibil'}
                  </option>
                ) : (
                  students.map((item) => (
                    <option key={item.username} value={item.username}>
                      {studentLabel(item)}
                    </option>
                  ))
                )}
              </select>
            </>
          )}
          <input
            className="input"
            aria-label="Cauta materie sau profesor"
            placeholder="Cauta materie sau profesor"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
          <button className="btn" onClick={reloadCurrentCatalog} disabled={loading || (canBrowseStudents && !selectedStudent)}>
            Actualizeaza
          </button>
        </div>
      </div>

      {banner && <div className={`banner ${banner.type}`}>{banner.text}</div>}

      {exportClasses.length > 0 && (
        <div className="mutedBlock" style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap', marginBottom: 18 }}>
          <strong>Export catalog clasa</strong>
          <select className="select" value={exportClassId} onChange={(event) => setExportClassId(event.target.value)} disabled={exportingCatalog}>
            {exportClasses.map((item) => (
              <option key={item.id} value={String(item.id)}>
                {item.name} {item.profile ? `- ${item.profile}` : ''}
              </option>
            ))}
          </select>
          <button className="btn" onClick={downloadClassCatalog} disabled={!exportClassId || exportingCatalog}>
            {exportingCatalog ? 'Se exporta...' : 'Exporta catalogul clasei'}
          </button>
        </div>
      )}

      {(canReviewGradeRequests || changeRequests.length > 0) && (
        <div className="mutedBlock" style={{ marginBottom: 18 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center', marginBottom: 10, flexWrap: 'wrap' }}>
            <strong>Cereri pentru modificarea notelor</strong>
            <button className="btn" onClick={loadChangeRequests} disabled={loadingRequests}>
              {loadingRequests ? 'Se incarca...' : 'Actualizeaza cererile'}
            </button>
          </div>

          {changeRequests.length === 0 ? (
            <div className="mutedSmall">Nu exista cereri vizibile pentru contul curent.</div>
          ) : (
            <div style={{ display: 'grid', gap: 10 }}>
              {changeRequests.map((request) => (
                <div key={request.id} className="catalogGradeItem">
                  <div className="catalogPair" style={{ alignItems: 'center' }}>
                    <strong>{request.request_type === 'DELETE' ? 'Cerere stergere nota' : 'Cerere modificare nota'}</strong>
                    <span className={`gradeBadge ${request.status === 'PENDING' ? 'warn' : request.status === 'APPROVED' ? 'good' : 'gradeBadge'}`}>
                      {request.status}
                    </span>
                  </div>
                  <div className="catalogHint">
                    Curent: {request.current_grade_value ?? '-'} / {formatDate(request.current_grade_date)}.
                    {request.request_type === 'UPDATE' ? ` Propus: ${request.proposed_grade_value ?? '-'} / ${formatDate(request.proposed_grade_date)}.` : ' Propus: stergere.'}
                  </div>
                  <div className="catalogHint">Motiv: {request.reason}</div>
                  <div className="catalogHint">Solicitat de: {request.requested_by_username}</div>
                  {request.resolution_note && <div className="catalogHint">Rezolvare: {request.resolution_note}</div>}
                  {request.can_review && request.status === 'PENDING' && (
                    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 8 }}>
                      <button className="btn primary" onClick={() => reviewGradeRequest(request.id, 'approve')} disabled={reviewingRequestId === request.id}>
                        Aproba
                      </button>
                      <button className="btn danger" onClick={() => reviewGradeRequest(request.id, 'reject')} disabled={reviewingRequestId === request.id}>
                        Respinge
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {student && (
        <div className="catalogStats">
          <div className="statPill">
            <strong>Elev:</strong> {student.first_name} {student.last_name}
          </div>
          <div className="statPill">
            <strong>Clasa:</strong> {student.class_name || '-'}
          </div>
          <div className="statPill">
            <strong>Materii afisate:</strong> {filteredSubjects.length}
          </div>
          <div className="statPill">
            <strong>Medii calculate:</strong> {subjectsWithAverage}
          </div>
          <div className="statPill">
            <strong>Total note:</strong> {totalGrades}
          </div>
          <div className="statPill">
            <strong>Total absente:</strong> {totalAbsences}
          </div>
        </div>
      )}

      {loading ? (
        <div className="mutedBlock">Se incarca datele din catalog...</div>
      ) : !student ? (
        <div className="mutedBlock">Nu exista date pentru catalog.</div>
      ) : filteredSubjects.length === 0 ? (
        <div className="mutedBlock">Nu exista materii pentru filtrul selectat.</div>
      ) : (
        <>
          <div className="tableWrap">
            <table className="tbl">
              <thead>
                <tr>
                  <th>Materie</th>
                  <th>Medie</th>
                  <th>Nota si data</th>
                  <th>Absente</th>
                  <th>Profesor</th>
                </tr>
              </thead>
              <tbody>
                {paginatedSubjects.map((row) => {
                  const grades = Array.isArray(row.grades) ? row.grades : []
                  const absences = Array.isArray(row.absences) ? row.absences : []
                  const teachers = Array.isArray(row.teacher_names) ? row.teacher_names : []
                  const addDraft = newGrades[row.subject_name] || { grade_value: '', grade_date: '', comment: '' }
                  const addAbsenceDraft = newAbsences[row.subject_name] || { absence_date: '' }

                  return (
                    <tr key={row.subject_name}>
                      <td>
                        <div className="cellTitle">{row.subject_name}</div>
                        <div className="catalogHint">{row.weekly_hours} ore/saptamana</div>
                      </td>
                      <td>
                        <div className="averageCell">{formatAverage(row.average)}</div>
                        {row.average === null || row.average === undefined ? (
                          <div className="catalogHint">minim {row.minimum_grades_for_average} note</div>
                        ) : null}
                      </td>
                      <td>
                        {grades.length === 0 && !row.can_add ? (
                          <span className="mutedSmall">-</span>
                        ) : (
                          <div className="catalogGradeList">
                            {grades.map((grade) => {
                              const draft = drafts[grade.id] || {
                                grade_value: grade.grade_value,
                                grade_date: grade.grade_date,
                                comment: grade.comment || '',
                                version: grade.version,
                              }
                              const visibleComment = normalizeOptionalText(grade.comment)
                              return (
                                <div key={grade.id} className="catalogGradeItem">
                                  {grade.can_request_change ? (
                                    <div className="catalogEditor">
                                      <div className="catalogPair">
                                        <span className={gradeTone(Number(grade.grade_value || 0))}>{grade.grade_value}</span>
                                        <span className="catalogDate">{formatDate(grade.grade_date)}</span>
                                      </div>
                                      <input
                                        className="input small"
                                        type="number"
                                        min="1"
                                        max="10"
                                        value={draft.grade_value}
                                        onChange={(event) => updateDraft(grade.id, 'grade_value', event.target.value)}
                                      />
                                      <input
                                        className="input small"
                                        type="date"
                                        value={draft.grade_date}
                                        onChange={(event) => updateDraft(grade.id, 'grade_date', event.target.value)}
                                      />
                                      <textarea
                                        className="input small"
                                        rows={3}
                                        maxLength={GRADE_COMMENT_LIMIT}
                                        value={draft.comment}
                                        onChange={(event) => updateDraft(grade.id, 'comment', event.target.value)}
                                        placeholder="Comentariu optional vizibil pentru elev, parinte si staff autorizat"
                                        style={{ resize: 'vertical', minHeight: 84 }}
                                      />
                                      <button
                                        className="btn primary"
                                        onClick={() => openGradeRequestDialog(grade, 'update')}
                                        disabled={requestingGradeId === grade.id || !draft.grade_date || !draft.grade_value || grade.pending_request_id}
                                      >
                                        Solicita modificare
                                      </button>
                                      <button
                                        className="btn danger"
                                        onClick={() => openGradeRequestDialog(grade, 'delete')}
                                        disabled={requestingGradeId === grade.id || grade.pending_request_id}
                                      >
                                        Solicita stergere
                                      </button>
                                    </div>
                                  ) : (
                                    <>
                                      <div className="catalogPair">
                                        <span className={gradeTone(Number(grade.grade_value || 0))}>{grade.grade_value}</span>
                                        <span className="catalogDate">{formatDate(grade.grade_date)}</span>
                                      </div>
                                      {visibleComment && (
                                        <div className="catalogHint" style={{ whiteSpace: 'pre-wrap' }}>
                                          Comentariu: {visibleComment}
                                        </div>
                                      )}
                                      {grade.pending_request_id && (
                                        <div className="catalogHint">
                                          Exista o cerere {String(grade.pending_request_type || '').toLowerCase()} in status {String(grade.pending_request_status || '').toLowerCase()}.
                                        </div>
                                      )}
                                    </>
                                  )}
                                </div>
                              )
                            })}

                            {row.can_add && (
                              <div className="catalogGradeItem">
                                <div className="catalogEditor">
                                  <input
                                    className="input small"
                                    type="number"
                                    min="1"
                                    max="10"
                                    value={addDraft.grade_value}
                                    onChange={(event) => updateNewGrade(row.subject_name, 'grade_value', event.target.value)}
                                    placeholder="Nota"
                                  />
                                  <input
                                    className="input small"
                                    type="date"
                                    value={addDraft.grade_date}
                                    onChange={(event) => updateNewGrade(row.subject_name, 'grade_date', event.target.value)}
                                  />
                                  <textarea
                                    className="input small"
                                    rows={3}
                                    maxLength={GRADE_COMMENT_LIMIT}
                                    value={addDraft.comment}
                                    onChange={(event) => updateNewGrade(row.subject_name, 'comment', event.target.value)}
                                    placeholder="Comentariu optional vizibil pentru elev, parinte si staff autorizat"
                                    style={{ resize: 'vertical', minHeight: 84 }}
                                  />
                                  <button
                                    className="btn primary"
                                    onClick={() => addGrade(row)}
                                    disabled={addingSubject === row.subject_name || !addDraft.grade_date || !addDraft.grade_value}
                                  >
                                    Adauga
                                  </button>
                                </div>
                              </div>
                            )}
                          </div>
                        )}
                      </td>
                      <td>
                        {absences.length === 0 && !row.can_add ? (
                          <span className="mutedSmall">-</span>
                        ) : (
                          <div className="catalogGradeList">
                            {absences.map((absence) => (
                              <div key={absence.id} className="catalogGradeItem">
                                <div className="catalogPair">
                                  <span className={absence.motivated ? 'gradeBadge good' : 'gradeBadge warn'}>
                                    {absence.motivated ? 'Motivata' : 'Nemotivata'}
                                  </span>
                                  <span className="catalogDate">{formatDate(absence.absence_date)}</span>
                                </div>
                                <div className="catalogHint">
                                  {absence.motivated
                                    ? `de ${absence.motivated_by_name || absence.motivated_by_username || '-'}`
                                    : `adaugata de ${absence.teacher_name || '-'}`}
                                </div>
                                {absence.motivation_reason && (
                                  <div className="catalogHint" style={{ whiteSpace: 'pre-wrap' }}>
                                    Motiv: {absence.motivation_reason}
                                  </div>
                                )}
                                {absence.motivatable && (
                                  <button
                                    className="btn primary"
                                    onClick={() => openMotivationDialog(absence)}
                                    disabled={motivatingAbsenceId === absence.id}
                                  >
                                    Motiveaza
                                  </button>
                                )}
                              </div>
                            ))}

                            {row.can_add && (
                              <div className="catalogGradeItem">
                                <div className="catalogEditor">
                                  <input
                                    className="input small"
                                    type="date"
                                    value={addAbsenceDraft.absence_date}
                                    onChange={(event) => updateNewAbsence(row.subject_name, 'absence_date', event.target.value)}
                                  />
                                  <button
                                    className="btn primary"
                                    onClick={() => addAbsence(row)}
                                    disabled={addingAbsenceSubject === row.subject_name || !addAbsenceDraft.absence_date}
                                  >
                                    Adauga absenta
                                  </button>
                                </div>
                              </div>
                            )}
                          </div>
                        )}
                      </td>
                      <td>{teachers.length > 0 ? teachers.join(', ') : '-'}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          <div className="paginationBar">
            <div className="mutedSmall">Pagina {currentPage} din {totalPages}</div>
            <div className="paginationActions">
              <button className="btn" onClick={() => setPage(1)} disabled={currentPage === 1}>Prima</button>
              <button className="btn" onClick={() => setPage((value) => Math.max(1, value - 1))} disabled={currentPage === 1}>Inapoi</button>
              <button className="btn" onClick={() => setPage((value) => Math.min(totalPages, value + 1))} disabled={currentPage === totalPages}>Inainte</button>
              <button className="btn" onClick={() => setPage(totalPages)} disabled={currentPage === totalPages}>Ultima</button>
            </div>
          </div>
        </>
      )}

      <TextPromptDialog
        open={motivationDialog.open}
        title="Motiveaza absenta"
        description={requiresMotivationReason
          ? 'Introdu motivul motivarii. Pentru parinte, acest camp este obligatoriu.'
          : 'Poti adauga optional un motiv pentru motivarea absentei.'}
        label={requiresMotivationReason ? 'Motiv' : 'Motiv optional'}
        placeholder="ex: consult medical, problema familiala, document justificativ"
        confirmLabel="Motiveaza absenta"
        tone="primary"
        value={motivationDialog.reason}
        onValueChange={(reason) => setMotivationDialog((current) => ({ ...current, reason }))}
        maxLength={ABSENCE_REASON_LIMIT}
        requireValue={requiresMotivationReason}
        loading={motivatingAbsenceId === motivationDialog.absence?.id}
        onCancel={() => setMotivationDialog({ open: false, absence: null, reason: '' })}
        onConfirm={motivateAbsence}
      />

      <TextPromptDialog
        open={gradeRequestDialog.open}
        title={gradeRequestDialog.mode === 'delete' ? 'Solicita stergerea notei' : 'Solicita modificarea notei'}
        description={gradeRequestDialog.mode === 'delete'
          ? 'Introdu motivul pentru care nota ar trebui stearsa. Cererea va ajunge la secretariat si director.'
          : 'Introdu motivul modificarii. Valorile propuse din editor vor fi trimise pentru aprobare.'}
        label="Motiv"
        placeholder="ex: eroare de introducere, re-evaluare aprobata, corectie administrativa"
        confirmLabel={gradeRequestDialog.mode === 'delete' ? 'Trimite cererea de stergere' : 'Trimite cererea de modificare'}
        tone="primary"
        value={gradeRequestDialog.reason}
        onValueChange={(reason) => setGradeRequestDialog((current) => ({ ...current, reason }))}
        maxLength={255}
        requireValue
        loading={requestingGradeId === gradeRequestDialog.grade?.id}
        onCancel={() => setGradeRequestDialog({ open: false, grade: null, mode: 'update', reason: '' })}
        onConfirm={submitGradeRequest}
      />
    </section>
  )
}
