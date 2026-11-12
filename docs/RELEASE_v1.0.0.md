# Release v1.0.0 — checklist del D2

US-25 Despliegue final (AB#56). Pasos que quedan después del bloque D1 (rama `feat/US-25-cierre-backend`) y que dependen del frontend. Se marcan en orden; si un paso falla, no se sigue con el siguiente.

> **Esquema:** si en algún momento hay un cambio de esquema, el script de `sql/` se ejecuta en el SQL Editor de Supabase **antes** del merge que trae el código. Producción corre con `ddl-auto=validate`: si el código llega primero, el backend no arranca en Render. Hoy no hay scripts pendientes; los seis de `db/migration/`, `sql/sprint2/` y `sql/sprint3/` ya están aplicados, incluido `sql/sprint3/US-24_ampliar_productos.sql` (catálogo de 23 productos).

## 1. Prerrequisitos

- [ ] Las fases F1 a F4 del frontend están mergeadas y desplegadas en Vercel.
- [ ] El dominio final del frontend está confirmado (hoy: `https://campored.rollerandres.dev` y `https://campored-frontend.vercel.app`).
- [ ] `CORS_ALLOWED_ORIGINS` en Render (servicio `campored-backend` → **Environment**) incluye el dominio final, sin `/` al final, separado por comas y sin `*`. La variable reemplaza el valor por defecto: deben quedar todos los orígenes que se usen. Comprobación del preflight (debe responder 200 con `Access-Control-Allow-Origin` igual al origen):

  ```bash
  curl -i -X OPTIONS https://campored-backend.onrender.com/api/pedidos \
    -H "Origin: https://campored.rollerandres.dev" \
    -H "Access-Control-Request-Method: POST" \
    -H "Access-Control-Request-Headers: authorization,content-type"
  ```

- [ ] El PR del bloque D1 está mergeado en `develop` y desplegado: en `https://campored-backend.onrender.com/v3/api-docs`, `components.schemas.PedidoResponse.properties.modalidadEntrega.enum` es `["RECOGIDA_FINCA", "DESPACHO_PRODUCTOR"]`.
- [ ] Los datos demo están cargados y `scripts/demo/verificar_datos_demo.sql` sale con `ok = true` en todas las filas.
- [ ] No quedan datos smoke de corridas anteriores (`scripts/smoke/limpiar_datos_smoke.sql`).

## 2. PR `develop` → `main`

Desde la raíz del repo:

```bash
git checkout develop && git pull --ff-only
git log --oneline origin/main..origin/develop      # revisar lo que entra
gh pr create --base main --head develop \
  --title "Release v1.0.0: MVP de CampoRed (sprints 1 a 3)" \
  --body-file <archivo con el texto de abajo>
gh pr merge <número> --merge                       # merge commit, sin squash ni rebase
```

Texto del PR:

```markdown
## Release v1.0.0 — MVP de CampoRed

Cierre del Sprint 3. Lleva a `main` todo lo integrado en `develop`.

### Historias de usuario

- Sprint 1: US-01 a US-05 y US-21 (registro, login y perfiles con JWT).
- Sprint 2: US-06 a US-09 y US-11 a US-13 (ofertas, mis ofertas, edición, agotar, catálogo y detalle).
- Sprint 3:
  - #13 — US-18 Checkout y pedido (AB#49) y US-10 Eliminar oferta (AB#38).
  - #14 — US-19 Historial de contactos (AB#50), backend de US-14 WhatsApp (AB#45) y US-15 Llamada (AB#46).
  - #16 — US-23 Pedidos recibidos (AB#54) y US-17 Canales de contacto (AB#47).
  - #17 — US-22 fechas con el offset de Colombia (AB#52) y smoke test del Sprint 3 (AB#56).
  - PR de `feat/US-25-cierre-backend` — Swagger de modalidades y 404 de perfil, reintento del smoke, datos demo, README y este checklist (AB#56, AB#49, AB#50, AB#47).

### Verificación

- `./mvnw clean verify` en verde (367 tests, cobertura de líneas 99,01 %, ramas 95,22 % en el cierre del D1).
- Smoke test del Sprint 3 contra Render: ver `docs/SMOKE_TEST_SPRINT3.md`.
- Datos demo verificados con `scripts/demo/verificar_datos_demo.sql`.

### Esquema

Sin cambios de esquema pendientes: los scripts de `sql/` ya están aplicados en Supabase.
```

## 3. Cambiar la rama de Render a `main`

- [ ] En Render: servicio `campored-backend` → **Settings** → **Build & Deploy** → **Branch** = `main` → **Save Changes**.
- [ ] **Manual Deploy** → **Deploy latest commit** y esperar a que **Events** muestre el deploy del merge commit del paso 2 como *live*.
- [ ] `https://campored-backend.onrender.com/v3/api-docs` responde 200 y `GET https://campored-backend.onrender.com/api/ofertas` responde 200.
- [ ] Desde aquí, los PR de feature siguen yendo a `develop`, y a producción solo llega lo que se mergea a `main`.

## 4. Smoke sobre `main` y limpieza

- [ ] Correr el smoke contra producción, con la tabla fuera del repo:

  ```powershell
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\smoke-sprint3.ps1 -TablaMd $env:TEMP\smoke-main.md
  ```

  Debe terminar con código 0. Una fila con «⚠ reintento tras 502/503/504» es un fallo pasajero de Render que se recuperó en el reintento; se anota, pero no bloquea.
- [ ] En el SQL Editor de Supabase: `scripts/smoke/verificar_esquema_sprint3.sql` (todo `ok = true`) y después `scripts/smoke/limpiar_datos_smoke.sql` (columna `despues` en 0).
- [ ] `scripts/demo/verificar_datos_demo.sql` sigue en `ok = true`: la limpieza smoke no toca los datos demo.
- [ ] Agregar la corrida a `docs/SMOKE_TEST_SPRINT3.md` como la vigente del release.

## 5. Recorrido completo — escenario 2 de US-25

Con el frontend en el dominio final y las cuentas demo. La contraseña es la de `DEMO_PASSWORD` con que se cargaron; los ids de cada oferta están en la sección «Usuarios demo» de `usuarios-prueba-render.md`.

| Rol | Cuenta | Para qué |
| --- | --- | --- |
| Comprador | C6 `compras.donarosa@campored.demo` — Tienda Doña Rosa | Empieza sin pedidos ni contactos: muestra el estado vacío y hace el pedido nuevo |
| Comprador | C1 `compras.fogonpaisa@campored.demo` — Restaurante Fogón Paisa | Historial con 3 pedidos y 3 contactos |
| Productor | P8 `diana.cardona@campored.demo` — Finca Puerto Garza | WhatsApp y llamada; recibe el pedido nuevo y elimina una oferta |
| Productor | P1 `luis.ospina@campored.demo` — Finca Villa Mora | 2 pedidos recibidos; su mora de castilla está bloqueada para eliminar |
| Productor | P4 `maria.restrepo@campored.demo` — Finca Los Naranjos | Solo llamada |

Catálogo → pedido → WhatsApp → Mis contactos → panel del productor:

- [ ] Sin sesión, el catálogo incluye las 26 ofertas demo activas; los filtros por municipio (Sonsón, San Carlos) y por estado funcionan, y las 3 agotadas no aparecen.
- [ ] Login como C6: **Mis contactos** muestra el estado vacío.
- [ ] Detalle de **Maracuyá** (P8): aparecen los botones de WhatsApp y de llamada.
- [ ] **Hacer pedido**: el checkout llega precargado con la dirección y el municipio de Tienda Doña Rosa (La Ceja). Elegir cantidad, fecha (hoy o después), jornada y confirmar: el pedido queda SOLICITADO con el total calculado.
- [ ] Desde el pedido, **WhatsApp** abre `wa.me` con el número de P8 y queda registrado el contacto.
- [ ] **Mis contactos** de C6 muestra el contacto nuevo, con el pedido, de primero.
- [ ] Login como P8: **Pedidos recibidos** muestra el pedido de Tienda Doña Rosa con su dirección, jornada y los números del comprador; el contador de pendientes sube a 1.
- [ ] Detalle de una oferta de P4 como comprador: solo aparece **Llamar**, con `tel:` al número de P4.
- [ ] Login como C1: **Mis contactos** muestra 3 contactos, del más reciente al más antiguo.

Eliminar ofertas (US-10):

- [ ] Como P8, eliminar **Cacao en grano seco** (eliminable): responde 204 y desaparece de mis ofertas y del catálogo.
- [ ] Como P1, intentar eliminar **Mora de castilla** (bloqueada por pedido): responde 422 con «No puedes eliminar esta oferta porque tiene pedidos pendientes. Primero márcala como agotada.».
- [ ] Opcional: como P2, intentar eliminar **Arveja verde** (solo tiene un contacto): responde 422 con «No puedes eliminar esta oferta porque ya tiene contactos de compradores. Márcala como agotada.».

El recorrido cambia los datos demo (pedido y contacto de C6, oferta borrada de P8). Para dejarlos como al principio antes de la presentación: `limpiar_datos_demo.sql` → `seed-demo.mjs` → `verificar_datos_demo.sql`.

## 6. Tag `v1.0.0`

Sobre el merge commit de `main` ya desplegado y verificado:

```bash
git checkout main && git pull --ff-only
git tag -a v1.0.0 -m "CampoRed MVP v1.0.0 (sprints 1 a 3)"
git push origin v1.0.0
```

Opcional: crear el release en GitHub desde el tag con el mismo texto del PR del paso 2.
