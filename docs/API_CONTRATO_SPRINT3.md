# Contrato API — Sprint 3

Base URL: `https://campored-backend.onrender.com` · Swagger: `/swagger-ui.html`

Si este documento y Swagger no coinciden, manda Swagger y se corrige el documento.

Convenciones generales (iguales al Sprint 2):

- Token en `Authorization: Bearer <token>`.
- **401** llega **sin cuerpo** (token ausente, inválido o expirado).
- Los demás errores usan `ErrorResponse`: `{ timestamp, status, error, mensaje, ruta, errores? }`. `errores` es un mapa `campo → mensaje` y solo aparece cuando hay errores por campo.
- Fechas `LocalDate` como `"2026-11-20"`. Montos y cantidades como número decimal (`420000.00`).

---

## Bloque A — US-18 Checkout y Pedido (AB#49) + US-10 Eliminar oferta (AB#38)

### Resumen de endpoints

| Método | Ruta | Rol | Éxito |
| --- | --- | --- | --- |
| POST | `/api/pedidos` | COMPRADOR | 201 + `Location: /api/pedidos/{id}` + `PedidoResponse` |
| GET | `/api/pedidos/{id}` | COMPRADOR dueño o PRODUCTOR de la oferta | 200 + `PedidoResponse` |
| DELETE | `/api/ofertas/{id}` | PRODUCTOR dueño | 204 sin cuerpo |

### Precarga del checkout

No hay endpoint nuevo para precargar el formulario. Usa el objeto `usuario` que llega en la respuesta del login (`POST /api/auth/login`) o del registro del comprador:

| Campo del checkout | Origen en `usuario` |
| --- | --- |
| `direccionEntrega` | `usuario.direccion` |
| `municipioEntrega` | `usuario.municipio` (código del enum, p. ej. `RIONEGRO`) |
| `notasAcceso` | `usuario.notasAcceso` |

El comprador puede cambiarlos antes de confirmar. El pedido guarda lo que se envía; el perfil no se modifica.

### Enums nuevos

| Enum | Valores | Uso |
| --- | --- | --- |
| `Jornada` | `MANANA`, `TARDE` | `jornada` del pedido. Mostrar como "Mañana" / "Tarde" |
| `EstadoPedido` | `SOLICITADO` | `estado` del pedido. En el MVP todo pedido queda en SOLICITADO |

**`modalidadEntrega`** reutiliza `ModalidadLogistica`, pero en un pedido solo se aceptan `RECOGIDA_FINCA` y `DESPACHO_PRODUCTOR`. `AMBAS` nunca es válida en un pedido.

| `modalidadLogistica` de la oferta | Qué enviar en `modalidadEntrega` |
| --- | --- |
| `AMBAS` | **Obligatoria**: `RECOGIDA_FINCA` o `DESPACHO_PRODUCTOR` (que elija el comprador) |
| `RECOGIDA_FINCA` | Omitir (o `null`) → se usa `RECOGIDA_FINCA`. Enviar la misma también es válido |
| `DESPACHO_PRODUCTOR` | Omitir (o `null`) → se usa `DESPACHO_PRODUCTOR`. Enviar la misma también es válido |

---

### POST /api/pedidos

Crea el pedido. El comprador sale del token. `precioUnitario` se copia de la oferta y `precioTotalEstimado` (cantidad × precio, 2 decimales) lo calcula el backend: no se envían. **El pedido no descuenta la cantidad disponible de la oferta.**

**Request**

```json
{
  "ofertaId": "8f2d6c1e-4b7a-4f3e-9c2d-1a2b3c4d5e6f",
  "cantidadSolicitada": 120.00,
  "direccionEntrega": "Calle 49 # 50-21, local 3",
  "municipioEntrega": "RIONEGRO",
  "fechaPreferida": "2026-11-20",
  "jornada": "MANANA",
  "modalidadEntrega": "DESPACHO_PRODUCTOR",
  "notasAcceso": "Entrada por la puerta de carga, preguntar por Laura"
}
```

| Campo | Tipo | Obligatorio | Reglas |
| --- | --- | --- | --- |
| `ofertaId` | UUID | Sí | La oferta debe existir y estar activa |
| `cantidadSolicitada` | decimal | Sí | > 0, hasta 10 enteros y 2 decimales, ≤ `cantidadDisponible` de la oferta |
| `direccionEntrega` | string | Sí | No vacía, máx. 200 |
| `municipioEntrega` | string | Sí | Código o nombre del municipio, sin distinguir mayúsculas ni tildes (`RIONEGRO`, `Rionegro`, `El Peñol`) |
| `fechaPreferida` | fecha | Sí | ≥ hoy (hora Colombia). Si la oferta está `EN_CRECIMIENTO`, ≥ `fechaEstimadaCosecha` |
| `jornada` | `Jornada` | Sí | `MANANA` o `TARDE` |
| `modalidadEntrega` | `ModalidadLogistica` | Según la oferta | Ver tabla de modalidad |
| `notasAcceso` | string | No | Máx. 500. Vacía → se guarda `null` |

