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
- `director`
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
- `director`
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

- Frontend: `http://localhost`
- Frontend din telefon, in aceeasi retea Wi-Fi: `http://<ip-laptop>`
- Backend API prin frontend/proxy: `http://localhost/api`
- Backend API direct: `http://localhost:8000/api`
- Swagger UI: `http://localhost:8000/swagger-ui.html`
- Keycloak: `http://localhost:8181`

Frontend-ul servit prin Docker foloseste aceeasi origine pentru API. Cererile la `/api` sunt redirectionate de Nginx catre backend, astfel incat aplicatia poate fi accesata si din browserul telefonului fara URL-uri hardcodate pe `localhost`.

In configuratia Docker curenta, frontend-ul este publicat pe portul standard HTTP `80`, astfel incat nu mai este necesar sufixul `:3000` in link.

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

In aceasta varianta, backend-ul ruleaza pe portul `8000`, iar frontend-ul pe `3000`. Serverul Vite expune aplicatia pe reteaua locala si face proxy pentru `/api` catre `http://localhost:8000`, astfel incat poti testa si de pe telefon la `http://<ip-laptop>:3000`.

### Note pentru accesul din telefon

- Telefonul si laptopul trebuie sa fie conectate la aceeasi retea Wi-Fi.
- Adresa de test pentru varianta Docker este `http://<ip-laptop>`, unde `<ip-laptop>` este IPv4-ul laptopului din reteaua locala.
- Daca pagina nu se deschide de pe telefon, verifica mai intai firewall-ul Windows si faptul ca portul `80` este accesibil in reteaua locala.
- Valorile `VITE_*` nu mai sunt citite din `infra/.env`. Daca vrei override-uri explicite pentru frontend, foloseste variabile de mediu dedicate pentru Vite, de exemplu intr-un fisier `frontend/.env.local`.
- Backend-ul accepta implicit origin-uri locale de dezvoltare pentru `localhost`, `127.0.0.1` si IP-uri din retele private uzuale (`192.168.x.x`, `10.x.x.x`, `172.16.x.x` - `172.31.x.x`). Daca folosesti alt tip de retea, extinde `APP_SECURITY_ALLOWED_ORIGIN_PATTERNS`.

## Conturi demo utile

Exemple rapide pentru testare:

- `sysadmin01 / sysadmin01`
- `admin01 / admin01`
- `secretariat01 / secretariat01`
- `romana01 / romana01`
- `student001 / student001`
- `parinte001 / parinte001`
- `scheduler01 / scheduler01`
