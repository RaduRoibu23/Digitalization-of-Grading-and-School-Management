import { useEffect, useMemo, useState } from 'react'
import { apiGet, apiPut } from '../services/apiService'

function formFromProfile(profile) {
  return {
    version: profile?.version ?? null,
    first_name: profile?.first_name || '',
    last_name: profile?.last_name || '',
    email: profile?.email || '',
    address: profile?.address || '',
    settings: {
      email_notifications_enabled: profile?.settings?.email_notifications_enabled ?? true,
      in_app_notifications_enabled: profile?.settings?.in_app_notifications_enabled ?? true,
    },
  }
}

function fallback(value) {
  return value == null || String(value).trim() === '' ? '—' : value
}

export default function ProfileScreen({ accessToken, roles = [] }) {
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState(false)
  const [banner, setBanner] = useState(null)
  const [me, setMe] = useState(null)
  const [form, setForm] = useState(formFromProfile(null))

  useEffect(() => {
    ;(async () => {
      setLoading(true)
      setBanner(null)
      try {
        const data = await apiGet('/me', accessToken)
        setMe(data)
        setForm(formFromProfile(data))
      } catch (error) {
        setBanner({ type: 'error', text: String(error?.message || error) })
      } finally {
        setLoading(false)
      }
    })()
  }, [accessToken])

  const isStudent = roles.includes('student') || me?.role === 'student'
  const isProfessor = roles.includes('professor') || me?.role === 'professor'
  const requiresAddress = isStudent
  const classText = me?.class_name || me?.class?.name || (me?.class_id ? `Clasa ${me.class_id}` : '—')
  const subjectText = Array.isArray(me?.subjects_taught) && me.subjects_taught.length > 0 ? me.subjects_taught.join(', ') : '—'
  const roleText = Array.isArray(me?.roles) && me.roles.length > 0 ? me.roles.join(', ') : fallback(me?.role)

  const canSubmit = useMemo(() => {
    return Boolean(
      form.first_name.trim()
      && form.last_name.trim()
      && form.email.trim()
      && (!requiresAddress || form.address.trim())
    )
  }, [form, requiresAddress])

  function updateField(field, value) {
    setForm((current) => ({
      ...current,
      [field]: value,
    }))
  }

  function updateSetting(field, value) {
    setForm((current) => ({
      ...current,
      settings: {
        ...current.settings,
        [field]: value,
      },
    }))
  }

  function cancelEditing() {
    setEditing(false)
    setForm(formFromProfile(me))
    setBanner(null)
  }

  async function saveProfile() {
    setSaving(true)
    setBanner(null)
    try {
      const updated = await apiPut(
        '/me/profile',
        {
          version: form.version,
          first_name: form.first_name.trim(),
          last_name: form.last_name.trim(),
          email: form.email.trim(),
          address: form.address.trim() || null,
          settings: form.settings,
        },
        accessToken
      )
      setMe(updated)
      setForm(formFromProfile(updated))
      setEditing(false)
      setBanner({ type: 'ok', text: 'Profilul tau a fost actualizat.' })
    } catch (error) {
      setBanner({ type: 'error', text: String(error?.message || error) })
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="contentCard profilePageCard">
      <div className="contentHeader">
        <div>
          <div className="title">Profilul meu</div>
          <div className="subtitle">Iti poti actualiza datele personale de baza si preferintele de notificare, fara a schimba datele administrative.</div>
        </div>

        <div className="headerActions">
          {editing ? (
            <>
              <button className="btn" type="button" onClick={cancelEditing} disabled={saving}>
                Renunta
              </button>
              <button className="btn primary" type="button" onClick={saveProfile} disabled={!canSubmit || saving}>
                {saving ? 'Se salveaza...' : 'Salveaza'}
              </button>
            </>
          ) : (
            <button className="btn primary" type="button" onClick={() => setEditing(true)} disabled={loading}>
              Editeaza profilul
            </button>
          )}
        </div>
      </div>

      {banner && <div className={`banner ${banner.type}`}>{banner.text}</div>}

      {loading ? (
        <div className="mutedBlock">Se incarca datele profilului...</div>
      ) : (
        <div className="profileLayout">
          <aside className="profileHero">
            <span className="profileKicker">Date personale</span>
            <h2>{fallback(`${me?.first_name || ''} ${me?.last_name || ''}`.trim())}</h2>
            <p>Identitatea ta din platforma este conectata la rolurile si datele academice deja existente.</p>

            <div className="profileHeroMeta">
              <span className="statPill">Roluri: <strong>{roleText}</strong></span>
              <span className="statPill">Clasa: <strong>{classText}</strong></span>
              <span className="statPill">Email: <strong>{fallback(me?.email)}</strong></span>
            </div>
          </aside>

          <div className="profileColumns">
            <section className="profilePanel">
              <div className="profilePanelHeader">
                <div className="title">Date editabile</div>
                <div className="subtitle">Aceste campuri sunt sincronizate si cu identitatea ta gestionata.</div>
              </div>

              <div className="profileFormGrid">
                <div className="field">
                  <label className="label">Prenume</label>
                  <input className="input" value={form.first_name} disabled={!editing || saving} onChange={(event) => updateField('first_name', event.target.value)} />
                </div>

                <div className="field">
                  <label className="label">Nume</label>
                  <input className="input" value={form.last_name} disabled={!editing || saving} onChange={(event) => updateField('last_name', event.target.value)} />
                </div>

                <div className="field">
                  <label className="label">Email</label>
                  <input className="input" type="email" value={form.email} disabled={!editing || saving} onChange={(event) => updateField('email', event.target.value)} />
                </div>

                <div className="field profileFieldFull">
                  <label className="label">Adresa</label>
                  <input className="input" value={form.address} disabled={!editing || saving} onChange={(event) => updateField('address', event.target.value)} placeholder={requiresAddress ? 'Camp obligatoriu pentru elevi' : 'Optional'} />
                </div>
              </div>
            </section>

            <section className="profilePanel">
              <div className="profilePanelHeader">
                <div className="title">Preferinte notificari</div>
                <div className="subtitle">Controlezi separat inbox-ul din aplicatie si emailurile automate.</div>
              </div>

              <label className={`settingCard ${editing ? 'is-editable' : ''}`.trim()}>
                <input
                  type="checkbox"
                  checked={form.settings.email_notifications_enabled}
                  disabled={!editing || saving}
                  onChange={(event) => updateSetting('email_notifications_enabled', event.target.checked)}
                />
                <span>
                  <strong>Email notifications</strong>
                  <small>Trimite rezumate si alerte pe email atunci cand exista evenimente relevante.</small>
                </span>
              </label>

              <label className={`settingCard ${editing ? 'is-editable' : ''}`.trim()}>
                <input
                  type="checkbox"
                  checked={form.settings.in_app_notifications_enabled}
                  disabled={!editing || saving}
                  onChange={(event) => updateSetting('in_app_notifications_enabled', event.target.checked)}
                />
                <span>
                  <strong>In-app notifications</strong>
                  <small>Pastreaza active inbox-ul, badge-ul si toast-urile din shell-ul principal.</small>
                </span>
              </label>
            </section>
          </div>

          <section className="profilePanel profileReadonlyPanel">
            <div className="profilePanelHeader">
              <div className="title">Date administrative</div>
              <div className="subtitle">Aceste informatii raman read-only in profilul propriu.</div>
            </div>

            <div className="profileReadonlyGrid">
              <div><strong>Username</strong><span>{fallback(me?.username)}</span></div>
              <div><strong>Rol</strong><span>{roleText}</span></div>
              <div><strong>Clasa</strong><span>{classText}</span></div>
              <div><strong>Materii predate</strong><span>{isProfessor ? subjectText : '—'}</span></div>
              <div><strong>CNP</strong><span>{fallback(me?.cnp)}</span></div>
              <div><strong>Serie</strong><span>{fallback(me?.series)}</span></div>
              <div><strong>Numar serie</strong><span>{fallback(me?.serial_number)}</span></div>
              <div><strong>Initiala tatalui</strong><span>{fallback(me?.father_initial)}</span></div>
            </div>
          </section>
        </div>
      )}
    </section>
  )
}
