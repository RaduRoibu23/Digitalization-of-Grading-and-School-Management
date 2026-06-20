import { useTheme } from '../../hooks/useTheme'

export default function ThemeToggle({ className = '' }) {
  const { theme, toggleTheme } = useTheme()
  const isLight = theme === 'light'

  return (
    <button
      type="button"
      className={`themeToggle ${className}`.trim()}
      onClick={toggleTheme}
      role="switch"
      aria-checked={isLight}
      aria-label={isLight ? 'Comuta pe tema intunecata' : 'Comuta pe tema luminoasa'}
      title={isLight ? 'Treci pe tema intunecata' : 'Treci pe tema luminoasa'}
    >
      <span className="themeToggleTrack" aria-hidden="true">
        <span className="themeToggleThumb">
          <svg className="themeToggleIcon themeToggleSun" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="4" />
            <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41" />
          </svg>
          <svg className="themeToggleIcon themeToggleMoon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
          </svg>
        </span>
      </span>
    </button>
  )
}
