# API de Convocatorias Favoritas

## Resumen

Se añade persistencia de favoritas por usuario autenticado en `user_favorite_convocatorias`.

- Aislamiento por usuario (`user_id` desde JWT)
- Upsert idempotente por `(user_id, convocatoria_id)`
- Estado de solicitud: `no_solicitada | solicitada`
- Import masivo para migrar desde `localStorage`

## Endpoints

Base path: `/api/usuario/favoritas`

### Listar

`GET /api/usuario/favoritas?estadoSolicitud=no_solicitada&q=tecnologia&page=0&size=20`

### Obtener por convocatoria

`GET /api/usuario/favoritas/{convocatoriaId}`

### Crear o actualizar (upsert)

`POST /api/usuario/favoritas`

```json
{
  "convocatoriaId": 123,
  "titulo": "Ayudas para digitalizacion",
  "organismo": "Ministerio X",
  "ubicacion": "Madrid",
  "tipo": "Subvencion",
  "sector": "Tecnologia",
  "fechaPublicacion": "2026-04-21",
  "fechaCierre": "2026-06-30",
  "presupuesto": "1.234.567,89 €",
  "abierto": true,
  "urlOficial": null,
  "idBdns": "9988",
  "numeroConvocatoria": "2026-ABC"
}
```

### Eliminar

`DELETE /api/usuario/favoritas/{convocatoriaId}`

### Cambiar estado

`PATCH /api/usuario/favoritas/{convocatoriaId}/estado`

```json
{ "estadoSolicitud": "solicitada" }
```

### Import masivo

`POST /api/usuario/favoritas/import`

```json
{
  "favoritas": [
    {
      "id": 123,
      "titulo": "Ayudas para digitalizacion",
      "organismo": "Ministerio X",
      "estadoSolicitud": "no_solicitada",
      "guardadaEn": "2026-04-21T10:00:00Z"
    }
  ]
}
```

## Ejemplos curl

```bash
curl -X GET "http://localhost:8080/api/usuario/favoritas?page=0&size=20" \
  -H "Authorization: Bearer <TOKEN>"

curl -X POST "http://localhost:8080/api/usuario/favoritas" \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"convocatoriaId":123,"titulo":"Ayudas 2026"}'

curl -X PATCH "http://localhost:8080/api/usuario/favoritas/123/estado" \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"estadoSolicitud":"solicitada"}'

curl -X DELETE "http://localhost:8080/api/usuario/favoritas/123" \
  -H "Authorization: Bearer <TOKEN>"
```

## Decisiones técnicas y trade-offs

- Se guarda snapshot de campos de convocatoria para render rapido y resiliencia si cambia la fuente externa.
- `POST` es idempotente: no duplica por `UNIQUE (user_id, convocatoria_id)`.
- `urlOficial` usa fallback basado en `idBdns` cuando no llega URL.
- El endpoint de import permite migracion progresiva desde frontend sin downtime.

