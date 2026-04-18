# Digitalization of Grading and School Management

## Setup

Pentru rularea solutiei sunt necesare Docker Desktop si Docker Compose.

Configuratia locala este pastrata in fisierul `infra/.env`. Inainte de pornire se verifica valorile din acest fisier, in special cele pentru baza de date, Keycloak si SMTP.

## Logica principala implementata

Aplicatia gestioneaza urmatoarele roluri principale:

- `student`
- `parent`
- `professor`
- `secretariat`
- `scheduler`
- `admin`
- `sysadmin`

### Rolul `parent`

Rolul `parent` este legat 1:1 de un elev, dupa conventia `parinteNNN -> studentNNN`.

Parintele poate:

- sa vada catalogul copilului
- sa vada absentele copilului
- sa vada orarul copilului, afisat in interfata ca `Orarul elevului`
- sa vada si sa primeasca notificarile academice relevante pentru copil
- sa foloseasca modulul de documente in contextul copilului asociat
- sa motiveze absentele copilului, cu motiv obligatoriu

In profilul parintelui:

- clasa afisata este clasa copilului asociat
- campurile `CNP`, `Serie`, `Numar serie` si `Initiala tatalui` nu se afiseaza

### Generarea conturilor demo

Pentru setul demo:

- elevii sunt generati ca `student001 ... student200`
- parintii sunt generati ca `parinte001 ... parinte200`
- fiecare pereche `studentNNN` si `parinteNNN` are acelasi nume de familie
- numele si prenumele parintilor sunt generate diferit, nu repetitiv
- pentru conturile demo, parola este aceeasi cu username-ul

In ecranul de login, butoanele rapide de sub formular pornesc autentificarea direct cu aceste credenziale presetate.

### Catalog si comentarii la note

Profesorul poate adauga optional un comentariu la nota. Comentariul este vizibil doar pentru:

- profesorul care a acordat nota
- elevul care a primit nota
- parintele elevului
- `secretariat`
- `sysadmin`

Comentariul nu este expus ca text liber in notificarile automate.

### Situatia scolara

La generarea situatiei scolare se include si `media totala`, calculata ca media aritmetica a tuturor materiilor care au deja medie.

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

## Conturi demo utile

Exemple rapide pentru testare:

- `sysadmin01 / sysadmin01`
- `secretariat01 / secretariat01`
- `romana01 / romana01`
- `student001 / student001`
- `parinte001 / parinte001`
- `scheduler01 / scheduler01`
