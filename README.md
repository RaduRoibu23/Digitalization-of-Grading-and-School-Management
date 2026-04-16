# Digitalization of Grading and School Management

## Setup

Pentru rularea solutiei sunt necesare Docker Desktop si Docker Compose.

Configuratia locala este pastrata in fisierul `infra/.env`. Inainte de pornire se verifica valorile din acest fisier, in special cele pentru baza de date, Keycloak si SMTP.

## Rularea solutiei cu Docker

Din radacina proiectului se ruleaza comanda:

```bash
docker compose --env-file infra/.env -f infra/docker-compose.local.yml up -d --build
```

Prin aceasta comanda sunt pornite toate serviciile necesare:

- frontend-ul
- backend-ul
- baza de date a aplicatiei
- baza de date pentru Keycloak
- serverul Keycloak

Dupa pornire, aplicatia este disponibila la urmatoarele adrese:

- Frontend: `http://localhost:3000`
- Backend API: `http://localhost:8000/api`
- Swagger UI: `http://localhost:8000/swagger-ui.html`
- Keycloak: `http://localhost:8181`

Pentru oprirea serviciilor se foloseste comanda:

```bash
docker compose --env-file infra/.env -f infra/docker-compose.local.yml down
```

## Rularea componentelor local

Daca se doreste rularea componentelor in mod separat, bazele de date si Keycloak pot fi pornite mai intai din Docker:

```bash
docker compose --env-file infra/.env -f infra/docker-compose.local.yml up -d postgres-app postgres-keycloak keycloak
```

Backend-ul se porneste din folderul `backend` cu:

```bash
mvn spring-boot:run
```

Frontend-ul se porneste din folderul `frontend` cu:

```bash
npm install
npm run dev
```

In aceasta varianta, backend-ul ruleaza pe portul `8000`, iar frontend-ul pe portul implicit al Vite, de regula `5173`, daca nu este configurat altfel.
