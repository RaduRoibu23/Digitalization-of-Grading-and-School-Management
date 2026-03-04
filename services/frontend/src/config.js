export const CONFIG = {
  keycloak: {
    url: 'http://localhost:8181',
    realm: 'timetable-realm',
    // Use timetable-backend for password grant (same client as API); timetable-frontend can be used if configured in Keycloak
    clientId: 'timetable-backend'
  },
  api: {
    baseUrl: 'http://localhost:8000'
  },
  auth: {
    storageKey: 'timetable_auth'
  },

  // Quick login presets (aligned with seed_keycloak.sh)
  // Format: sysadmin01, admin/professor/secretariat/scheduler (2 digits), student (2 digits)
  demoUsers: [
    { label: 'Sysadmin', username: 'sysadmin01', password: 'sysadmin01' },
    { label: 'Admin', username: 'admin01', password: 'admin01' },
    { label: 'Secretariat', username: 'secretariat01', password: 'secretariat01' },
    { label: 'Profesor', username: 'professor01', password: 'professor01' },
    { label: 'Student', username: 'student01', password: 'student01' },
    { label: 'Scheduler', username: 'scheduler01', password: 'scheduler01' },

  ]
};
