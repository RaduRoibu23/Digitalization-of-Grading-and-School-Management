# API

Endpoint-urile principale raman sub prefixul `/api`.

- `POST /api/login` si `POST /api/refresh` gestioneaza schimbul de token-uri cu Keycloak.
- `GET /api/me` intoarce profilul utilizatorului autentificat si rolurile aplicatiei.
- `GET /api/timetables/**` si `POST /api/timetables/generate` acopera citirea si generarea orarului.
- `GET /api/catalog/**`, `POST /api/catalog/grades` si `POST /api/catalog/absences` acopera catalogul.
- `GET /api/documents/requests`, `POST /api/documents/requests` si rutele de aprobare/descarcare acopera documentele.
- `GET /api/notifications/**`, `GET /api/audit-logs` si `GET /api/feedback/**` acopera notificarile, auditul si feedback-ul.

Documentatia OpenAPI ramane disponibila la `/swagger-ui.html`.
