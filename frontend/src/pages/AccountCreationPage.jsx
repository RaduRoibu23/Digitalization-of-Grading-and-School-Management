import React, { useEffect, useState } from 'react'
import { apiGet, apiPost } from '../services/apiService'

const CREATE_ROLE_OPTIONS = [
  { value: 'student', label: 'Student' },
  { value: 'professor', label: 'Profesor' },
  { value: 'secretariat', label: 'Secretariat' },
  { value: 'scheduler', label: 'Scheduler' },
  { value: 'admin', label: 'Admin' },
  { value: 'sysadmin', label: 'Sysadmin' },
]

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

export default function AccountCreationScreen({ accessToken, roles = [] }) {
  const canCreateAccounts = roles.includes('sysadmin')
  const [loading, setLoading] = useState(false)
  const [creating, setCreating] = useState(false)
  const [banner, setBanner] = useState(null)
  const [classes, setClasses] = useState([])
  const [subjects, setSubjects] = useState([])
  const [createdProfile, setCreatedProfile] = useState(null)
  const [form, setForm] = useState(createFormState())

  useEffect(() => {
    if (!canCreateAccounts) {
      return
    }

    ;(async () => {
      setLoading(true)
      try {
        const [classData, subjectData] = await Promise.all([
          apiGet('/classes', accessToken),
          apiGet('/subjects', accessToken),
        ])

        const nextClasses = Array.isArray(classData) ? classData : []
        const nextSubjects = Array.isArray(subjectData) ? subjectData : []

        setClasses(nextClasses)
        setSubjects(nextSubjects)
        setForm((current) => ({
          ...current,
          class_id: current.class_id || (nextClasses[0] ? String(nextClasses[0].id) : ''),
          subject_name: current.subject_name || (nextSubjects[0]?.name || ''),
        }))
      } catch (error) {
        setBanner({ type: 'error', text: String(error?.message || error) })
      } finally {
        setLoading(false)
      }
    })()
  }, [accessToken, canCreateAccounts])

  function updateField(field, value) {
    setForm((current) => {
      if (field === 'role') {
        return {
          ...current,
          role: value,
          class_id: value === 'student' ? (current.class_id || (classes[0] ? String(classes[0].id) : '')) : '',
          subject_name: value === 'professor' ? (current.subject_name || (subjects[0]?.name || '')) : '',
        }
      }

      if (field === 'username') {
        return {
          ...current,
          username: value.toLowerCase(),
        }
      }

      return {
        ...current,
        [field]: value,
      }
    })
  }

  function resetForm() {
    setForm({
      ...createFormState(),
      class_id: classes[0] ? String(classes[0].id) : '',
      subject_name: subjects[0]?.name || '',
    })
  }

  async function createProfile() {
    setCreating(true)
    setBanner(null)
    try {
      const created = await apiPost(
        '/profiles',
        {
          username: form.username.trim().toLowerCase(),
          password: form.password,
          role: form.role,
          first_name: form.first_name.trim(),
          last_name: form.last_name.trim(),
          email: form.email.trim(),
          class_id: form.role === 'student' && form.class_id ? Number(form.class_id) : null,
          subjects_taught: form.role === 'professor' && form.subject_name ? [form.subject_name] : [],
        },
        accessToken
      )

      setCreatedProfile(created)
      setBanner({ type: 'ok', text: `Contul ${created.username} a fost creat.` })
      resetForm()
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setCreating(false)
    }
  }

  const canSubmit = Boolean(
    canCreateAccounts
      && form.username.trim()
      && form.password
      && form.first_name.trim()
      && form.last_name.trim()
      && form.email.trim()
      && (form.role !== 'student' || form.class_id)
      && (form.role !== 'professor' || form.subject_name)
  )

  if (!canCreateAccounts) {
    return (
      <section className="contentCard">
        <div className="contentHeader">
          <div>
            <div className="title">Creeaza cont</div>
            <div className="subtitle">Modul disponibil doar pentru sysadmin.</div>
          </div>
        </div>
        <div className="banner error">Nu ai acces la crearea de conturi.</div>
      </section>
    )
  }

  return (
    <section className="contentCard">
      <div className="contentHeader">
        <div>
          <div className="title">Creeaza cont</div>
          <div className="subtitle">Consola dedicata pentru sysadmin, separata de lista de utilizatori.</div>
        </div>
        <div className="headerActions studentHeaderActions">
          <button className="btn" onClick={resetForm} disabled={creating || loading}>
            Reseteaza formularul
          </button>
          <button className="btn primary" onClick={createProfile} disabled={!canSubmit || creating || loading}>
            {creating ? 'Se creeaza...' : 'Creeaza cont'}
          </button>
        </div>
      </div>

      {banner && <div className={`banner ${banner.type}`}>{banner.text}</div>}

      <div className="mutedBlock" style={{ marginBottom: 18 }}>
        Username-ul se salveaza automat cu litere mici pentru compatibilitate cu autentificarea. Pentru elevi, adresa, CNP-ul, seria, numarul de serie si initiala prenumelui tatalui se genereaza automat la creare.
      </div>

      {loading ? (
        <div className="mutedBlock">Se incarca datele necesare...</div>
      ) : (
        <div style={{ display: 'grid', gap: 14, gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))' }}>
          <div className="field">
            <label className="label">Rol</label>
            <select className="select" value={form.role} onChange={(event) => updateField('role', event.target.value)} disabled={creating}>
              {CREATE_ROLE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>

          <div className="field">
            <label className="label">Username</label>
            <input className="input" value={form.username} onChange={(event) => updateField('username', event.target.value)} disabled={creating} />
          </div>

          <div className="field">
            <label className="label">Parola</label>
            <input className="input" type="password" value={form.password} onChange={(event) => updateField('password', event.target.value)} disabled={creating} />
          </div>

          <div className="field">
            <label className="label">Prenume</label>
            <input className="input" value={form.first_name} onChange={(event) => updateField('first_name', event.target.value)} disabled={creating} />
          </div>

          <div className="field">
            <label className="label">Nume</label>
            <input className="input" value={form.last_name} onChange={(event) => updateField('last_name', event.target.value)} disabled={creating} />
          </div>

          <div className="field">
            <label className="label">Email</label>
            <input className="input" type="email" value={form.email} onChange={(event) => updateField('email', event.target.value)} disabled={creating} />
          </div>

          {form.role === 'student' && (
            <div className="field">
              <label className="label">Clasa</label>
              <select className="select" value={form.class_id} onChange={(event) => updateField('class_id', event.target.value)} disabled={creating}>
                <option value="">Selecteaza clasa</option>
                {classes.map((schoolClass) => (
                  <option key={schoolClass.id} value={String(schoolClass.id)}>
                    {schoolClass.profile ? `${schoolClass.name} - ${schoolClass.profile}` : schoolClass.name}
                  </option>
                ))}
              </select>
            </div>
          )}

          {form.role === 'professor' && (
            <div className="field">
              <label className="label">Materia predata</label>
              <select className="select" value={form.subject_name} onChange={(event) => updateField('subject_name', event.target.value)} disabled={creating}>
                <option value="">Selecteaza materia</option>
                {subjects.map((subject) => (
                  <option key={subject.id || subject.name} value={subject.name}>
                    {subject.name}
                  </option>
                ))}
              </select>
            </div>
          )}
        </div>
      )}

      {createdProfile && (
        <div className="mutedBlock" style={{ marginTop: 18 }}>
          <div style={{ display: 'grid', gap: 8 }}>
            <div><strong>Ultimul cont creat:</strong> {createdProfile.username}</div>
            <div><strong>Rol:</strong> {roleLabel(createdProfile.role)}</div>
            <div><strong>Email:</strong> {createdProfile.email || '-'}</div>
            <div><strong>Clasa:</strong> {createdProfile.class_name || '-'}</div>
            {createdProfile.role === 'student' && <div><strong>Serie:</strong> {createdProfile.series || '-'}</div>}
            {createdProfile.role === 'student' && <div><strong>Nr. serie:</strong> {createdProfile.serial_number || '-'}</div>}
            {createdProfile.role === 'student' && <div><strong>Initiala prenumelui tatalui:</strong> {createdProfile.father_initial || '-'}</div>}
          </div>
        </div>
      )}
    </section>
  )
}
