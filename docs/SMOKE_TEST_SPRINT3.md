# Smoke test del Sprint 3 en producción

US-25 Despliegue final (AB#56) · verificación de los Bloques A, B y C ya mergeados en `develop`.

| Dato | Valor |
| --- | --- |
| Fecha de la corrida | 2026-11-05, 20:27–20:29 hora Colombia (2026-11-06 01:27–01:29 UTC) |
| URL | `https://campored-backend.onrender.com` (Render, plan Free) |
| Base de datos | Supabase (PostgreSQL, Session Pooler) |
| Commit de `develop` probado | `41d0cf7` — Merge pull request #16 (incluye #13 y #14) |
| Sufijo de la corrida | `20261105202741` |
| Script | `scripts/smoke/smoke-sprint3.ps1` (Windows PowerShell 5.1) |
| Build local de ese commit | `./mvnw clean verify`: 356 tests, 0 fallos; cobertura JaCoCo 99,01 % de líneas, 94,30 % de ramas |

## Resultado

**API: 68 de 68 chequeos pasados. CORS: 4 de 4 pasados. Ningún ❌.**

- El despliegue sirve la versión nueva: `/v3/api-docs` expone `POST /api/pedidos`, `GET /api/pedidos/{id}`, `GET /api/pedidos/recibidos`, `POST /api/contactos`, `GET /api/contactos/mios` y `DELETE /api/ofertas/{id}`. El backend arrancó con `ddl-auto=validate`, así que las tablas `pedidos` y `registros_contacto` existen en Supabase y coinciden con las entidades.
- No hubo cold start: Render respondió desde la primera petición.
- La corrida cayó en la franja en que la fecha UTC (06-11) ya no coincide con la de Colombia (05-11). El pedido con fecha 05-11 se aceptó y el de 04-11 respondió `La fecha preferida no puede ser anterior a hoy.`. Eso confirma que en producción las fechas de negocio usan el `Clock` de America/Bogota (`TimeConfig`) y no la fecha UTC del servidor.

## Detalle por chequeo

| HU | Escenario | Esperado | Obtenido | Resultado |
| --- | --- | --- | --- | --- |
| US-25 | A1 `GET /v3/api-docs` expone los endpoints del Sprint 3 (arrancó con ddl-auto=validate) | 200 | 200 | ✅ |
| US-11 | A2 GET /api/ofertas responde PageResponse | 200 | 200 | ✅ |
| CORS | A3 preflight OPTIONS /api/contactos desde https://campored.rollerandres.dev | 2xx | 200 | ✅ |
| CORS | A3 preflight OPTIONS /api/pedidos desde https://campored.rollerandres.dev | 2xx | 200 | ✅ |
| CORS | A3 preflight OPTIONS /api/contactos desde https://campored-frontend.vercel.app | 2xx | 200 | ✅ |
| CORS | A3 preflight OPTIONS /api/pedidos desde https://campored-frontend.vercel.app | 2xx | 200 | ✅ |
| US-01 | B4 registro productor A (Sonsón) | 201 | 201 | ✅ |
| US-01 | B4 registro productor B (San Carlos) | 201 | 201 | ✅ |
| US-03 | B4 registro comprador A | 201 | 201 | ✅ |
| US-03 | B4 registro comprador B | 201 | 201 | ✅ |
| US-02 | B4 login ProductorA | 200 | 200 | ✅ |
| US-02 | B4 login ProductorB | 200 | 200 | ✅ |
| US-02 | B4 login CompradorA | 200 | 200 | ✅ |
| US-02 | B4 login CompradorB | 200 | 200 | ✅ |
| US-17 | B5 productor A habilita WhatsApp y llamada con números | 200 | 200 | ✅ |
| US-17 | B5 productor B apaga ambos canales | 200 | 200 | ✅ |
| US-06 | B6 crear O1 DISPONIBLE, RECOGIDA_FINCA, 250 KILOGRAMO | 201 | 201 | ✅ |
| US-06 | B6 crear O2 EN_CRECIMIENTO, AMBAS, cosecha hoy+10 | 201 | 201 | ✅ |
| US-06 | B6 crear O3 DISPONIBLE (para eliminar sin pedidos) | 201 | 201 | ✅ |
| US-06 | B6 crear O4 DISPONIBLE (para agotar) | 201 | 201 | ✅ |
| US-06 | B6 crear O5 DISPONIBLE (solo contactos) | 201 | 201 | ✅ |
| US-06 | B6 crear O6 DISPONIBLE del productor B | 201 | 201 | ✅ |
| US-14/15 | B7 detalle de O1 como comprador A trae whatsappContacto y telefonoContacto | 200 | 200 | ✅ |
| US-14/15 | B7 detalle de O1 sin token trae los números en null | 200 | 200 | ✅ |
| US-18 | Esc 1 pedido de 100 kg sobre O1, hoy, MANANA, sin modalidad | 201 | 201 | ✅ |
| US-18 | Esc 1 el pedido no descuenta inventario (O1 sigue en 250) | 200 | 200 | ✅ |
| US-18 | Esc 2 sin dirección de entrega | 400 | 400 | ✅ |
| US-18 | Esc 3 sin jornada | 400 | 400 | ✅ |
| US-18 | Esc 5 cantidad 300 kg mayor a la disponible (250) | 400 | 400 | ✅ |
| US-18 | Regla fecha preferida ayer (Colombia) | 400 | 400 | ✅ |
| US-18 | Regla O2 con fecha hoy+2, antes de la cosecha (hoy+10) | 400 | 400 | ✅ |
| US-18 | Regla O2 (AMBAS) sin modalidad | 400 | 400 | ✅ |
| US-18 | Regla O2 con modalidad AMBAS | 400 | 400 | ✅ |
| US-18 | Regla O1 (RECOGIDA_FINCA) con DESPACHO_PRODUCTOR | 400 | 400 | ✅ |
| US-18 | Regla varios errores de negocio juntos (cantidad, fecha, modalidad) sobre O2 | 400 | 400 | ✅ |
| US-18 | Regla varios errores de formato juntos (dirección y jornada) | 400 | 400 | ✅ |
| US-18 | Regla O2 con hoy+12 y DESPACHO_PRODUCTOR | 201 | 201 | ✅ |
| US-09 | Agotar O4 | 200 | 200 | ✅ |
| US-18 | Pedido sobre O4 agotada | 422 | 422 | ✅ |
| US-18 | Pedido sobre oferta inexistente | 404 | 404 | ✅ |
| US-18 | Productor A creando un pedido | 403 | 403 | ✅ |
| US-18 | Pedido sin token | 401 | 401 | ✅ |
| US-18 | GET P1 como comprador A (dueño) | 200 | 200 | ✅ |
| US-18 | GET P1 como productor A (dueño de la oferta) | 200 | 200 | ✅ |
| US-18 | GET P1 como comprador B (ajeno) | 403 | 403 | ✅ |
| US-18 | GET P1 como productor B (ajeno) | 403 | 403 | ✅ |
| US-18 | GET pedido inexistente | 404 | 404 | ✅ |
| US-14 | Esc 3 WhatsApp sobre O1 desde el pedido P1 | 201 | 201 | ✅ |
| US-14 | Esc 4 WhatsApp sobre O5 sin pedido | 201 | 201 | ✅ |
| US-15 | Esc 3 llamada sobre O1 | 201 | 201 | ✅ |
| US-14 | WhatsApp sobre O6 (productor B con canales apagados) | 422 | 422 | ✅ |
| US-19 | Comprador B usando el pedido P1 de comprador A | 422 | 422 | ✅ |
| US-19 | Contacto sobre O4 agotada se registra igual | 201 | 201 | ✅ |
| US-19 | Esc 2 historial del comprador A en orden descendente, sin números | 200 | 200 | ✅ |
| US-19 | Esc 3 comprador B sin contactos responde [] | 200 | 200 | ✅ |
| US-19 | Historial consultado por un productor | 403 | 403 | ✅ |
| US-19 | Historial sin token | 401 | 401 | ✅ |
| US-23 | Esc 1 productor A ve sus pedidos en orden descendente con datos del comprador | 200 | 200 | ✅ |
| US-23 | Esc 2/3 productor B no ve pedidos de A (aislamiento) | 200 | 200 | ✅ |
| US-23 | Pedidos recibidos consultados por un comprador | 403 | 403 | ✅ |
| US-10 | Esc 1 eliminar O3 sin pedidos ni contactos | 204 | 204 | ✅ |
| US-10 | Esc 1 O3 ya no tiene detalle | 404 | 404 | ✅ |
| US-10 | Esc 1 O3 no aparece en mis-ofertas | 200 | 200 | ✅ |
| US-10 | Esc 2 eliminar O1 con pedidos y contactos | 422 | 422 | ✅ |
| US-10 | Esc 3 eliminar O5 solo con contactos | 422 | 422 | ✅ |
| US-10 | Productor B eliminando O1 (ajena) | 403 | 403 | ✅ |
| US-10 | Comprador eliminando una oferta | 403 | 403 | ✅ |
| US-10 | Eliminar oferta inexistente | 404 | 404 | ✅ |
| US-17 | Esc 2 productor B habilita WhatsApp sin número guardado | 400 | 400 | ✅ |
| US-17 | Esc 2 productor B habilita llamada con teléfono vacío | 400 | 400 | ✅ |
| US-17 | Esc 3 ambos canales en false | 200 | 200 | ✅ |
| US-22 | Formato `ErrorResponse` en las 28 respuestas de error (sin 401) | 28 válidas | 28 válidas | ✅ |

Notas:

- La fila US-22 resume la revisión de las 28 respuestas 4xx de la corrida (los 401 aparte): todas traen `timestamp`, `status`, `error`, `mensaje` y `ruta`, el `status` del cuerpo coincide con el HTTP, y `errores` aparece solo en los 400 por campo.
- Los 401 llegan sin cuerpo, como dice el contrato.
- `GET /api/ofertas/{id}` de una oferta eliminada responde 404 con `La oferta no existe o no está disponible` (el mensaje del detalle público, US-12). El contrato de US-10 solo exige el 404.
- US-17 Esc 2 se probó por los dos caminos del backend: WhatsApp sin número guardado (`UsuarioService`) y teléfono vacío en la petición (`CanalesContactoValidator`). Después del 400, el teléfono guardado del productor B sigue intacto.

## CORS

Preflight `OPTIONS` con `Access-Control-Request-Method: POST` y `Access-Control-Request-Headers: authorization,content-type`:

| Origen | `/api/contactos` | `/api/pedidos` |
| --- | --- | --- |
| `https://campored.rollerandres.dev` | ✅ 200 | ✅ 200 |
| `https://campored-frontend.vercel.app` | ✅ 200 | ✅ 200 |

En los cuatro casos `Access-Control-Allow-Origin` es igual al origen, y `Access-Control-Allow-Headers` incluye `authorization` y `content-type`. El registro de contactos con `keepalive` del frontend puede enviar el preflight sin problema. `CORS_ALLOWED_ORIGINS` en Render ya incluye los dos dominios.

## Hora de las fechas

| Campo | Valor recibido | UTC de la petición | Colombia | Coincide con |
| --- | --- | --- | --- | --- |
| `fechaCreacion` (pedido P1) | `2026-11-06T01:28:07.616432` | 2026-11-06T01:28:07 | 2026-11-05T20:28:07 | UTC |
| `fechaContacto` (contacto C1) | `2026-11-06T01:28:22.267774` | 2026-11-06T01:28:21 | 2026-11-05T20:28:21 | UTC |
| `timestamp` de `ErrorResponse` | `2026-11-06T01:28:30.108422793` | 2026-11-06T01:28:30 | 2026-11-05T20:28:30 | UTC |

Las tres fechas salen en **hora UTC y sin offset** (`LocalDateTime`). El JVM de Render corre en UTC y `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`.

**Qué vería el frontend:** `new Date("2026-11-06T01:28:07.616432")` interpreta una cadena sin offset como hora **local del navegador**. Un pedido hecho a las 8:28 p. m. del 5 de noviembre se mostraría como «6 nov, 1:28 a. m.»: cinco horas adelante y, entre las 7 p. m. y la medianoche, con la fecha del día siguiente. Un «hace X minutos» saldría en el futuro. El contrato ya avisa que es hora UTC, pero el formato no lo dice.

**Corrección mínima propuesta para US-25** (no aplicada):

1. En `PedidoResponse` (`fechaCreacion`, `src/main/java/com/campored/backend/dto/PedidoResponse.java:27`) y `ContactoResponse` (`fechaContacto`, `ContactoResponse.java:19`), cambiar `LocalDateTime` por `OffsetDateTime`.
2. En `PedidoMapper.java:27` y `ContactoMapper.java:26`, convertir con `fecha.atZone(ZoneId.systemDefault()).toOffsetDateTime()`. Así sale `"2026-11-06T01:28:07.616432Z"` en Render, y sigue siendo correcto si se corre local con el JVM en hora Colombia.
3. Para que todo el API sea coherente, lo mismo en `OfertaResponse.java:44`, `OfertaDetalleResponse.java:37` (`OfertaMapper.java:43`, `:69`, `:92`) y en `ErrorResponse.timestamp` (`ErrorResponse.java:19`, `GlobalExceptionHandler.java:132`, con `OffsetDateTime.now()`).

Las entidades y el esquema no cambian: las columnas siguen siendo `TIMESTAMP` en UTC, y no hace falta script SQL. Hay que actualizar los tests que comparan la fecha y la nota de `docs/API_CONTRATO_SPRINT3.md`. Si se prefiere no tocar el backend, la alternativa en el frontend es parsear con `new Date(valor + "Z")` y formatear con `timeZone: "America/Bogota"`.

## Datos creados

Todos los usuarios tienen correo `smoke.<rol>.20261105202741@campored.test`: `productora`, `productorb`, `compradora` y `compradorb`. Las contraseñas se generaron en memoria y no se guardaron. Los teléfonos son ficticios (`3000000001` a `3000000007`).

| Tabla | Filas que quedan de esta corrida |
| --- | --- |
| `usuarios` | 4 |
| `fincas` | 2 |
| `negocios` | 2 |
| `ofertas` | 5 (O1, O2, O4, O5, O6; O3 se eliminó en US-10 Esc 1). Todas quedaron AGOTADAS al final, fuera del catálogo público |
| `pedidos` | 2 (P1 sobre O1 y P2 sobre O2) |
| `registros_contacto` | 4 (tres sobre O1/O5 y uno sobre O4 agotada) |

## Cómo repetirlo

1. Desde la raíz del repo, en Windows:

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\smoke-sprint3.ps1
   ```

   Parámetros opcionales: `-BaseUrl http://localhost:8080` para otro entorno, `-TablaMd <ruta>` para guardar la tabla de resultados y `-ConservarOfertasActivas` para no agotar las ofertas smoke al final.
2. Cada corrida usa un sufijo nuevo (fecha y hora de Colombia), así que no choca con corridas anteriores. Si un paso bloqueante falla (registro, login, creación de ofertas o del pedido P1), se detiene. Código de salida: 0 si todo pasa, 1 si falla algún chequeo, 2 si se detuvo.
3. En el SQL Editor de Supabase, ejecutar `scripts/smoke/verificar_esquema_sprint3.sql` (solo lectura: CHECK, FK sin `ON DELETE CASCADE`, índices, RLS y conteo de filas smoke).
4. Después, `scripts/smoke/limpiar_datos_smoke.sql` (borra solo lo que cuelga de `smoke.%@campored.test`; para probarlo antes, cambiar `COMMIT` por `ROLLBACK`).
