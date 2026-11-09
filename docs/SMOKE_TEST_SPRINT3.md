# Smoke test del Sprint 3 en producción

US-25 Despliegue final (AB#56) · corrida vigente antes del release v1.0.0, con los Bloques A, B y C y los ajustes de fechas (#17) ya mergeados en `develop`.

| Dato | Valor |
| --- | --- |
| Fecha de la corrida | 2026-11-08, 21:02–21:05 hora Colombia (2026-11-09 02:02–02:05 UTC) |
| URL | `https://campored-backend.onrender.com` (Render, plan Free) |
| Base de datos | Supabase (PostgreSQL, Session Pooler) |
| Commit de `develop` probado | `4e63a82` — Merge pull request #17 (sobre #13, #14 y #16) |
| Sufijo de la corrida | `20261108210257` |
| Script | `scripts/smoke/smoke-sprint3.ps1` (Windows PowerShell 5.1), ya con el reintento ante 502, 503 o 504; los chequeos son los mismos |
| Build local | `./mvnw clean verify` de `feat/US-25-cierre-backend` (`4e63a82` más las anotaciones de Swagger y su test): 367 tests, 0 fallos; cobertura JaCoCo 99,01 % de líneas, 95,22 % de ramas |

## Resultado

**API: 73 de 73 chequeos pasados. CORS: 4 de 4 pasados. Ningún ❌ y ningún reintento.**

- El despliegue sirve la versión de `develop`: `/v3/api-docs` expone `POST /api/pedidos`, `GET /api/pedidos/{id}`, `GET /api/pedidos/recibidos`, `POST /api/contactos`, `GET /api/contactos/mios` y `DELETE /api/ofertas/{id}`. El backend arrancó con `ddl-auto=validate`, así que el esquema de Supabase coincide con las entidades.
- Render estaba dormido: el script esperó el arranque (cerca de un minuto y medio) y después ninguna petición respondió 502, 503 ni 504, así que el reintento nuevo no llegó a activarse.
- Las cinco fechas con hora revisadas llegan con el offset `-05:00` y a menos de 5 s de la hora de Colombia de la petición (ver [Fechas con el offset de Colombia](#fechas-con-el-offset-de-colombia)).

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
| US-22 | I fechaCreacion del pedido P1 con offset -05:00 y hora de la corrida | 201 | 201 | ✅ |
| US-22 | I fechaContacto del contacto C1 con offset -05:00 y hora de la corrida | 201 | 201 | ✅ |
| US-22 | I timestamp de ErrorResponse (400 sin dirección) con offset -05:00 y hora de la corrida | 400 | 400 | ✅ |
| US-22 | I fechaCreacion de la oferta O1 con offset -05:00 y hora de la corrida | 201 | 201 | ✅ |
| US-22 | I usuario.fechaRegistro del login del comprador A con offset -05:00 y hora de la corrida | 200 | 200 | ✅ |

Notas:

- La fila US-22 de formato resume la revisión de las 28 respuestas 4xx de la corrida (los 401 aparte): todas traen `timestamp`, `status`, `error`, `mensaje` y `ruta`, el `status` del cuerpo coincide con el HTTP, y `errores` aparece solo en los 400 por campo.
- Los 401 llegan sin cuerpo, como dice el contrato.
- `GET /api/ofertas/{id}` de una oferta eliminada responde 404 con `La oferta no existe o no está disponible` (el mensaje del detalle público, US-12). El contrato de US-10 solo exige el 404.
- US-17 Esc 2 se prueba por los dos caminos del backend: WhatsApp sin número guardado (`UsuarioService`) y teléfono vacío en la petición (`CanalesContactoValidator`). Después del 400, el teléfono guardado del productor B sigue intacto.
- Si una petición recibe 502, 503 o 504, el script espera 5 s y la repite una vez; el chequeo evalúa la respuesta del reintento y la fila queda marcada con «⚠ reintento tras <código>» en la consola y en la tabla. En esta corrida no hubo ninguna.

## CORS

Preflight `OPTIONS` con `Access-Control-Request-Method: POST` y `Access-Control-Request-Headers: authorization,content-type`:

| Origen | `/api/contactos` | `/api/pedidos` |
| --- | --- | --- |
| `https://campored.rollerandres.dev` | ✅ 200 | ✅ 200 |
| `https://campored-frontend.vercel.app` | ✅ 200 | ✅ 200 |

En los cuatro casos `Access-Control-Allow-Origin` es igual al origen, y `Access-Control-Allow-Headers` incluye `authorization` y `content-type`. `CORS_ALLOWED_ORIGINS` en Render incluye los dos dominios. Si el dominio final del frontend cambia antes del release, hay que agregarlo y repetir este chequeo (ver `docs/RELEASE_v1.0.0.md`).

## Fechas con el offset de Colombia

| Campo | Valor recibido | Hora Colombia de la petición |
| --- | --- | --- |
| `fechaCreacion` (pedido P1) | `2026-11-08T21:04:37.896802-05:00` | 2026-11-08T21:04:38 |
| `fechaContacto` (contacto C1) | `2026-11-08T21:04:47.394079-05:00` | 2026-11-08T21:04:48 |
| `timestamp` de `ErrorResponse` (400 sin dirección) | `2026-11-08T21:04:38.594101897-05:00` | 2026-11-08T21:04:39 |
| `fechaCreacion` (oferta O1) | `2026-11-08T21:04:35.010904-05:00` | 2026-11-08T21:04:36 |
| `usuario.fechaRegistro` (login del comprador A) | `2026-11-08T21:04:28.791911-05:00` | 2026-11-08T21:04:33 |

Todas traen offset explícito y coinciden con la hora de Colombia de la petición; `fechaRegistro` es unos segundos anterior porque el usuario se registró antes del login. El frontend puede usar `new Date(valor)` sin concatenar `Z`.

## Antecedentes

- **Corrida `20261105202741`** (2026-11-05, 20:27–20:29 hora Colombia, commit `41d0cf7` — PR #16 sobre #13 y #14): primera corrida en producción, 68 de 68 chequeos de API y 4 de 4 de CORS. Confirmó que la fecha preferida usa el `Clock` de America/Bogota, pero mostró que `fechaCreacion`, `fechaContacto` y `timestamp` salían en hora UTC sin offset. La corrección se aplicó en el PR #17 (US-22, `FechaRespuesta.aColombia`) y desde entonces el smoke la verifica en la sección I.
- **Corrida `20261105211742`** (2026-11-05, 21:17 hora Colombia): un 502 pasajero de Render hizo fallar un chequeo que no tenía relación con el código. Es el antecedente del reintento único ante 502, 503 o 504 que ahora hace `Invoke-Api`.

## Datos creados

Todos los usuarios tienen correo `smoke.<rol>.20261108210257@campored.test`: `productora`, `productorb`, `compradora` y `compradorb`. Las contraseñas se generaron en memoria y no se guardaron. Los teléfonos son ficticios (`3000000001` a `3000000007`).

| Tabla | Filas que quedan de esta corrida |
| --- | --- |
| `usuarios` | 4 |
| `fincas` | 2 |
| `negocios` | 2 |
| `ofertas` | 5 (O1, O2, O4, O5, O6; O3 se eliminó en US-10 Esc 1). Todas quedaron AGOTADAS al final, fuera del catálogo público |
| `pedidos` | 2 (P1 sobre O1 y P2 sobre O2) |
| `registros_contacto` | 4 (tres sobre O1/O5 y uno sobre O4 agotada) |

Se borran con `scripts/smoke/limpiar_datos_smoke.sql`, que también quita lo que quede de corridas anteriores y no toca los datos demo (`@campored.demo`).

## Cómo repetirlo

1. Desde la raíz del repo, en Windows:

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\smoke-sprint3.ps1
   ```

   Parámetros opcionales: `-BaseUrl http://localhost:8080` para otro entorno, `-TablaMd <ruta>` para guardar la tabla de resultados y `-ConservarOfertasActivas` para no agotar las ofertas smoke al final.
2. Cada corrida usa un sufijo nuevo (fecha y hora de Colombia), así que no choca con corridas anteriores. Si un paso bloqueante falla (registro, login, creación de ofertas o del pedido P1), se detiene. Código de salida: 0 si todo pasa, 1 si falla algún chequeo, 2 si se detuvo.
3. En el SQL Editor de Supabase, ejecutar `scripts/smoke/verificar_esquema_sprint3.sql` (solo lectura: CHECK, FK sin `ON DELETE CASCADE`, índices, RLS y conteo de filas smoke).
4. Después, `scripts/smoke/limpiar_datos_smoke.sql` (borra solo lo que cuelga de `smoke.%@campored.test`; para probarlo antes, cambiar `COMMIT` por `ROLLBACK`).
