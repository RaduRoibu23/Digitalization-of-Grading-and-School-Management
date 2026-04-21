const env = import.meta.env

export const CONFIG = {
  keycloak: {
    url: env.VITE_KEYCLOAK_URL || 'http://localhost:8181',
    realm: env.VITE_KEYCLOAK_REALM || 'timetable-realm',
    clientId: env.VITE_KEYCLOAK_CLIENT_ID || 'timetable-backend',
  },

  api: {
    baseUrl: (env.VITE_API_BASE_URL || 'http://localhost:8000/api').replace(/\/$/, ''),
  },

  auth: {
    storageKey: env.VITE_AUTH_STORAGE_KEY || 'timetable_auth',
  },

  presetAccounts: [
    { label: 'Sysadmin', username: 'sysadmin01', password: 'sysadmin01' },
    { label: 'Director', username: 'admin01', password: 'admin01' },
    { label: 'Secretariat', username: 'secretariat01', password: 'secretariat01' },
    { label: 'Profesor', username: 'romana01', password: 'romana01' },
    { label: 'Student', username: 'student001', password: 'student001' },
    { label: 'Parinte', username: 'parinte001', password: 'parinte001' },
    { label: 'Scheduler', username: 'scheduler01', password: 'scheduler01' },
  ],
}
