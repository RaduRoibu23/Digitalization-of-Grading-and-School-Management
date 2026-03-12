import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { loadPublicClasses, registerAccount } from '../services/authService'

export default function Register() {
  const navigate = useNavigate()
  const [classes, setClasses] = useState([])
  const [loading, setLoading] = useState(false)
  const [loadingClasses, setLoadingClasses] = useState(true)
  const [banner, setBanner] = useState(null)
  const [form, setForm] = useState({
    username: '',
    password: '',
    confirmPassword: '',
    first_name: '',
    last_name: '',
    email: '',
    class_id: '',
  })

  useEffect(() => {
    let active = true
    ;(async () => {
      try {
        const data = await loadPublicClasses()
        if (!active) return
        setClasses(data)
        if (data.length > 0) {
          setForm((current) => ({ ...current, class_id: current.class_id || String(data[0].id) }))
        }
      } catch (error) {
        if (!active) return
        setBanner({ type: 'error', text: String(error?.message || error) })
      } finally {
        if (active) {
          setLoadingClasses(false)
        }
      }
    })()
    return () => {
      active = false
    }
  }, [])

  const canSubmit = useMemo(() => {
    const hasRequired = form.username && form.password && form.confirmPassword && form.first_name && form.last_name && form.email
    const classReady = classes.length === 0 || form.class_id
    return hasRequired && classReady && !loading
  }, [classes.length, form, loading])

  const updateField = (field, value) => {
    setForm((current) => ({
      ...current,
      [field]: value,
    }))
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    setBanner(null)

    if (form.password !== form.confirmPassword) {
      setBanner({ type: 'error', text: 'Parolele nu coincid.' })
      return
    }

    setLoading(true)
    try {
      await registerAccount({
        username: form.username.trim(),
        password: form.password,
        first_name: form.first_name.trim(),
        last_name: form.last_name.trim(),
        email: form.email.trim(),
        class_id: form.class_id ? Number(form.class_id) : null,
      })
      navigate('/login', {
        replace: true,
        state: {
          registered: true,
          username: form.username.trim(),
        },
      })
    } catch (error) {
      const message = String(error?.message || error)
      setBanner({
        type: 'error',
        text: message.includes('Username') ? 'Username folosit deja' : message,
      })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="loginPage">
      <div className="loginCard registerCard">
        <div className="title">Register</div>
        <div className="subtitle">Creeaza un cont de elev in aplicatie</div>

        <form className="needs-validation" onSubmit={handleSubmit}>
          <div className="twoColFields">
            <div className="field">
              <div className="label">Prenume</div>
              <input className="input form-control" value={form.first_name} onChange={(event) => updateField('first_name', event.target.value)} required />
            </div>
            <div className="field">
              <div className="label">Nume</div>
              <input className="input form-control" value={form.last_name} onChange={(event) => updateField('last_name', event.target.value)} required />
            </div>
          </div>

          <div className="field">
            <div className="label">Email</div>
            <input className="input form-control" type="email" value={form.email} onChange={(event) => updateField('email', event.target.value)} required />
          </div>

          <div className="twoColFields">
            <div className="field">
              <div className="label">Username</div>
              <input className="input form-control" value={form.username} onChange={(event) => updateField('username', event.target.value)} required />
            </div>
            <div className="field">
              <div className="label">Clasa</div>
              <select
                className="select form-select"
                value={form.class_id}
                onChange={(event) => updateField('class_id', event.target.value)}
                disabled={loadingClasses || classes.length === 0}
                required={classes.length > 0}
              >
                {classes.map((schoolClass) => (
                  <option key={schoolClass.id} value={schoolClass.id}>
                    {schoolClass.name} - {schoolClass.profile}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="twoColFields">
            <div className="field">
              <div className="label">Parola</div>
              <input className="input form-control" type="password" value={form.password} onChange={(event) => updateField('password', event.target.value)} required />
            </div>
            <div className="field">
              <div className="label">Confirmare parola</div>
              <input className="input form-control" type="password" value={form.confirmPassword} onChange={(event) => updateField('confirmPassword', event.target.value)} required />
            </div>
          </div>

          <button className="btn btn-primary loginMainBtn" type="submit" disabled={!canSubmit}>
            {loading ? 'Se creeaza contul...' : 'Register'}
          </button>

          <div className="authSwitch">
            <span>Ai deja cont?</span>
            <Link className="linkBtn" to="/login">Inapoi la login</Link>
          </div>

          {banner && <div className={`banner ${banner.type}`}>{banner.text}</div>}
        </form>
      </div>
    </div>
  )
}