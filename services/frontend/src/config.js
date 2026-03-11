export const CONFIG = {
  keycloak: {
    url: 'http://localhost:8181',
    realm: 'timetable-realm',
    clientId: 'timetable-backend'
  },

  api: {
    baseUrl: 'http://localhost:8000/api'
  },

  auth: {
    storageKey: 'timetable_auth'
  },

  quickUsers: [
    { label: 'Sysadmin', username: 'sysadmin01', password: 'sysadmin01' },
    { label: 'Admin', username: 'admin01', password: 'admin01' },
    { label: 'Secretariat', username: 'secretariat01', password: 'secretariat01' },
    { label: 'Profesor', username: 'professor01', password: 'professor01' },
    { label: 'Student', username: 'student001', password: 'student001' },
    { label: 'Scheduler', username: 'scheduler01', password: 'scheduler01' },
  ]
};

