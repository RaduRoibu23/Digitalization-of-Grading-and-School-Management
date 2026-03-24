import React, { useEffect, useMemo, useState } from 'react'
import { apiGet, apiPost, apiPut } from '../services/apiService'

const PAGE_SIZE = 15
const ROLE_OPTIONS = [
  { value: 'student', label: 'Studenti' },
  { value: 'professor', label: 'Profesori' },
  { value: 'secretariat', label: 'Secretariat' },
  { value: 'scheduler', label: 'Scheduler' },
  { value: 'admin', label: 'Admin' },
  { value: 'sysadmin', label: 'Sysadmin' },
  { value: '', label: 'Toate rolurile' },
]

const CREATE_ROLE_OPTIONS = [
  { value: 'student', label: 'Student' },
  { value: 'professor', label: 'Profesor' },
  { value: 'secretariat', label: 'Secretariat' },
  { value: 'scheduler', label: 'Scheduler' },
  { value: 'admin', label: 'Admin' },
  { value: 'sysadmin', label: 'Sysadmin' },
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

function classLabel(profile) {
  return profile.class_name || profile.className || profile.homeroom_class_name || profile.homeroomClassName || (profile.class_id ? `Clasa ${profile.class_id}` : '-')
}

function roleLabel(role) {
  switch (role) {
    case 'student':
      return 'Student'
    case 'professor':
      return 'Profesor'
    case 'secretariat':
      return 'Secretariat'
    case 'scheduler':
      return 'Scheduler'
    case 'admin':
      return 'Admin'
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
    address: profile?.address || '',
    cnp: profile?.cnp || '',
  }
}

function createFormState() {
  return {
    username: '',
    password: '',
    role: 'student',
    first_name: '',
    last_name: '',
    email: '',
    class_id: '',
    subject_name: '',
  }
}

export default function StudentsScreen({ accessToken, roles = [] }) {
  const canManageProfiles = roles.includes('secretariat') || roles.includes('sysadmin')
  const canCreateAccounts = roles.includes('sysadmin')
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [creating, setCreating] = useState(false)
  const [banner, setBanner] = useState(null)
  const [profiles, setProfiles] = useState([])
  const [classes, setClasses] = useState([])
  const [subjects, setSubjects] = useState([])
  const [sortBy, setSortBy] = useState(null)
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(1)
  const [roleFilter, setRoleFilter] = useState('student')
  const [editingUsername, setEditingUsername] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  const [form, setForm] = useState(formFromProfile(null))
  const [createForm, setCreateForm] = useState(createFormState())
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    ;(async () => {
      setLoading(true)
      try {
        const [profileData, classData, subjectData] = await Promise.all([
          apiGet(`/profiles${roleFilter ? `?role=${encodeURIComponent(roleFilter)}` : ''}`, accessToken),
          canManageProfiles ? apiGet('/classes', accessToken) : Promise.resolve([]),
          canCreateAccounts ? apiGet('/subjects', accessToken) : Promise.resolve([]),
        ])
        setProfiles(Array.isArray(profileData) ? profileData : [])
        setClasses(Array.isArray(classData) ? classData : [])
        setSubjects(Array.isArray(subjectData) ? subjectData : [])
        if (canCreateAccounts) {
          setCreateForm((current) => ({
            ...current,
            class_id: current.class_id || (Array.isArray(classData) && classData.length > 0 ? String(classData[0].id) : ''),
            subject_name: current.subject_name || (Array.isArray(subjectData) && subjectData.length > 0 ? subjectData[0].name : ''),
          }))
        }
        if (editingUsername) {
          const freshProfile = (Array.isArray(profileData) ? profileData : []).find((profile) => profile.username === editingUsername)
          if (!freshProfile) {
            setEditingUsername('')
            setForm(formFromProfile(null))
          }
        }
      } catch (error) {
        setBanner({ type: 'error', text: String(error?.message || error) })
      } finally {
        setLoading(false)
      }
    })()
  }, [accessToken, canCreateAccounts, canManageProfiles, roleFilter, editingUsername, reloadKey])

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
        return `${username} ${buildProfileName(profile)} ${classLabel(profile)} ${role} ${email} ${address} ${cnp}`
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

  function updateCreateField(field, value) {
    setCreateForm((current) => {
      if (field !== 'role') {
        return {
          ...current,
          [field]: value,
        }
      }

      return {
        ...current,
        role: value,
        class_id: value === 'student' ? (current.class_id || (classes[0] ? String(classes[0].id) : '')) : '',
        subject_name: value === 'professor' ? (current.subject_name || (subjects[0]?.name || '')) : '',
      }
    })
  }

  function resetCreateForm() {
    setCreateForm({
      ...createFormState(),
      class_id: classes.length > 0 ? String(classes[0].id) : '',
      subject_name: subjects.length > 0 ? subjects[0].name : '',
    })
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
          address: form.address.trim() || null,
          cnp: form.cnp.trim() || null,
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

  async function createProfile() {
    setCreating(true)
    setBanner(null)
    try {
      const created = await apiPost(
        '/profiles',
        {
          username: createForm.username.trim(),
          password: createForm.password,
          role: createForm.role,
          first_name: createForm.first_name.trim(),
          last_name: createForm.last_name.trim(),
          email: createForm.email.trim(),
          class_id: createForm.role === 'student' && createForm.class_id ? Number(createForm.class_id) : null,
          subjects_taught: createForm.role === 'professor' && createForm.subject_name ? [createForm.subject_name] : [],
        },
        accessToken
      )

      setCreateOpen(false)
      resetCreateForm()
      setReloadKey((current) => current + 1)
      if (!roleFilter || roleFilter === created.role) {
        setProfiles((current) => [created, ...current.filter((profile) => profile.username !== created.username)])
      }
      setBanner({ type: 'ok', text: `Contul ${created.username} a fost creat.` })
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setCreating(false)
    }
  }

  const title = canManageProfiles ? 'Administrare persoane' : 'Lista studenti'
  const subtitle = canManageProfiles
    ? 'Secretariatul si sysadmin-ul pot filtra si modifica profiluri, iar sysadmin-ul poate crea conturi noi.'
    : 'Vizualizare clara a elevilor din sistem, cu cautare si paginare.'
  const canSubmitCreate = Boolean(
    canCreateAccounts
      && createForm.username.trim()
      && createForm.password
      && createForm.first_name.trim()
      && createForm.last_name.trim()
      && createForm.email.trim()
      && (createForm.role !== 'student' || createForm.class_id)
      && (createForm.role !== 'professor' || createForm.subject_name)
  )

  return (
    <section className="contentCard">
      <div className="contentHeader">
        <div>
          <div className="title">{title}</div>
          <div className="subtitle">{subtitle}</div>
        </div>
        <div className="headerActions studentHeaderActions">
          {canCreateAccounts && (
            <button
              className={`btn ${createOpen ? 'primary' : ''}`}
              onClick={() => {
                const nextOpen = !createOpen
                setCreateOpen(nextOpen)
                setBanner(null)
                resetCreateForm()
              }}
              disabled={loading || saving || creating}
            >
              {createOpen ? 'Ascunde formularul' : 'Cont nou'}
            </button>
          )}
          {canManageProfiles && (
            <select
              className="select"
              value={roleFilter}
              onChange={(event) => setRoleFilter(event.target.value)}
              disabled={loading || saving || creating}
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
            placeholder="Cauta dupa nume, username, rol, email, adresa, CNP sau clasa"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
          <button
            className={`btn ${sortBy === 'last_name' ? 'primary' : ''}`}
            onClick={() => setSortBy(sortBy === 'last_name' ? null : 'last_name')}
            disabled={loading || profiles.length === 0 || creating}
          >
            Sorteaza dupa nume
          </button>
          <button
            className={`btn ${sortBy === 'class_name' ? 'primary' : ''}`}
            onClick={() => setSortBy(sortBy === 'class_name' ? null : 'class_name')}
            disabled={loading || profiles.length === 0 || creating}
          >
            Sorteaza dupa clasa
          </button>
          {canManageProfiles && (
            <button
              className={`btn ${sortBy === 'role' ? 'primary' : ''}`}
              onClick={() => setSortBy(sortBy === 'role' ? null : 'role')}
              disabled={loading || profiles.length === 0 || creating}
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

      {canCreateAccounts && createOpen && (
        <div className="mutedBlock" style={{ marginBottom: 18 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, alignItems: 'center', flexWrap: 'wrap', marginBottom: 14 }}>
            <div>
              <strong>Cont nou:</strong> creeaza un utilizator nou direct din consola de administrare.
            </div>
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
              <button className="btn primary" onClick={createProfile} disabled={!canSubmitCreate || creating}>
                {creating ? 'Se creeaza...' : 'Creeaza cont'}
              </button>
              <button
                className="btn"
                onClick={() => {
                  setCreateOpen(false)
                  resetCreateForm()
                }}
                disabled={creating}
              >
                Renunta
              </button>
            </div>
          </div>

          <div style={{ display: 'grid', gap: 14, gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))' }}>
            <div className="field">
              <label className="label">Rol</label>
              <select className="select" value={createForm.role} onChange={(event) => updateCreateField('role', event.target.value)} disabled={creating}>
                {CREATE_ROLE_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label className="label">Username</label>
              <input className="input" value={createForm.username} onChange={(event) => updateCreateField('username', event.target.value)} disabled={creating} />
            </div>
            <div className="field">
              <label className="label">Parola</label>
              <input className="input" type="password" value={createForm.password} onChange={(event) => updateCreateField('password', event.target.value)} disabled={creating} />
            </div>
            <div className="field">
              <label className="label">Prenume</label>
              <input className="input" value={createForm.first_name} onChange={(event) => updateCreateField('first_name', event.target.value)} disabled={creating} />
            </div>
            <div className="field">
              <label className="label">Nume</label>
              <input className="input" value={createForm.last_name} onChange={(event) => updateCreateField('last_name', event.target.value)} disabled={creating} />
            </div>
            <div className="field">
              <label className="label">Email</label>
              <input className="input" type="email" value={createForm.email} onChange={(event) => updateCreateField('email', event.target.value)} disabled={creating} />
            </div>
            {createForm.role === 'student' && (
              <div className="field">
                <label className="label">Clasa</label>
                <select className="select" value={createForm.class_id} onChange={(event) => updateCreateField('class_id', event.target.value)} disabled={creating}>
                  <option value="">Selecteaza clasa</option>
                  {classes.map((schoolClass) => (
                    <option key={schoolClass.id} value={String(schoolClass.id)}>
                      {schoolClass.profile ? `${schoolClass.name} - ${schoolClass.profile}` : schoolClass.name}
                    </option>
                  ))}
                </select>
              </div>
            )}
            {createForm.role === 'professor' && (
              <div className="field">
                <label className="label">Materia predata</label>
                <select className="select" value={createForm.subject_name} onChange={(event) => updateCreateField('subject_name', event.target.value)} disabled={creating}>
                  <option value="">Selecteaza materia</option>
                  {subjects.map((subject) => (
                    <option key={subject.id || subject.name} value={subject.name}>
                      {subject.name}
                    </option>
                  ))}
                </select>
              </div>
            )}
            {createForm.role === 'student' && (
              <div className="mutedSmall" style={{ alignSelf: 'end' }}>
                Adresa si CNP-ul se genereaza automat la creare si apar imediat in profilul elevului.
              </div>
            )}
          </div>
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
            <div className="field">
              <label className="label">Adresa</label>
              <input className="input" value={form.address} onChange={(event) => updateField('address', event.target.value)} disabled={saving} />
            </div>
            <div className="field">
              <label className="label">CNP</label>
              <input className="input" value={form.cnp} onChange={(event) => updateField('cnp', event.target.value)} disabled={saving} />
            </div>
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
                  <th>{canManageProfiles ? 'Persoana' : 'Elev'}</th>
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
                  const className = classLabel(profile)
                  return (
                    <tr key={profile.id || profile.username}>
                      <td className="thin">{pageStart + index + 1}</td>
                      <td>
                        <div className="studentNameCell">
                          <div className="studentInitials">{buildInitials(fullName)}</div>
                          <div>
                            <div className="studentName">{fullName}</div>
                            <div className="studentMeta">{profile.email || 'Fara email'}</div>
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
                        <span className="studentClassBadge">{className}</span>
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