**Response 201** — header `Location: https://campored-backend.onrender.com/api/pedidos/3c9a…`

```json
{
  "id": "3c9a7e10-2f4b-4c1d-8e6a-5b7c9d0e1f23",
  "estado": "SOLICITADO",
  "fechaCreacion": "2026-11-05T15:00:00.123456",
  "cantidadSolicitada": 120.00,
  "precioUnitario": 3500.00,
  "precioTotalEstimado": 420000.00,
  "direccionEntrega": "Calle 49 # 50-21, local 3",
  "municipioEntrega": "RIONEGRO",
  "fechaPreferida": "2026-11-20",
  "jornada": "MANANA",
  "modalidadEntrega": "DESPACHO_PRODUCTOR",
  "notasAcceso": "Entrada por la puerta de carga, preguntar por Laura",
  "oferta": {
    "id": "8f2d6c1e-4b7a-4f3e-9c2d-1a2b3c4d5e6f",
    "producto": "TOMATE_CHONTO",
    "productoOtro": null,
    "unidadMedida": "KILOGRAMO",
    "fincaNombre": "Finca El Roble",
    "municipio": "SONSON"
  },
  "productor": {
    "nombreCompleto": "María Gómez",
    "whatsappDisponible": true,
    "llamadaDisponible": false,
    "whatsappContacto": "+573109876543",
    "telefonoContacto": null
  },
  "comprador": {
    "nombre": "Laura Restrepo",
    "nombreNegocio": "Restaurante El Fogón",
    "whatsapp": "+573201112233",
    "telefono": "+576045551234"
  }
}
```

- `fechaCreacion` viene sin zona horaria, igual que en ofertas (hora UTC del servidor).
- Si `oferta.producto` es `OTRO`, el nombre a mostrar está en `oferta.productoOtro`.
- `productor`: `whatsappDisponible` / `llamadaDisponible` son `true` solo si el productor habilitó el canal **y** tiene número. Los números (`whatsappContacto`, `telefonoContacto`) solo vienen cuando el canal correspondiente está disponible; si no, `null`. Son los datos para los botones de WhatsApp / Llamada (US-14 / US-15).
- `comprador.nombreNegocio`, `whatsapp` y `telefono` pueden ser `null`.

**Response 400 — errores por campo**

Los errores de formato (`@Valid`) y los de negocio llegan con el **mismo** formato y el mismo `mensaje` general. Pinta cada `errores.<campo>` bajo su input.

Primero se valida el formato. Si hay errores de formato, responde con esos. Si el formato está bien, se validan juntas las reglas de negocio (municipio, cantidad, fecha, modalidad) y llegan todas en una sola respuesta:

```json
{
  "timestamp": "2026-11-05T10:00:00.123",
  "status": 400,
  "error": "Bad Request",
  "mensaje": "La solicitud contiene campos inválidos",
  "ruta": "/api/pedidos",
  "errores": {
    "cantidadSolicitada": "La cantidad solicitada supera la disponible.",
    "fechaPreferida": "La fecha preferida no puede ser anterior a hoy.",
    "modalidadEntrega": "Debes seleccionar la modalidad de entrega."
  }
}
```

Un valor de `jornada` o `modalidadEntrega` fuera del enum (p. ej. `"NOCHE"`) responde 400 con `mensaje: "El cuerpo de la solicitud no es un JSON válido"` y sin `errores`. Usa selectores con los valores del enum para evitarlo.

**Response 404** — oferta inexistente

```json
{ "status": 404, "error": "Not Found", "mensaje": "La oferta no existe", "ruta": "/api/pedidos", "timestamp": "…" }
```

**Response 422** — oferta agotada

```json
{ "status": 422, "error": "Unprocessable Entity", "mensaje": "Esta oferta ya no está disponible para pedidos.", "ruta": "/api/pedidos", "timestamp": "…" }
```

---

### GET /api/pedidos/{id}

Devuelve el mismo `PedidoResponse` del POST. Solo lo pueden ver:

- el **comprador** que creó el pedido (incluye los números del productor según sus canales), y
- el **productor** dueño de la oferta (incluye el contacto del comprador).

Primero se valida que exista (404) y luego el permiso (403).

| Caso | HTTP | `mensaje` |
| --- | --- | --- |
| Pedido propio / de mi oferta | 200 | — |
| `id` que no es UUID | 400 | `El parámetro 'id' tiene un valor inválido: '…'` |
| Sin token | 401 | (sin cuerpo) |
| Otro comprador u otro productor | 403 | `No tienes permiso para ver este pedido.` |
| Pedido inexistente | 404 | `El pedido no existe` |

---

### DELETE /api/ofertas/{id}

