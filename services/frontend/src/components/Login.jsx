import { useMemo, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { login } from '../services/authService'
import { CONFIG } from '../config'
import AuthShowcase from './AuthShowcase'

export default function Login({ onLogin }) {
  const location = useLocation()
  const successMessage = useMemo(() => {
    if (!location.state?.registered) {
      return ''
    }
    return location.state.username ? `Contul ${location.state.username} a fost creat. Te poti autentifica acum.` : 'Contul a fost creat. Te poti autentifica acum.'
  }, [location.state])

  const [username, setUsername] = useState(location.state?.username || '')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const showcaseCards = [
    { value: 'Acces rapid', title: 'Autentificare clara', text: 'Intri direct in platforma si ajungi la modulele potrivite rolului tau.' },
    { value: 'Date reale', title: 'Informatii persistente', text: 'Orarul, catalogul si notificarile raman disponibile intre sesiuni.' },
    { value: 'Flux unitar', title: 'Lucru organizat', text: 'Elevii, profesorii si administratia folosesc acelasi spatiu de lucru.' },
    { value: 'Control', title: 'Acces pe roluri', text: 'Fiecare utilizator vede doar zonele relevante pentru activitatea sa.' },
  ]

  const doLogin = async (nextUsername, nextPassword) => {
    setError('')
    setLoading(true)
    try {
      const tokens = await login(nextUsername, nextPassword)
      onLogin(tokens)
    } catch (err) {
      setError(err.message || 'Login failed')
    } finally {
      setLoading(false)
    }
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    await doLogin(username, password)
  }

  const loginPreset = async (user) => {
    setUsername(user.username)
    setPassword(user.password)
    await doLogin(user.username, user.password)
  }

  return (
    <div className="loginPage">
      <div className="authLayout authLayoutCompact">
        <AuthShowcase
          eyebrow="Acces in platforma"
          title="Autentificare pentru conturile existente"
          description="Introdu datele contului pentru a ajunge in zona ta de lucru, cu acces la orar, catalog si notificarile relevante."
          badges={['Orar', 'Catalog', 'Notificari']}
          cards={showcaseCards}
          variant="compact"
        />

        <div className="loginCard authCard">
          <div className="authCardHeader">
            <div className="title">Autentificare</div>
            <div className="subtitle">Digitalization of Grading and School Management</div>
          </div>

          <form onSubmit={handleSubmit}>
            <div className="field">
              <div className="label">Username</div>
              <input
                className="input"
                id="username"
                name="username"
                autoComplete="off"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                placeholder="user"
                required
              />
            </div>

            <div className="field">
              <div className="label">Password</div>
              <input
                className="input"
                id="password"
                name="password"
                autoComplete="off"
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="parola"
                required
              />
            </div>

            <button className="btn btn-primary loginMainBtn" type="submit" disabled={loading}>
              {loading ? 'Se conecteaza...' : 'Login'}
            </button>

            <div className="quickLoginGrid">
              {CONFIG.quickUsers.map((user) => (
                <button
                  key={user.label}
                  className="btn btnSmall"
                  type="button"
                  onClick={() => loginPreset(user)}
                  disabled={loading}
                  title={`Login: ${user.username}`}
                >
                  {user.label}
                </button>
              ))}
            </div>

            <div className="authSwitch">
              <Link className="linkBtn" to="/">Prima pagina</Link>
              <span>/</span>
              <Link className="linkBtn" to="/register">Creeaza cont</Link>
            </div>

            {successMessage && <div className="banner ok">{successMessage}</div>}
            {error && <div className="alert">{error}</div>}
          </form>
        </div>
      </div>
    </div>
  )
}
