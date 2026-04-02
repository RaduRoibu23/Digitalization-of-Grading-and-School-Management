# Architecture

Platforma este structurata ca monorepo cu separare clara intre aplicatii si infrastructura:

- `frontend/` gazduieste SPA-ul React/Vite si consuma API-ul backend-ului prin servicii centralizate.
- `backend/` expune API-ul REST Spring Boot si este organizat pe module functionale (`auth`, `catalog`, `documents`, `timetable`, etc.).
- `infra/` pastreaza stack-ul local de dezvoltare: PostgreSQL pentru aplicatie, PostgreSQL pentru Keycloak, Keycloak si Nginx.

Fluxul de autentificare ramane bazat pe Keycloak. Frontend-ul obtine token-uri prin backend, backend-ul valideaza JWT-urile ca resource server si foloseste in continuare integrarea Keycloak pentru login, refresh si provisioning de conturi.