Borrado **físico** de una oferta propia. Tras el 204 la oferta desaparece del catálogo, del detalle (`GET /api/ofertas/{id}` → 404) y de `mis-ofertas`. Se pueden eliminar tanto ofertas activas como agotadas, siempre que no tengan pedidos.

Pide confirmación antes de llamar ("¿Estás seguro? Esta acción es irreversible.").

| Caso | HTTP | `mensaje` |
| --- | --- | --- |
| Eliminada | 204 | (sin cuerpo) |
| `id` que no es UUID | 400 | `El parámetro 'id' tiene un valor inválido: '…'` |
| Sin token | 401 | (sin cuerpo) |
| Usuario comprador | 403 | `El rol del usuario no permite esta operación` |
| Oferta de otro productor | 403 | `No tienes permiso para modificar esta oferta.` |
| Oferta inexistente | 404 | `La oferta no existe` |
| La oferta tiene pedidos | 422 | `No puedes eliminar esta oferta porque tiene pedidos pendientes. Primero márcala como agotada.` |

Cuando llegue el bloque B (US-19) se suma otro 422 para ofertas con contactos registrados: `No puedes eliminar esta oferta porque ya tiene contactos de compradores. Márcala como agotada.` Conviene mostrar el `mensaje` del 422 tal como llega, sin depender del texto exacto.

---

### Mensajes y códigos HTTP del bloque A

| Endpoint | Caso | HTTP | Dónde llega | Mensaje |
| --- | --- | --- | --- | --- |
| POST pedidos | Sin oferta | 400 | `errores.ofertaId` | Debes indicar la oferta del pedido. |
| POST pedidos | Sin cantidad | 400 | `errores.cantidadSolicitada` | La cantidad solicitada es obligatoria. |
| POST pedidos | Cantidad ≤ 0 | 400 | `errores.cantidadSolicitada` | La cantidad solicitada debe ser mayor que cero. |
| POST pedidos | Cantidad con más de 10 enteros o 2 decimales | 400 | `errores.cantidadSolicitada` | La cantidad admite hasta 10 enteros y 2 decimales. |
| POST pedidos | Cantidad mayor a la disponible | 400 | `errores.cantidadSolicitada` | La cantidad solicitada supera la disponible. |
| POST pedidos | Sin dirección | 400 | `errores.direccionEntrega` | La dirección de entrega es obligatoria para continuar. |
| POST pedidos | Dirección > 200 | 400 | `errores.direccionEntrega` | La dirección de entrega no puede superar 200 caracteres. |
| POST pedidos | Sin municipio | 400 | `errores.municipioEntrega` | El municipio de entrega es obligatorio. |
| POST pedidos | Municipio fuera del catálogo | 400 | `errores.municipioEntrega` | El municipio de entrega no es válido. |
| POST pedidos | Sin fecha | 400 | `errores.fechaPreferida` | La fecha preferida de entrega es obligatoria. |
| POST pedidos | Fecha anterior a hoy | 400 | `errores.fechaPreferida` | La fecha preferida no puede ser anterior a hoy. |
| POST pedidos | Fecha anterior a la cosecha (oferta EN_CRECIMIENTO) | 400 | `errores.fechaPreferida` | La fecha preferida no puede ser anterior a la fecha estimada de cosecha. |
| POST pedidos | Sin jornada | 400 | `errores.jornada` | Debes seleccionar la jornada de entrega: Mañana o Tarde. |
| POST pedidos | Oferta AMBAS sin modalidad | 400 | `errores.modalidadEntrega` | Debes seleccionar la modalidad de entrega. |
| POST pedidos | Modalidad no habilitada o AMBAS | 400 | `errores.modalidadEntrega` | La modalidad de entrega no está habilitada en esta oferta. |
| POST pedidos | Notas > 500 | 400 | `errores.notasAcceso` | Las notas de acceso no pueden superar 500 caracteres. |
| POST pedidos | Usuario productor | 403 | `mensaje` | El rol del usuario no permite esta operación |
| POST pedidos | Oferta inexistente | 404 | `mensaje` | La oferta no existe |
| POST pedidos | Oferta agotada | 422 | `mensaje` | Esta oferta ya no está disponible para pedidos. |
| GET pedido | No es parte del pedido | 403 | `mensaje` | No tienes permiso para ver este pedido. |
| GET pedido | Inexistente | 404 | `mensaje` | El pedido no existe |
| DELETE oferta | Oferta ajena | 403 | `mensaje` | No tienes permiso para modificar esta oferta. |
| DELETE oferta | Inexistente | 404 | `mensaje` | La oferta no existe |
| DELETE oferta | Tiene pedidos | 422 | `mensaje` | No puedes eliminar esta oferta porque tiene pedidos pendientes. Primero márcala como agotada. |
| Todos | Sin token | 401 | — | (sin cuerpo) |

En todos los 400 con `errores`, `mensaje` es `La solicitud contiene campos inválidos`.
