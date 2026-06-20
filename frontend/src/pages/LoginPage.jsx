import { useState } from 'react'
import { Link } from 'react-router-dom'
import { CONFIG } from '../config/config'
import { login } from '../services/authService'
import AuthIntroPanel from './AuthIntroPanel'
import ThemeToggle from '../components/ui/ThemeToggle'

export default function LoginPage({ onLogin }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const overviewCards = [
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

  const applyPreset = async (user) => {
    const presetPassword = user.password || user.username
    setUsername(user.username)
    setPassword(presetPassword)
    setError('')
    await doLogin(user.username, presetPassword)
  }

  return (
    <div className="loginPage">
      <div className="authLayout authLayoutCompact">
        <AuthIntroPanel
          eyebrow="Acces in platforma"
          title="Autentificare pentru conturile existente"
          description="Introdu datele contului pentru a ajunge in zona ta de lucru, cu acces la orar, catalog si notificarile relevante."
          badges={['Orar', 'Catalog', 'Notificari']}
          cards={overviewCards}
          variant="compact"
        />

        <div className="loginCard authCard anim-fade-up">
          <div className="authCardHeader">
            <div>
              <div className="title">Autentificare</div>
              <div className="subtitle">Digitalization of Grading and School Management</div>
            </div>
            <ThemeToggle />
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

            <div className="presetAccountGrid">
              {CONFIG.presetAccounts.map((user) => (
                <button
                  key={user.label}
                  className="btn btnSmall"
                  type="button"
                  onClick={() => void applyPreset(user)}
                  disabled={loading}
                  title={`Autentificare rapida pentru ${user.username}`}
                >
                  {user.label}
                </button>
              ))}
            </div>

            <div className="authSwitch">
              <Link className="linkBtn" to="/">Prima pagina</Link>
            </div>

            {error && <div className="alert">{error}</div>}
          </form>
        </div>
      </div>
    </div>
  )
}
