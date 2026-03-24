export const CONFIG = {
  keycloak: {
    url: 'http://localhost:8181',
    realm: 'timetable-realm',
    clientId: 'timetable-backend',
  },

  api: {
    baseUrl: 'http://localhost:8000/api',
  },

  auth: {
    storageKey: 'timetable_auth',
  },

  quickUsers: [
    { label: 'Sysadmin', username: 'sysadmin01' },
    { label: 'Admin', username: 'admin01' },
    { label: 'Secretariat', username: 'secretariat01' },
    { label: 'Profesor', username: 'romana01' },
    { label: 'Student', username: 'student001' },
    { label: 'Scheduler', username: 'scheduler01' },
  ],
}
