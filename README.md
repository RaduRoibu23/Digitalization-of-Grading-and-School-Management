# Digitalization of Grading and School Management

Monorepo pentru o platforma scolara care reuneste catalogul digital, generarea orarului, notificarile si administrarea conturilor. Arhitectura actuala pastreaza frontend-ul React, backend-ul Spring Boot, PostgreSQL pentru persistenta aplicatiei si Keycloak ca serviciu activ de identitate si autorizare.

## Structura

```text
.
|-- frontend/
|-- backend/
|-- infra/
|-- docs/
|-- scripts/
|-- .github/workflows/
|-- render.yaml
|-- README.md
`-- LICENSE
```

## Componente

- `frontend/`: aplicatia React/Vite, reorganizata in `src/app`, `src/pages`, `src/components`, `src/services`, `src/styles` si `src/config`.
- `backend/`: aplicatia Spring Boot, reorganizata pe module functionale: `auth`, `audit`, `catalog`, `documents`, `feedback`, `notifications`, `reference`, `timetable`, plus `common`.
- `infra/`: infrastructura pentru dezvoltare locala, inclusiv `docker-compose.local.yml`, fisierul `infra/.env`, configurarea Nginx si importul realm-ului Keycloak.
- `docs/`: note scurte despre arhitectura, API si baza de date.
- `scripts/`: comenzi rapide pentru pornirea componentelor locale.

## Pornire locala

1. Completeaza valorile din `infra/.env`. Pentru testare locala, aplicatia foloseste Mailtrap Sandbox, deci ai nevoie de credentialele SMTP din sandbox-ul Mailtrap.
2. Porneste intregul stack local:

```bash
docker compose -f infra/docker-compose.local.yml up --build
```

Sau foloseste scripturile helper:

```bash
./scripts/run-keycloak.sh
./scripts/run-backend.sh
./scripts/run-frontend.sh
```

## Endpoint-uri locale

- Frontend: `http://localhost:3000`
- Backend API: `http://localhost:8000/api`
- Swagger UI: `http://localhost:8000/swagger-ui.html`
- Keycloak: `http://localhost:8181`

## Observatii importante

- Keycloak nu este componenta legacy si ramane parte activa din fluxul de autentificare.
- Backend-ul continua sa foloseasca Flyway pentru migrari si PostgreSQL pentru datele aplicatiei.
- Fluxurile publice de register si feedback extern nu mai fac parte din aplicatie; conturile se creeaza doar prin fluxurile interne, iar formularul `Help` ramane disponibil doar utilizatorilor autentificati.
- Notificarile existente din aplicatie continua sa fie salvate in platforma si sunt oglindite si pe email in Mailtrap Sandbox atunci cand SMTP-ul este configurat.
- Realm-ul Keycloak este importat din `infra/keycloak/realms/timetable-realm-realm.json`.
- Nginx ramane folosit pentru servirea build-ului frontend in containerul local.

## CI si deploy

- `.github/workflows/frontend-ci.yml` construieste frontend-ul cu Node.js.
- `.github/workflows/backend-ci.yml` construieste backend-ul cu Maven si Java 17.
- `render.yaml` ofera un punct de plecare pentru publicarea backend-ului pe Render.

Documentatia suplimentara se afla in `docs/architecture.md`, `docs/api.md` si `docs/database.md`.
