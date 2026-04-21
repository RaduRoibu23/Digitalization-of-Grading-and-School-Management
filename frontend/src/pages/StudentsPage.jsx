import React, { useEffect, useMemo, useState } from 'react'
import { apiGet, apiPut } from '../services/apiService'
import { loadViewState, saveViewState } from '../services/viewState'

const PAGE_SIZE = 15
const STUDENTS_VIEW_STATE_KEY = 'students'
const ROLE_OPTIONS = [
  { value: 'student', label: 'Studenti' },
  { value: 'parent', label: 'Parinti' },
  { value: 'professor', label: 'Profesori' },
  { value: 'secretariat', label: 'Secretariat' },
  { value: 'scheduler', label: 'Scheduler' },
  { value: 'director', label: 'Director' },
  { value: 'sysadmin', label: 'Sysadmin' },
  { value: '', label: 'Toate rolurile' },
]

function buildProfileName(profile) {
  const lastName = profile.last_name || profile.lastName || ''
  const firstName = profile.first_name || profile.firstName || ''
  return `${lastName} ${firstName}`.trim() || 'Fara nume'
}

function buildInitials(name) {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() || '')
    .join('')
}

function linkedStudentLabel(profile) {
  return profile.linked_student_name || profile.linked_student_username || '-'
}

function classLabel(profile) {
  return profile.class_name
    || profile.className
    || profile.linked_student_class_name
    || profile.linkedStudentClassName
    || profile.homeroom_class_name
    || profile.homeroomClassName
    || (profile.class_id ? `Clasa ${profile.class_id}` : profile.linked_student_class_id ? `Clasa ${profile.linked_student_class_id}` : '-')
}

function roleLabel(role) {
  switch (role) {
    case 'student':
      return 'Student'
    case 'parent':
      return 'Parinte'
    case 'professor':
      return 'Profesor'
    case 'secretariat':
      return 'Secretariat'
    case 'scheduler':
      return 'Scheduler'
    case 'director':
      return 'Director'
    case 'sysadmin':
      return 'Sysadmin'
    default:
      return role || '-'
  }
}

function formFromProfile(profile) {
  return {
    version: profile?.version ?? null,
    first_name: profile?.first_name || profile?.firstName || '',
    last_name: profile?.last_name || profile?.lastName || '',
    email: profile?.email || '',
    class_id: profile?.class_id ? String(profile.class_id) : '',
    homeroom_class_id: profile?.homeroom_class_id ? String(profile.homeroom_class_id) : '',
    linked_student_username: profile?.linked_student_username || '',
    address: profile?.address || '',
    cnp: profile?.cnp || '',
    series: profile?.series || '',
    serial_number: profile?.serial_number || profile?.serialNumber || '',
    father_initial: profile?.father_initial || profile?.fatherInitial || '',
  }
}

function studentOptionLabel(profile) {
  if (!profile) return '-'
  const fullName = buildProfileName(profile)
  return `${fullName} (${profile.username}) - ${classLabel(profile)}`
}

