import { useMemo, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { login } from '../services/authService'
import { CONFIG } from '../config'

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
      <div className="loginCard">
        <div className="title">Login</div>
        <div className="subtitle">Digitalization of Grading and School Management</div>

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
            <span>Nu ai cont?</span>
            <Link className="linkBtn" to="/register">Creeaza cont</Link>
          </div>

          {successMessage && <div className="banner ok">{successMessage}</div>}
          {error && <div className="alert">{error}</div>}
        </form>
      </div>

      <div className="loginFooter">
        {CONFIG.keycloak.url} | {CONFIG.keycloak.realm} | client: timetable-backend
      </div>
    </div>
  )
}
