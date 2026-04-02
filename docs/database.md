# Database

Aplicatia foloseste doua baze PostgreSQL separate in dezvoltarea locala:

- `postgres-app`: baza principala a aplicatiei, folosita de backend si migrata cu Flyway.
- `postgres-keycloak`: baza dedicata Keycloak-ului.

In baza aplicatiei se regasesc tabele pentru:

- date de referinta (`school_classes`, `subjects`, `rooms`, `user_profiles`);
- orar (`timetable_entries`);
- catalog (`student_grades`, `student_absences`);
- notificari, audit, feedback si cereri de documente.

Migrarile existente din `backend/src/main/resources/db/migration/` au fost pastrate integral.