export default function StudentsScreen({ accessToken, roles = [] }) {
  const initialViewState = useMemo(
    () => loadViewState(STUDENTS_VIEW_STATE_KEY, {
      roleFilter: 'student',
      search: '',
      sortBy: null,
      page: 1,
    }),
    []
  )
  const canManageProfiles = roles.includes('secretariat') || roles.includes('director') || roles.includes('sysadmin')
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [banner, setBanner] = useState(null)
  const [profiles, setProfiles] = useState([])
  const [classes, setClasses] = useState([])
  const [students, setStudents] = useState([])
  const [sortBy, setSortBy] = useState(initialViewState.sortBy)
  const [search, setSearch] = useState(initialViewState.search)
  const [page, setPage] = useState(initialViewState.page)
  const [roleFilter, setRoleFilter] = useState(initialViewState.roleFilter)
  const [editingUsername, setEditingUsername] = useState('')
  const [form, setForm] = useState(formFromProfile(null))

  useEffect(() => {
    ;(async () => {
      setLoading(true)
      try {
        const [profileData, classData, studentData] = await Promise.all([
          apiGet(`/profiles${roleFilter ? `?role=${encodeURIComponent(roleFilter)}` : ''}`, accessToken),
          canManageProfiles ? apiGet('/classes', accessToken) : Promise.resolve([]),
          canManageProfiles ? apiGet('/profiles?role=student', accessToken) : Promise.resolve([]),
        ])
        const nextProfiles = Array.isArray(profileData) ? profileData : []
        const nextClasses = Array.isArray(classData) ? classData : []
        const nextStudents = Array.isArray(studentData) ? studentData : []

        setProfiles(nextProfiles)
        setClasses(nextClasses)
        setStudents(nextStudents)
        if (editingUsername) {
          const freshProfile = nextProfiles.find((profile) => profile.username === editingUsername)
          if (!freshProfile) {
            setEditingUsername('')
            setForm(formFromProfile(null))
          } else {
            setForm(formFromProfile(freshProfile))
          }
        }
      } catch (error) {
        setBanner({ type: 'error', text: String(error?.message || error) })
      } finally {
        setLoading(false)
      }
    })()
  }, [accessToken, canManageProfiles, roleFilter, editingUsername])

  useEffect(() => {
    setPage(1)
  }, [search, sortBy, roleFilter])

  const editingProfile = useMemo(
    () => profiles.find((profile) => profile.username === editingUsername) || null,
    [profiles, editingUsername]
  )

  const filteredProfiles = useMemo(() => {
    const query = search.trim().toLowerCase()
    let list = profiles
    if (query) {
      list = list.filter((profile) => {
        const username = profile.username || ''
        const role = profile.role || ''
        const email = profile.email || ''
        const address = profile.address || ''
        const cnp = profile.cnp || ''
        const series = profile.series || ''
        const serialNumber = profile.serial_number || profile.serialNumber || ''
        const fatherInitial = profile.father_initial || profile.fatherInitial || ''
        const linkedStudent = linkedStudentLabel(profile)
        return `${username} ${buildProfileName(profile)} ${classLabel(profile)} ${role} ${email} ${address} ${cnp} ${series} ${serialNumber} ${fatherInitial} ${linkedStudent}`
          .toLowerCase()
          .includes(query)
      })
    }

    if (!sortBy) {
      return list
    }

    const sorted = [...list]
    sorted.sort((a, b) => {
      if (sortBy === 'last_name') {
        return buildProfileName(a).localeCompare(buildProfileName(b))
      }
      if (sortBy === 'class_name') {
        return classLabel(a).localeCompare(classLabel(b))
      }
      if (sortBy === 'role') {
        return roleLabel(a.role).localeCompare(roleLabel(b.role))
      }
      return 0
    })
    return sorted
  }, [profiles, search, sortBy])

  const totalPages = Math.max(1, Math.ceil(filteredProfiles.length / PAGE_SIZE))
  const currentPage = Math.min(page, totalPages)
  const pageStart = (currentPage - 1) * PAGE_SIZE
  const paginatedProfiles = filteredProfiles.slice(pageStart, pageStart + PAGE_SIZE)

  useEffect(() => {
    saveViewState(STUDENTS_VIEW_STATE_KEY, {
      roleFilter,
      search,
      sortBy,
      page: currentPage,
    })
  }, [currentPage, roleFilter, search, sortBy])

  useEffect(() => {
    if (page !== currentPage) {
      setPage(currentPage)
    }
  }, [currentPage, page])

  function beginEdit(profile) {
    setEditingUsername(profile.username || '')
    setForm(formFromProfile(profile))
    setBanner(null)
  }

  function cancelEdit() {
    setEditingUsername('')
    setForm(formFromProfile(null))
  }

  function updateField(field, value) {
    setForm((current) => ({
      ...current,
      [field]: value,
    }))
  }

  async function saveProfile() {
    if (!editingProfile) return
    setSaving(true)
    setBanner(null)
    try {
      const updated = await apiPut(
        `/profiles/${editingProfile.username}`,
        {
          version: form.version,
          first_name: form.first_name.trim(),
          last_name: form.last_name.trim(),
          email: form.email.trim(),
          class_id: editingProfile.role === 'student' && form.class_id ? Number(form.class_id) : null,
          homeroom_class_id: editingProfile.role === 'professor' && form.homeroom_class_id ? Number(form.homeroom_class_id) : null,
          linked_student_username: editingProfile.role === 'parent' ? (form.linked_student_username || null) : null,
          address: form.address.trim() || null,
          cnp: form.cnp.trim() || null,
          series: editingProfile.role === 'student' ? (form.series.trim().toUpperCase() || null) : null,
          serial_number: editingProfile.role === 'student' ? (form.serial_number.trim() || null) : null,
          father_initial: editingProfile.role === 'student' ? (form.father_initial.trim().toUpperCase() || null) : null,
        },
        accessToken
      )

      setProfiles((current) =>
        current.map((profile) => (profile.username === updated.username ? updated : profile))
      )
      setForm(formFromProfile(updated))
      setBanner({ type: 'ok', text: 'Profilul a fost actualizat.' })
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setSaving(false)
    }
  }

  const title = canManageProfiles ? 'Utilizatori' : 'Lista utilizatori'
  const subtitle = canManageProfiles
    ? 'Secretariatul, directorul si sysadmin-ul pot filtra si modifica profiluri din toate rolurile.'
    : 'Vizualizare clara a utilizatorilor din sistem, cu cautare si paginare.'

  return (
    <section className="contentCard">
      <div className="contentHeader">
        <div>
          <div className="title">{title}</div>
          <div className="subtitle">{subtitle}</div>
        </div>
        <div className="headerActions studentHeaderActions">
          {canManageProfiles && (
            <select
              className="select"
              value={roleFilter}
              onChange={(event) => setRoleFilter(event.target.value)}
              disabled={loading || saving}
            >
              {ROLE_OPTIONS.map((option) => (
                <option key={option.value || 'all'} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          )}
          <input
            className="input"
            placeholder="Cauta dupa nume, username, rol, email, adresa, CNP, clasa sau elev asociat"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
          <button
            className={`btn ${sortBy === 'last_name' ? 'primary' : ''}`}
            onClick={() => setSortBy(sortBy === 'last_name' ? null : 'last_name')}
            disabled={loading || profiles.length === 0}
          >
            Sorteaza dupa nume
          </button>
          <button
            className={`btn ${sortBy === 'class_name' ? 'primary' : ''}`}
            onClick={() => setSortBy(sortBy === 'class_name' ? null : 'class_name')}
            disabled={loading || profiles.length === 0}
          >
            Sorteaza dupa clasa
          </button>
          {canManageProfiles && (
            <button
              className={`btn ${sortBy === 'role' ? 'primary' : ''}`}
              onClick={() => setSortBy(sortBy === 'role' ? null : 'role')}
              disabled={loading || profiles.length === 0}
            >
              Sorteaza dupa rol
            </button>
          )}
        </div>
      </div>

      <div className="catalogStats studentStats">
        <div className="statPill">Total in lista: <strong>{profiles.length}</strong></div>
        <div className="statPill">Rezultate filtrate: <strong>{filteredProfiles.length}</strong></div>
        <div className="statPill">Pagina curenta: <strong>{currentPage}</strong></div>
      </div>

      {banner && <div className={`banner ${banner.type}`}>{banner.text}</div>}

      {roles.includes('sysadmin') && (
        <div className="mutedBlock" style={{ marginBottom: 18 }}>
          Pentru conturi noi foloseste intrarea separata <strong>Creeaza cont</strong> din meniul principal.
        </div>
      )}

      {canManageProfiles && editingProfile && (
        <div className="mutedBlock" style={{ marginBottom: 18 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, alignItems: 'center', flexWrap: 'wrap', marginBottom: 14 }}>
            <div>
              <strong>Editezi:</strong> {buildProfileName(editingProfile)} ({roleLabel(editingProfile.role)})
            </div>
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
              <button className="btn primary" onClick={saveProfile} disabled={saving}>
                {saving ? 'Se salveaza...' : 'Salveaza'}
              </button>
              <button className="btn" onClick={cancelEdit} disabled={saving}>
                Renunta
              </button>
            </div>
          </div>

          <div style={{ display: 'grid', gap: 14, gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))' }}>
            <div className="field">
              <label className="label">Nume</label>
              <input className="input" value={form.last_name} onChange={(event) => updateField('last_name', event.target.value)} disabled={saving} />
            </div>
            <div className="field">
              <label className="label">Prenume</label>
              <input className="input" value={form.first_name} onChange={(event) => updateField('first_name', event.target.value)} disabled={saving} />
            </div>
            <div className="field">
              <label className="label">Email</label>
              <input className="input" value={form.email} onChange={(event) => updateField('email', event.target.value)} disabled={saving} />
            </div>
            {editingProfile.role === 'student' && (
              <div className="field">
                <label className="label">Clasa</label>
                <select className="select" value={form.class_id} onChange={(event) => updateField('class_id', event.target.value)} disabled={saving}>
                  <option value="">Selecteaza clasa</option>
                  {classes.map((schoolClass) => (
                    <option key={schoolClass.id} value={String(schoolClass.id)}>
                      {schoolClass.profile ? `${schoolClass.name} - ${schoolClass.profile}` : schoolClass.name}
                    </option>
                  ))}
                </select>
              </div>
            )}
            {editingProfile.role === 'professor' && (
              <div className="field">
                <label className="label">Diriginte la clasa</label>
                <select className="select" value={form.homeroom_class_id} onChange={(event) => updateField('homeroom_class_id', event.target.value)} disabled={saving}>
                  <option value="">Fara dirigentie</option>
                  {classes.map((schoolClass) => (
                    <option key={schoolClass.id} value={String(schoolClass.id)}>
                      {schoolClass.profile ? `${schoolClass.name} - ${schoolClass.profile}` : schoolClass.name}
                    </option>
                  ))}
                </select>
              </div>
            )}
            {editingProfile.role === 'parent' && (
              <div className="field">
                <label className="label">Elev asociat</label>
                <select className="select" value={form.linked_student_username} onChange={(event) => updateField('linked_student_username', event.target.value)} disabled={saving}>
                  <option value="">Selecteaza elevul</option>
                  {students.map((student) => (
                    <option key={student.username} value={student.username}>
                      {studentOptionLabel(student)}
                    </option>
                  ))}
                </select>
              </div>
            )}
            <div className="field">
              <label className="label">Adresa</label>
              <input className="input" value={form.address} onChange={(event) => updateField('address', event.target.value)} disabled={saving} />
            </div>
            <div className="field">
              <label className="label">CNP</label>
              <input className="input" value={form.cnp} onChange={(event) => updateField('cnp', event.target.value)} disabled={saving} />
            </div>
            {editingProfile.role === 'student' && (
              <div className="field">
                <label className="label">Serie</label>
                <input
                  className="input"
                  value={form.series}
                  onChange={(event) => updateField('series', event.target.value)}
                  disabled={saving}
                  maxLength={2}
                />
              </div>
            )}
            {editingProfile.role === 'student' && (
              <div className="field">
                <label className="label">Nr. serie</label>
                <input
                  className="input"
                  value={form.serial_number}
                  onChange={(event) => updateField('serial_number', event.target.value)}
                  disabled={saving}
                  maxLength={6}
                />
              </div>
            )}
            {editingProfile.role === 'student' && (
              <div className="field">
                <label className="label">Initiala prenumelui tatalui</label>
                <input
                  className="input"
                  value={form.father_initial}
                  onChange={(event) => updateField('father_initial', event.target.value)}
                  disabled={saving}
                  maxLength={1}
                />
              </div>
            )}
          </div>
        </div>
      )}

      {loading ? (
        <div className="mutedBlock">Se incarca lista...</div>
      ) : filteredProfiles.length === 0 ? (
        <div className="mutedBlock">Nu exista profiluri pentru filtrul selectat.</div>
      ) : (
        <>
          <div className="tableWrap studentTableWrap">
            <table className="tbl studentTable">
              <thead>
                <tr>
                  <th className="thin">#</th>
                  <th>{canManageProfiles ? 'Persoana' : 'Utilizator'}</th>
                  <th>Username</th>
                  {canManageProfiles && <th>Rol</th>}
                  <th>Clasa</th>
                  {canManageProfiles && <th>Adresa</th>}
                  {canManageProfiles && <th>CNP</th>}
                  {canManageProfiles && <th>Actiuni</th>}
                </tr>
              </thead>
              <tbody>
                {paginatedProfiles.map((profile, index) => {
                  const fullName = buildProfileName(profile)
                  const currentClassName = classLabel(profile)
                  const associationText = profile.role === 'parent' ? `Parinte pentru ${linkedStudentLabel(profile)}` : null
                  return (
                    <tr key={profile.id || profile.username}>
                      <td className="thin">{pageStart + index + 1}</td>
                      <td>
                        <div className="studentNameCell">
                          <div className="studentInitials">{buildInitials(fullName)}</div>
                          <div>
                            <div className="studentName">{fullName}</div>
                            <div className="studentMeta">{associationText || profile.email || 'Fara email'}</div>
                          </div>
                        </div>
                      </td>
                      <td>
                        <span className="studentUsername">@{profile.username || '-'}</span>
                      </td>
                      {canManageProfiles && (
                        <td>
                          <span className="studentClassBadge">{roleLabel(profile.role)}</span>
                        </td>
                      )}
                      <td>
                        <span className="studentClassBadge">{currentClassName}</span>
                      </td>
                      {canManageProfiles && <td>{profile.address || '-'}</td>}
                      {canManageProfiles && <td>{profile.cnp || '-'}</td>}
                      {canManageProfiles && (
                        <td>
                          <button className="btn" onClick={() => beginEdit(profile)} disabled={saving}>
                            {editingUsername === profile.username ? 'Editezi' : 'Editeaza'}
                          </button>
                        </td>
                      )}
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
    </section>
  )
}
