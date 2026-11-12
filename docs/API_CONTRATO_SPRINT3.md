# Contrato API — Sprint 3

Base URL: `https://campored-backend.onrender.com` · Swagger: `/swagger-ui.html`

Si este documento y Swagger no coinciden, manda Swagger y se corrige el documento.

Convenciones generales (iguales al Sprint 2):

- Token en `Authorization: Bearer <token>`.
- **401** llega **sin cuerpo** (token ausente, inválido o expirado).
- Los demás errores usan `ErrorResponse`: `{ timestamp, status, error, mensaje, ruta, errores? }`. `errores` es un mapa `campo → mensaje` y solo aparece cuando hay errores por campo.
- Fechas `LocalDate` como `"2026-11-20"`. Fechas con hora con el offset de Colombia (ver [Formato de fechas](#formato-de-fechas)). Montos y cantidades como número decimal (`420000.00`).
- Un token válido de un usuario que ya no existe responde **404** `Perfil de usuario no encontrado` en `POST /api/pedidos`, `POST /api/contactos`, `GET /api/pedidos/recibidos` y `PATCH /api/usuarios/perfil/productor`.
- En pedidos, `modalidadEntrega` solo admite `RECOGIDA_FINCA` y `DESPACHO_PRODUCTOR`; Swagger lo documenta así aunque el enum `ModalidadLogistica` incluya `AMBAS`.

### Formato de fechas

Todas las fechas con hora del API salen en **hora de Colombia con offset explícito** (`-05:00`), en formato ISO 8601:

| Campo | Dónde aparece |
| --- | --- |
| `fechaCreacion` | pedidos, ofertas (crear, editar, agotar, mis-ofertas, catálogo y detalle) |
| `fechaContacto` | contactos |
| `fechaRegistro` | `usuario` del login, del registro y de los PATCH de perfil |
| `timestamp` | todas las respuestas de error (`ErrorResponse`) |

Ejemplo: `"fechaCreacion": "2026-11-05T20:28:07.616432-05:00"`. La parte fraccionaria puede traer entre 0 y 9 dígitos.

**Nota para el frontend:** `new Date(valor)` interpreta bien estas cadenas porque traen el offset. **No hay que concatenar `Z`**: eso movería la hora cinco horas. Para mostrarlas, formatea con `toLocaleString("es-CO", { timeZone: "America/Bogota" })` o `Intl.DateTimeFormat` con esa zona. Las fechas sin hora (`fechaPreferida`, `fechaEstimadaCosecha`) siguen como `"2026-11-20"`, sin offset.

---

## US-24 — Catálogo de productos ampliado (AB#55)

El enum `Producto` pasa de 8 a 23 valores. Los 7 originales y `OTRO` no cambian; los 15 nuevos van antes de `OTRO`. Aplica a `producto` en crear y editar ofertas, al filtro `GET /api/ofertas?producto=`, a `oferta.producto` de los pedidos y al texto `producto` de los contactos.

| Valor | Etiqueta |
| --- | --- |
| `ARVEJA_VERDE` | Arveja verde |
| `CACAO` | Cacao en grano seco |
| `CEBOLLA` | Cebolla |
| `GRANADILLA` | Granadilla |
| `GULUPA` | Gulupa |
| `LIMON_TAHITI` | Limón Tahití |
| `LULO` | Lulo |
| `MARACUYA` | Maracuyá |
| `PANELA` | Panela |
| `PAPA_CAPIRA` | Papa capira |
| `PIMENTON` | Pimentón |
| `PINA` | Piña |
| `TOMATE_ARBOL` | Tomate de árbol |
| `UCHUVA` | Uchuva |
| `YUCA` | Yuca |

- El filtro por producto sigue la regla de US-13: `producto=PANELA` trae también las ofertas `OTRO` cuyo `productoOtro` es "Panela" (sin distinguir mayúsculas, tildes ni espacios en los extremos). Las ofertas `OTRO` que ya existían no se migran.
- Un valor fuera del enum responde igual que antes: 400 con `mensaje` `El parámetro 'producto' tiene un valor inválido: '…'. Valores permitidos: …`, que ahora lista los 23 valores.
- Esquema: `sql/sprint3/US-24_ampliar_productos.sql` amplía el `CHECK` de `ofertas.producto`.

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
  "fechaCreacion": "2026-11-05T15:00:00.123456-05:00",
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

- `fechaCreacion` viene en hora de Colombia con offset (`-05:00`); ver [Formato de fechas](#formato-de-fechas).
- Si `oferta.producto` es `OTRO`, el nombre a mostrar está en `oferta.productoOtro`.
- `productor`: `whatsappDisponible` / `llamadaDisponible` son `true` solo si el productor habilitó el canal **y** tiene número. Los números (`whatsappContacto`, `telefonoContacto`) solo vienen cuando el canal correspondiente está disponible; si no, `null`. Son los datos para los botones de WhatsApp / Llamada (US-14 / US-15).
- `comprador.nombreNegocio`, `whatsapp` y `telefono` pueden ser `null`.

**Response 400 — errores por campo**

Los errores de formato (`@Valid`) y los de negocio llegan con el **mismo** formato y el mismo `mensaje` general. Pinta cada `errores.<campo>` bajo su input.

Primero se valida el formato. Si hay errores de formato, responde con esos. Si el formato está bien, se validan juntas las reglas de negocio (municipio, cantidad, fecha, modalidad) y llegan todas en una sola respuesta:

```json
{
  "timestamp": "2026-11-05T10:00:00.123-05:00",
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

Borrado **físico** de una oferta propia. Tras el 204 la oferta desaparece del catálogo, del detalle (`GET /api/ofertas/{id}` → 404) y de `mis-ofertas`. Se pueden eliminar tanto ofertas activas como agotadas, siempre que no tengan pedidos ni contactos registrados.

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
| La oferta tiene contactos de compradores (sin pedidos) | 422 | `No puedes eliminar esta oferta porque ya tiene contactos de compradores. Márcala como agotada.` |

Si la oferta tiene pedidos y contactos, llega el mensaje de pedidos. Conviene mostrar el `mensaje` del 422 tal como llega, sin depender del texto exacto.

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
| DELETE oferta | Tiene contactos (sin pedidos) | 422 | `mensaje` | No puedes eliminar esta oferta porque ya tiene contactos de compradores. Márcala como agotada. |
| Todos | Sin token | 401 | — | (sin cuerpo) |

En todos los 400 con `errores`, `mensaje` es `La solicitud contiene campos inválidos`.

---

## Bloque B — US-19 Historial de contactos (AB#50) + backend de US-14 (AB#45) y US-15 (AB#46)

### Resumen de endpoints

| Método | Ruta | Rol | Éxito |
| --- | --- | --- | --- |
| POST | `/api/contactos` | COMPRADOR | 201 + `ContactoResponse` (sin header `Location`) |
| GET | `/api/contactos/mios` | COMPRADOR | 200 + lista de `ContactoResponse` |

US-14 (WhatsApp) y US-15 (Llamada) no tienen endpoints propios: los números salen de `productor` en el detalle de la oferta (`GET /api/ofertas/{id}`) y en el pedido (`GET /api/pedidos/{id}`), y el contacto se registra con `POST /api/contactos`.

### Cómo registrar el contacto desde el frontend

- Al presionar "Coordinar por WhatsApp" o "Llamar al productor", abre `wa.me` o `tel:` **y en paralelo** envía `POST /api/contactos`. No esperes la respuesta para abrir el enlace.
- Si el registro falla (red, 422, lo que sea), **no se lo muestres al usuario**: el contacto ya ocurrió. A lo sumo, déjalo en la consola.
- Muestra cada botón solo si `whatsappDisponible` / `llamadaDisponible` es `true`. Con esa regla el 422 de canal no debería ocurrir; si ocurre, es porque el productor cambió sus canales mientras el comprador tenía la página abierta.
- Envía `pedidoId` cuando el contacto sale de la pantalla del pedido (checkout o detalle del pedido). Desde el detalle de la oferta, omítelo.

### Enum nuevo

| Enum | Valores | Uso |
| --- | --- | --- |
| `CanalContacto` | `WHATSAPP`, `LLAMADA` | `canal` del contacto. Mostrar como "WhatsApp" / "Llamada" |

---

### POST /api/contactos

Registra un contacto del comprador autenticado con el productor de una oferta. La fecha la fija el backend. **Se registra aunque la oferta esté agotada.**

No devuelve `Location` porque no existe `GET /api/contactos/{id}`; el historial se consulta en `/mios`.

**Request**

```json
{
  "ofertaId": "8f2d6c1e-4b7a-4f3e-9c2d-1a2b3c4d5e6f",
  "canal": "WHATSAPP",
  "pedidoId": "3c9a7e10-2f4b-4c1d-8e6a-5b7c9d0e1f23"
}
```

| Campo | Tipo | Obligatorio | Reglas |
| --- | --- | --- | --- |
| `ofertaId` | UUID | Sí | La oferta debe existir (activa o agotada) |
| `canal` | `CanalContacto` | Sí | El productor debe tener ese canal habilitado **y** con número |
| `pedidoId` | UUID | No | Si llega, debe ser un pedido del mismo comprador sobre la misma oferta |

**Response 201**

```json
{
  "id": "b7e4c2a1-9d3f-4e6b-8a1c-2f3e4d5c6b7a",
  "fechaContacto": "2026-11-05T15:00:00.123456-05:00",
  "canal": "WHATSAPP",
  "ofertaId": "8f2d6c1e-4b7a-4f3e-9c2d-1a2b3c4d5e6f",
  "pedidoId": "3c9a7e10-2f4b-4c1d-8e6a-5b7c9d0e1f23",
  "producto": "Tomate chonto",
  "productorNombre": "María Gómez",
  "fincaNombre": "Finca El Roble"
}
```

- `producto` ya viene como texto para mostrar: el nombre visible del catálogo (`Tomate chonto`, `Café pergamino`…) o, si la oferta es `OTRO`, lo que escribió el productor en `productoOtro`.
- `pedidoId` es `null` cuando el contacto no salió de un pedido.
- No incluye números de contacto.
- `fechaContacto` viene en hora de Colombia con offset (`-05:00`); ver [Formato de fechas](#formato-de-fechas).

**Response 422 — canal no disponible**

```json
{
  "timestamp": "2026-11-05T10:00:00.123-05:00",
  "status": 422,
  "error": "Unprocessable Entity",
  "mensaje": "El productor no tiene habilitado este canal de contacto.",
  "ruta": "/api/contactos"
}
```

Un `canal` fuera del enum (p. ej. `"SMS"`) responde 400 con `mensaje: "El cuerpo de la solicitud no es un JSON válido"` y sin `errores`.

---

### GET /api/contactos/mios

Historial del comprador autenticado, del más reciente al más antiguo, **sin paginar**. Solo trae los contactos propios.

**Response 200**

```json
[
  {
    "id": "b7e4c2a1-9d3f-4e6b-8a1c-2f3e4d5c6b7a",
    "fechaContacto": "2026-11-05T15:00:00.123456-05:00",
    "canal": "LLAMADA",
    "ofertaId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
    "pedidoId": null,
    "producto": "Aguacate",
    "productorNombre": "María Gómez",
    "fincaNombre": "Finca El Roble"
  },
  {
    "id": "c8f5d3b2-0e4a-4f7c-9b2d-3a4b5c6d7e8f",
    "fechaContacto": "2026-11-04T21:30:00.654321-05:00",
    "canal": "WHATSAPP",
    "ofertaId": "8f2d6c1e-4b7a-4f3e-9c2d-1a2b3c4d5e6f",
    "pedidoId": "3c9a7e10-2f4b-4c1d-8e6a-5b7c9d0e1f23",
    "producto": "Tomate chonto",
    "productorNombre": "María Gómez",
    "fincaNombre": "Finca El Roble"
  }
]
```

Sin contactos responde `200` con `[]`. Ahí el frontend muestra "Aún no has contactado a ningún productor. Explora el catálogo." (US-19 Esc 3).

---

### Mensajes y códigos HTTP del bloque B

| Endpoint | Caso | HTTP | Dónde llega | Mensaje |
| --- | --- | --- | --- | --- |
| POST contactos | Sin oferta | 400 | `errores.ofertaId` | Debes indicar la oferta contactada. |
| POST contactos | Sin canal | 400 | `errores.canal` | Debes indicar el canal de contacto. |
| POST contactos | Canal fuera del enum | 400 | `mensaje` | El cuerpo de la solicitud no es un JSON válido |
| POST contactos | `ofertaId` o `pedidoId` que no es un UUID | 400 | `mensaje` | El cuerpo de la solicitud no es un JSON válido |
| POST contactos | Usuario productor | 403 | `mensaje` | El rol del usuario no permite esta operación |
| POST contactos | Oferta inexistente | 404 | `mensaje` | La oferta no existe |
| POST contactos | Canal apagado o sin número | 422 | `mensaje` | El productor no tiene habilitado este canal de contacto. |
| POST contactos | Pedido inexistente, de otro comprador o de otra oferta | 422 | `mensaje` | El pedido no corresponde a esta oferta. |
| GET mios | Usuario productor | 403 | `mensaje` | El rol del usuario no permite esta operación |
| Todos | Sin token | 401 | — | (sin cuerpo) |

Orden de las validaciones del POST: formato (400) → oferta (404) → canal (422) → pedido (422).

En todos los 400 con `errores`, `mensaje` es `La solicitud contiene campos inválidos`.

---

## Bloque C — US-23 Pedidos recibidos (AB#54) + US-17 Canales de contacto (AB#47)

### Resumen de endpoints

| Método | Ruta | Rol | Éxito |
| --- | --- | --- | --- |
| GET | `/api/pedidos/recibidos` | PRODUCTOR | 200 + lista de `PedidoResponse` |
| PATCH | `/api/usuarios/perfil/productor` | PRODUCTOR | 200 + `UsuarioResponse` (endpoint existente de US-04; cambian los mensajes de error) |

No existe `/api/usuarios/perfil/contacto`: los canales se configuran con `PATCH /api/usuarios/perfil/productor`.

---

### GET /api/pedidos/recibidos

Pedidos hechos sobre las ofertas del productor autenticado, del más reciente al más antiguo, **sin paginar**. Incluye pedidos de ofertas ya agotadas. Un productor nunca ve pedidos de ofertas de otro.

Cada elemento es el **mismo `PedidoResponse`** de US-18 (ver Bloque A). Para el panel, los datos del comprador vienen en `comprador { nombre, nombreNegocio, whatsapp, telefono }` y los de entrega en `direccionEntrega`, `municipioEntrega`, `fechaPreferida`, `jornada`, `modalidadEntrega` y `notasAcceso`.

**Contador "Pedidos pendientes":** no hay endpoint de conteo. Calcúlalo en el cliente con `respuesta.length`: en el MVP todos los pedidos están en `SOLICITADO` y no se pueden aceptar, entregar ni cancelar.

**Response 200 — con pedidos**

```json
[
  {
    "id": "5d1e2f3a-4b5c-4d6e-8f7a-9b0c1d2e3f4a",
    "estado": "SOLICITADO",
    "fechaCreacion": "2026-11-05T16:20:00.123456-05:00",
    "cantidadSolicitada": 30.00,
    "precioUnitario": 52000.00,
    "precioTotalEstimado": 1560000.00,
    "direccionEntrega": "Carrera 50 # 45-10",
    "municipioEntrega": "LA_CEJA",
    "fechaPreferida": "2026-11-12",
    "jornada": "TARDE",
    "modalidadEntrega": "RECOGIDA_FINCA",
    "notasAcceso": null,
    "oferta": {
      "id": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
      "producto": "AGUACATE",
      "productoOtro": null,
      "unidadMedida": "ARROBA",
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
      "nombre": "Andrés Mejía",
      "nombreNegocio": "La Parrilla",
      "whatsapp": "+573157778899",
      "telefono": null
    }
  },
  {
    "id": "3c9a7e10-2f4b-4c1d-8e6a-5b7c9d0e1f23",
    "estado": "SOLICITADO",
    "fechaCreacion": "2026-11-05T15:00:00.123456-05:00",
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
]
```

**Response 200 — sin pedidos**

```json
[]
```

| Caso | HTTP | `mensaje` |
| --- | --- | --- |
| Lista (con o sin pedidos) | 200 | — |
| Sin token | 401 | (sin cuerpo) |
| Usuario comprador | 403 | `El rol del usuario no permite esta operación` |
| Perfil del token inexistente | 404 | `Perfil de usuario no encontrado` |

---

### PATCH /api/usuarios/perfil/productor — canales de contacto (US-17)

El endpoint ya existía (US-04). Este bloque solo cambia dos cosas:

1. Los mensajes al habilitar un canal sin número son los del BDD.
2. Ese error **siempre** llega por campo en `errores.whatsapp` / `errores.telefono`, también cuando el número no viene en la petición y el productor no tiene uno guardado. Antes ese caso llegaba solo en `mensaje`.

**Request — Esc 1: activar WhatsApp con número y apagar la llamada**

```json
{
  "whatsapp": "+573109876543",
  "canalWhatsappHabilitado": true,
  "canalLlamadaHabilitado": false
}
```

Solo se modifican los campos enviados. Con este cambio, en todas las ofertas del productor `whatsappDisponible` es `true` y `llamadaDisponible` es `false`, y los compradores solo reciben `whatsappContacto`.

**Response 400 — Esc 2: activar WhatsApp sin número**

```json
{
  "timestamp": "2026-11-05T10:00:00.123-05:00",
  "status": 400,
  "error": "Bad Request",
  "mensaje": "La solicitud contiene campos inválidos",
  "ruta": "/api/usuarios/perfil/productor",
  "errores": {
    "whatsapp": "Debes ingresar un número de WhatsApp para habilitar este canal."
  }
}
```

El cambio no se guarda. Si fallan los dos canales, llegan `errores.telefono` y `errores.whatsapp` en la misma respuesta.

**Esc 3: ningún canal activo.** Enviar `canalWhatsappHabilitado: false` y `canalLlamadaHabilitado: false` responde 200 y se guarda. El aviso lo pinta el frontend cuando los dos quedan en `false`:

> Los compradores no podrán contactarte hasta que actives al menos un canal.

| Caso | HTTP | Dónde llega | Mensaje |
| --- | --- | --- | --- |
| WhatsApp habilitado sin número (vacío en la petición o sin número guardado) | 400 | `errores.whatsapp` | Debes ingresar un número de WhatsApp para habilitar este canal. |
| Llamada habilitada sin teléfono (vacío en la petición o sin número guardado) | 400 | `errores.telefono` | Debes ingresar un número de teléfono para habilitar este canal. |
| Teléfono o WhatsApp con formato inválido | 400 | `errores.telefono` / `errores.whatsapp` | El teléfono debe tener entre 10 y 13 dígitos / El WhatsApp debe tener entre 10 y 13 dígitos |
| Ambos canales apagados | 200 | — | (aviso del frontend) |
| Sin token | 401 | — | (sin cuerpo) |
| Usuario comprador | 403 | `mensaje` | El rol del usuario no permite esta operación |
| Perfil del token inexistente | 404 | `mensaje` | Perfil de usuario no encontrado |

En todos los 400 con `errores`, `mensaje` es `La solicitud contiene campos inválidos`.
