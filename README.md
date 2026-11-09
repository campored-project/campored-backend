# CampoRed — Backend

> API RESTful para la plataforma de comercialización directa entre productores agrícolas y compradores comerciales del Oriente Antioqueño.

---

## Tabla de contenidos

- [Descripción](#descripción)
- [Stack tecnológico](#stack-tecnológico)
- [Requisitos previos](#requisitos-previos)
- [Configuración del entorno local](#configuración-del-entorno-local)
- [Variables de entorno](#variables-de-entorno)
- [Ejecución](#ejecución)
- [Arquitectura y estructura del proyecto](#arquitectura-y-estructura-del-proyecto)
- [Modelo de datos](#modelo-de-datos)
- [API — Endpoints implementados](#api--endpoints-implementados)
- [Seguridad](#seguridad)
- [Testing](#testing)
  - [Smoke test contra producción](#smoke-test-contra-producción)
  - [Scripts SQL](#scripts-sql)
  - [Datos de prueba](#datos-de-prueba)
- [Flujo de trabajo Git](#flujo-de-trabajo-git)
- [Integración con Azure DevOps](#integración-con-azure-devops)
- [Despliegue](#despliegue)
- [Equipo](#equipo)

---

## Descripción

CampoRed conecta pequeños productores agrícolas de **Sonsón** y **San Carlos** (Antioquia) con compradores comerciales (restaurantes, tiendas y mayoristas) del Oriente Antioqueño y el Área Metropolitana de Medellín, eliminando intermediarios en la cadena de distribución local.

Este repositorio contiene el backend del MVP: una API REST modular construida con Spring Boot 3 sobre PostgreSQL, con autenticación JWT, control de acceso por roles (PRODUCTOR / COMPRADOR) y cobertura de tests del **99 %** de líneas.

**Producción (Render):**
- **API:** `https://campored-backend.onrender.com/api`
- **Swagger UI:** `https://campored-backend.onrender.com/swagger-ui.html`
- **OpenAPI JSON:** `https://campored-backend.onrender.com/v3/api-docs`

> Render (plan Free) apaga el servicio tras unos minutos sin tráfico: la primera petición puede tardar hasta un minuto mientras arranca.

---

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.2.0 |
| Base de datos | PostgreSQL (Supabase en producción) |
| Seguridad | Spring Security + JWT (jjwt 0.12) + BCrypt (costo 10) |
| Build | Maven 3.9+ |
| Testing | JUnit 5 + Mockito + Spring Boot Test |
| Cobertura | JaCoCo |
| Documentación API | SpringDoc OpenAPI (Swagger UI) |
| Control de versiones | Git + GitHub |
| Gestión de proyecto | Azure DevOps (Scrum — sprints mensuales) |

---

## Requisitos previos

- **Java 21** — [Descargar Temurin](https://adoptium.net/)
- **Maven 3.9+** — o usar el wrapper `mvnw` incluido en el repo
- **PostgreSQL 15+** — local, o usar la instancia de Supabase del equipo
- **Git**
- **Node.js 18+** — solo para el seed de datos demo (`scripts/demo/seed-demo.mjs`)
- **Windows PowerShell 5.1** — solo para el smoke test (`scripts/smoke/smoke-sprint3.ps1`)

Verificar versiones:

```bash
java -version
mvn -version   # o .\mvnw.cmd -version en Windows
```

---

## Configuración del entorno local

### 1. Clonar el repositorio

```bash
git clone https://github.com/campored-project/campored-backend.git
cd campored-backend
git checkout develop
```

### 2. Crear la base de datos local (opcional si usas Supabase)

```sql
CREATE DATABASE campored_db;
```

### 3. Crear el archivo de propiedades local

Crea `src/main/resources/application-local.properties` (está en `.gitignore`, no se sube al repo):

```properties
# Base de datos
spring.datasource.url=jdbc:postgresql://localhost:5432/campored_db
spring.datasource.username=postgres
spring.datasource.password=TU_PASSWORD_LOCAL

# JWT
jwt.secret=CLAVE_SECRETA_LOCAL_MINIMO_256_BITS_PARA_DESARROLLO
jwt.expiration=86400000

# JPA — valida el esquema contra las entidades, nunca lo modifica
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.profiles.active=local
```

> **Nota:** Si usas la instancia de Supabase del equipo, solicita las credenciales. La conexión directa es solo IPv6; usa siempre el **Session Pooler** (`aws-0-us-east-2.pooler.supabase.com:5432`).

> **No uses `ddl-auto=update` (ni `create`) contra Supabase.** Hibernate alteraría la base de producción sin los `CHECK`, índices ni RLS de los scripts y la dejaría distinta de lo que valida Render. Todo cambio de esquema es un script de `sql/` que se ejecuta a mano en el SQL Editor de Supabase **antes** del merge (ver [Scripts SQL](#scripts-sql)). Con una base local vacía, crea las tablas ejecutando esos scripts en orden.

---

## Variables de entorno

Son las que lee `src/main/resources/application.properties`:

| Variable | Descripción | Ejemplo |
|---|---|---|
| `DB_URL` | URL JDBC de la base de datos | `jdbc:postgresql://aws-0-us-east-2.pooler.supabase.com:5432/postgres?sslmode=require` |
| `DB_USERNAME` | Usuario de la BD | `postgres.xxxxxxxx` |
| `DB_PASSWORD` | Contraseña de la BD | `***` |
| `JWT_SECRET` | Clave para firmar tokens (mínimo 256 bits) | `your-super-secret-key...` |
| `JWT_EXPIRATION` | Duración del token en ms (por defecto 24 h) | `86400000` |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos para CORS, separados por coma (por defecto `http://localhost:5173`, `https://campored-frontend.vercel.app` y `https://campored.rollerandres.dev`) | `http://localhost:5173,https://campored-frontend.vercel.app` |
| `PORT` | Puerto HTTP (por defecto 8080; Render lo asigna solo) | `8080` |

En producción (Render) estas variables se configuran en **Environment** del servicio `campored-backend`. **Nunca se suben al repositorio.**

### CORS

- Orígenes permitidos:
  - `http://localhost:5173` (frontend Vite dev)
  - `https://campored-frontend.vercel.app` (frontend en producción, Vercel)
  - `https://campored.rollerandres.dev` (frontend, dominio propio)
- Para otros orígenes: definirlos en la variable de entorno `CORS_ALLOWED_ORIGINS`
- Métodos: GET, POST, PUT, PATCH, DELETE, OPTIONS
- Credenciales: habilitadas
- Ruta: `/api/**` — Swagger UI no lo necesita porque se sirve desde el mismo origen

> **Nota:** `CORS_ALLOWED_ORIGINS` **reemplaza** el valor por defecto: si la defines, incluye también los orígenes anteriores. Escribe cada origen sin `/` final y no uses `*`, que es incompatible con credenciales habilitadas.

---

## Ejecución

### Desarrollo local

```bash
# Con wrapper Maven (recomendado)
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local   # Windows
./mvnw spring-boot:run -Dspring-boot.run.profiles=local        # Linux/Mac
```

La API queda disponible en:

- **API base:** `http://localhost:8080/api`
- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`

### Compilar JAR

```bash
.\mvnw.cmd clean package -DskipTests
java -jar target/campored-backend-0.0.1-SNAPSHOT.jar
```

---

## Arquitectura y estructura del proyecto

El backend sigue un patrón en capas estricto: **Controller → Service → Repository**, con DTOs para las entradas/salidas y entidades JPA para la persistencia.

```
src/
├── main/
│   ├── java/com/campored/backend/
│   │   ├── config/             # SecurityConfig, JwtAuthFilter, CorsConfig, OpenApiConfig, TimeConfig
│   │   ├── controller/         # Auth, Usuario, Oferta, Pedido y Contacto
│   │   ├── dto/                # Request/Response objects, ErrorResponse, PageResponse
│   │   │   └── validation/     # Anotaciones y validadores personalizados
│   │   ├── entity/             # Usuario, Finca, Negocio, Oferta, Pedido, RegistroContacto y enums
│   │   ├── exception/          # GlobalExceptionHandler, excepciones personalizadas
│   │   ├── repository/         # Interfaces JpaRepository y OfertaSpecifications
│   │   ├── service/            # Auth, Jwt, Usuario, Oferta, Pedido y Contacto
│   │   └── util/               # Mappers, ProductoNormalizer, FechaRespuesta
│   └── resources/
│       ├── application.properties          # Config base (compartida)
│       ├── application-local.properties    # Config local (gitignored)
│       └── db/migration/                   # Script SQL manual del Sprint 1 (US-04)
│
└── test/
    └── java/com/campored/backend/
        ├── config/             # SecurityIntegrationTest, CorsIntegrationTest
        ├── controller/         # Integración por controlador, con JWT reales y H2
        ├── service/            # Unitarios de servicio con Mockito
        └── ...                 # dto/validation, entity, repository, util

sql/sprint2/, sql/sprint3/      # Scripts SQL manuales de Supabase (ver Scripts SQL)
scripts/smoke/                  # Smoke test contra producción y su limpieza
scripts/demo/                   # Datos demo para la presentación
docs/                           # Contrato API del Sprint 3, smoke test y checklist de release
```

---

## Modelo de datos

### Entidades principales

```
Usuario
├── id                         UUID (PK)
├── correo                     VARCHAR UNIQUE NOT NULL
├── contrasena_hash            VARCHAR NOT NULL
├── nombre                     VARCHAR NOT NULL
├── telefono                   VARCHAR (opcional)
├── rol                        ENUM {PRODUCTOR, COMPRADOR}
├── canal_whatsapp_habilitado  BOOLEAN DEFAULT false
├── canal_llamada_habilitado   BOOLEAN DEFAULT false
└── created_at                 TIMESTAMP

Finca  (solo si rol = PRODUCTOR)
├── id              UUID (PK)
├── usuario_id      FK → Usuario
├── nombre_finca    VARCHAR NOT NULL
├── municipio       VARCHAR NOT NULL
└── vereda          VARCHAR

Negocio  (solo si rol = COMPRADOR)
├── id                UUID (PK)
├── usuario_id        FK → Usuario UNIQUE
├── nombre_negocio    VARCHAR NOT NULL
├── tipo_negocio      VARCHAR NOT NULL
├── direccion         VARCHAR NOT NULL
├── municipio         VARCHAR NOT NULL
├── horario_recepcion VARCHAR (opcional)
└── notas_acceso      VARCHAR (opcional)
```

**Regla:** un usuario tiene `Finca` **o** `Negocio`, nunca ambos.

Los Sprints 2 y 3 agregan `ofertas` (US-06), `pedidos` (US-18) y `registros_contacto` (US-19). Sus columnas, `CHECK` e índices están en los scripts de [Scripts SQL](#scripts-sql).

---

## API — Endpoints implementados

### Autenticación — `/api/auth`

| Método | Endpoint | Descripción | Auth |
|---|---|---|---|
| `POST` | `/api/auth/registro/productor` | Registrar productor con datos de finca | Público |
| `POST` | `/api/auth/registro/comprador` | Registrar comprador con datos de negocio | Público |
| `POST` | `/api/auth/login` | Autenticar y obtener JWT | Público |

#### POST `/api/auth/registro/productor`

```json
// Request
{
  "correo": "juan@finca.com",
  "contrasena": "SecurePass123!",
  "nombre": "Juan Pérez",
  "telefono": "+573001234567",
  "nombreFinca": "Finca La Esperanza",
  "municipio": "SONSON",
  "vereda": "El Sauce"
}

// Response 201
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tipo": "Bearer",
  "expiraEnMs": 86400000,
  "usuario": {
    "id": "uuid",
    "nombre": "Juan Pérez",
    "correo": "juan@finca.com",
    "rol": "PRODUCTOR",
    "nombreFinca": "Finca La Esperanza",
    "municipio": "SONSON",
    "vereda": "El Sauce",
    "canalWhatsappHabilitado": false,
    "canalLlamadaHabilitado": false
  }
}
```

#### POST `/api/auth/registro/comprador`

```json
// Request
{
  "correo": "restaurante@email.com",
  "contrasena": "SecurePass123!",
  "nombre": "Restaurante El Oriente",
  "nombreNegocio": "El Oriente",
  "tipoNegocio": "RESTAURANTE",
  "municipio": "SAN_CARLOS",
  "direccion": "Cra 5 #12-34",
  "telefono": "+573007654321"
}

// Response 201 — misma estructura que productor, rol: COMPRADOR
```

#### POST `/api/auth/login`

```json
// Request
{
  "correo": "juan@finca.com",
  "contrasena": "SecurePass123!"
}

// Response 200 — misma estructura de token y usuario
```

---

### Perfiles de usuario — `/api/usuarios`

| Método | Endpoint | Descripción | Auth |
|---|---|---|---|
| `PATCH` | `/api/usuarios/perfil/productor` | Actualizar perfil del productor autenticado | PRODUCTOR |
| `PATCH` | `/api/usuarios/perfil/comprador` | Actualizar perfil del comprador autenticado | COMPRADOR |

#### PATCH `/api/usuarios/perfil/productor`

Solo se actualizan los campos enviados. Un campo ausente conserva su valor; `""` borra los campos opcionales.

```json
// Request (parcial)
{
  "telefono": "+573009876543",
  "canalWhatsappHabilitado": true,
  "canalLlamadaHabilitado": false
}
```

> **Regla de canales (US-17):** `canalWhatsappHabilitado: true` exige un `whatsapp` guardado y `canalLlamadaHabilitado: true` exige un `telefono` guardado; si no, responde 400 en `errores.whatsapp` o `errores.telefono`.

#### PATCH `/api/usuarios/perfil/comprador`

```json
// Request (parcial)
{
  "direccion": "Cra 5 #12-34",
  "municipio": "SAN_CARLOS",
  "horarioRecepcion": "Lunes a viernes 8am-5pm",
  "notasAcceso": "Tocar al portero",
  "telefono": "+573001112233",
  "canalWhatsappHabilitado": true
}
```

---

### Resumen de endpoints — Sprints 2 y 3

El detalle de cada endpoint (cuerpos, ejemplos y mensajes exactos) está en [`docs/API_CONTRATO_SPRINT3.md`](docs/API_CONTRATO_SPRINT3.md) y en Swagger. Si no coinciden, manda Swagger.

**Sprint 2 — Ofertas y catálogo**

| Método | Ruta | Rol | HU |
|---|---|---|---|
| `POST` | `/api/ofertas` | PRODUCTOR | US-06 Crear oferta |
| `GET` | `/api/ofertas/mis-ofertas` | PRODUCTOR | US-07 Mis ofertas (activas y agotadas) |
| `PUT` | `/api/ofertas/{id}` | PRODUCTOR dueño | US-08 Editar oferta |
| `PATCH` | `/api/ofertas/{id}/estado` | PRODUCTOR dueño | US-09 Marcar como agotada |
| `GET` | `/api/ofertas` | Público | US-11 / US-13 Catálogo paginado con filtros |
| `GET` | `/api/ofertas/{id}` | Público (los números solo para COMPRADOR) | US-12 Detalle de oferta |

**Sprint 3 — Pedidos, contactos y panel del productor**

| Método | Ruta | Rol | HU |
|---|---|---|---|
| `POST` | `/api/pedidos` | COMPRADOR | US-18 Checkout y pedido |
| `GET` | `/api/pedidos/{id}` | COMPRADOR dueño o PRODUCTOR de la oferta | US-18 Detalle del pedido |
| `GET` | `/api/pedidos/recibidos` | PRODUCTOR | US-23 Pedidos recibidos |
| `DELETE` | `/api/ofertas/{id}` | PRODUCTOR dueño | US-10 Eliminar oferta (bloqueado si tiene pedidos o contactos) |
| `POST` | `/api/contactos` | COMPRADOR | US-19 Registro del contacto (backend de US-14 WhatsApp y US-15 llamada) |
| `GET` | `/api/contactos/mios` | COMPRADOR | US-19 Historial de contactos |
| `PATCH` | `/api/usuarios/perfil/productor` | PRODUCTOR | US-17 Canales de contacto (endpoint de US-04) |

Todos los errores siguen el formato único de US-22. US-24 (imágenes) es solo del frontend.

### Valores válidos (enums)

| Campo | Valores aceptados |
|---|---|
| `municipio` | 24 municipios del Oriente Antioqueño y Medellín (`SONSON`, `SAN_CARLOS`, `RIONEGRO`, `MEDELLIN`…). Se aceptan el código o el nombre, sin distinguir mayúsculas ni tildes |
| `tipoNegocio` | `RESTAURANTE`, `TIENDA`, `MINIMERCADO`, `MAYORISTA` |
| `modalidadLogistica` (oferta) | `RECOGIDA_FINCA`, `DESPACHO_PRODUCTOR`, `AMBAS` |
| `modalidadEntrega` (pedido) | `RECOGIDA_FINCA`, `DESPACHO_PRODUCTOR` (nunca `AMBAS`) |
| `jornada` | `MANANA`, `TARDE` |
| `canal` | `WHATSAPP`, `LLAMADA` |

La lista completa de cada enum está en Swagger (`/v3/api-docs`).

### Códigos de respuesta

| Código | Significado |
|---|---|
| `200` | OK |
| `201` | Recurso creado |
| `204` | Recurso eliminado, sin cuerpo |
| `400` | Validación fallida — campo `errores` con detalle por campo |
| `401` | Token ausente, inválido, expirado o con firma incorrecta (sin cuerpo) |
| `403` | Rol insuficiente o recurso de otro usuario |
| `404` | Recurso no encontrado |
| `409` | Correo ya registrado |
| `422` | Regla de negocio: oferta agotada, canal no habilitado, oferta con pedidos o contactos |
| `500` | Error interno del servidor |

Formato de error (`ErrorResponse`, US-22). `errores` solo aparece cuando hay errores por campo:
```json
{
  "timestamp": "2026-11-05T20:28:30.108422793-05:00",
  "status": 400,
  "error": "Bad Request",
  "mensaje": "La solicitud contiene campos inválidos",
  "ruta": "/api/pedidos",
  "errores": {
    "direccionEntrega": "La dirección de entrega es obligatoria para continuar."
  }
}
```

---

## Seguridad

### Autenticación JWT

- Algoritmo: **HS256**
- Expiración por defecto: **24 horas** (`jwt.expiration=86400000`)
- El principal del token es el **UUID** del usuario (claim `uid`), no el correo
- Payload: `sub` (correo), `uid` (UUID), `rol`, `iat`, `exp` — sin datos sensibles
- Contraseñas: cifradas con **BCrypt costo 10** (OWASP 2021)
- Un token sin claim `uid` es rechazado con `401`

### Flujo de autorización

```
Request
  └── JwtAuthFilter
        ├── Sin token           → 401
        ├── Token mal formado   → 401
        ├── Firma incorrecta    → 401
        ├── Token expirado      → 401
        ├── Sin claim uid       → 401
        └── Token válido
              └── @PreAuthorize("hasRole('PRODUCTOR')") → 403 si rol no coincide
                    └── Lógica de negocio → 200 / 4xx
```

### Endpoints públicos

- `POST /api/auth/registro/productor`
- `POST /api/auth/registro/comprador`
- `POST /api/auth/login`
- `GET /api/ofertas` y `GET /api/ofertas/{id}` (catálogo y detalle)
- `GET /swagger-ui/**` y `GET /v3/api-docs/**`

Todo lo demás exige token; el rol se controla con `@PreAuthorize`.

---

## Testing

### Ejecutar tests

```bash
# Build completo: compila, corre todos los tests y verifica la cobertura con JaCoCo
./mvnw clean verify            # Linux/Mac
.\mvnw.cmd clean verify        # Windows
# Reporte en: target/site/jacoco/index.html

# Un test específico
./mvnw test -Dtest=PedidoServiceTest
```

Los tests no necesitan base de datos ni variables de entorno: usan H2 en memoria en modo PostgreSQL (`src/test/resources/application-test.properties`). Corren con `-Duser.timezone=UTC`, igual que Render: el `pom.xml` lo pasa a Surefire, así que `./mvnw clean verify` ya lo aplica. Si corres un test desde el IDE, agrega `-Duser.timezone=UTC` en las opciones de la JVM para reproducir las fechas de producción.

### Estado actual — Sprint 3

| Métrica | Valor |
|---|---|
| Total de tests | **367** |
| Resultado | 367 / 0 fallos — BUILD SUCCESS |
| Cobertura de líneas | **99,01 %** |
| Cobertura de ramas | **95,22 %** |

### Cobertura mínima exigida

El build falla si JaCoCo baja del 80 % (`pom.xml`). El criterio del equipo es no bajar del 95 % de líneas.

### Suites de tests

- Unitarios de servicio con Mockito (`*ServiceTest`): un test por regla y por escenario BDD.
- Integración por controlador (`*ControllerIntegrationTest`) con `@SpringBootTest`, MockMvc, H2 y **JWT reales** generados con `JwtService`: rol incorrecto (403), sin token (401), recurso ajeno (403) e inexistente (404).
- `SecurityIntegrationTest` y `CorsIntegrationTest`: cadena de seguridad, tokens inválidos y preflight desde el frontend.
- `FechasRespuestaIntegrationTest`: las fechas con hora salen con el offset de Colombia (`-05:00`).

### Smoke test contra producción

`scripts/smoke/smoke-sprint3.ps1` (Windows PowerShell 5.1) recorre los flujos del Sprint 3 contra el backend desplegado con usuarios temporales `smoke.<rol>.<sufijo>@campored.test`. Ante un 502, 503 o 504 reintenta una vez tras 5 s y lo marca con «reintento tras <código>».

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\smoke-sprint3.ps1
# Opcionales: -BaseUrl http://localhost:8080 · -TablaMd <ruta del .md de resultados> · -ConservarOfertasActivas
```

Código de salida: 0 si todo pasa, 1 si falla algún chequeo, 2 si se detuvo en un paso bloqueante. Después de cada corrida, en el SQL Editor de Supabase:

1. `scripts/smoke/verificar_esquema_sprint3.sql` (solo lectura: `CHECK`, FK, índices, RLS y filas smoke).
2. `scripts/smoke/limpiar_datos_smoke.sql` (borra solo lo de `smoke.%@campored.test`).

El resultado vigente está en [`docs/SMOKE_TEST_SPRINT3.md`](docs/SMOKE_TEST_SPRINT3.md).

### Scripts SQL

Producción corre con `ddl-auto=validate`: si el esquema no coincide con las entidades, el backend no arranca en Render. Por eso cada cambio de esquema es un script manual que se ejecuta en el SQL Editor de Supabase **antes** de mergear el código que lo usa.

| Script | Qué hace | Sprint |
|---|---|---|
| `src/main/resources/db/migration/US-04_canales_contacto_usuarios.sql` | Columnas de canales de contacto en `usuarios` | 1 |
| `sql/sprint2/US-06_crear_tabla_ofertas.sql` | Tabla `ofertas` con sus `CHECK` | 2 |
| `sql/sprint2/US-11-13_indices_catalogo_ofertas.sql` | Índices del catálogo | 2 |
| `sql/sprint3/US-18_crear_tabla_pedidos.sql` | Tabla `pedidos` | 3 |
| `sql/sprint3/US-19_crear_tabla_registros_contacto.sql` | Tabla `registros_contacto` | 3 |

Todos están aplicados en Supabase. Los de `scripts/` no cambian el esquema: verifican o limpian datos de prueba.

### Datos de prueba

En producción conviven dos tipos de datos de prueba, cada uno con su dominio de correo. **Ninguna de las dos limpiezas toca el otro tipo** (ni los usuarios reales).

| Tipo | Correos | Vida | Flujo |
|---|---|---|---|
| Smoke | `smoke.*@campored.test` | Temporales: los crea cada corrida del smoke test | `smoke-sprint3.ps1` → `verificar_esquema_sprint3.sql` → `limpiar_datos_smoke.sql` después de cada corrida |
| Demo | `*@campored.demo` | Permanentes, para la presentación | `limpiar_datos_demo.sql` → `seed-demo.mjs` → `verificar_datos_demo.sql` |

Los datos demo (`scripts/demo/`) son 8 productores de Sonsón y San Carlos, 6 compradores, 29 ofertas (3 agotadas), 9 pedidos y 12 contactos, definidos en `scripts/demo/datos-demo.json`. Se cargan solo por la API, para que pasen por BCrypt y por las validaciones:

```bash
# La contraseña de todos los usuarios demo sale de DEMO_PASSWORD; el script nunca la imprime
DEMO_PASSWORD='...' node scripts/demo/seed-demo.mjs          # bash
$env:DEMO_PASSWORD='...'; node scripts/demo/seed-demo.mjs    # PowerShell
```

El seed es idempotente (una segunda corrida no crea nada) y se niega a correr si encuentra datos demo de otra carga: en ese caso, primero `limpiar_datos_demo.sql`. `BASE_URL` cambia el backend (por defecto Render). Escribe los ids de la corrida y la lista de cuentas fuera del repo (`../seed-demo/resultado-demo.json` y la sección «Usuarios demo» de `../usuarios-prueba-render.md`). Al final, `verificar_datos_demo.sql` debe mostrar `ok = true` en todas las filas.

---

## Flujo de trabajo Git

### Ramas

| Rama | Propósito |
|---|---|
| `main` | Producción — recibe el merge desde `develop` en cada release |
| `develop` | Integración — todas las features se mergean aquí |
| `feat/US-XX-descripcion` | Rama de feature por historia de usuario o bloque |

### Ciclo por historia de usuario

```bash
git checkout develop
git pull --ff-only
git checkout -b feat/US-18-10-pedido-eliminar

# Desarrollar + tests (./mvnw clean verify en verde)

git add src/...
git commit -m "feat(US-18): crear entidad Pedido y endpoint POST /api/pedidos AB#49"
git push -u origin feat/US-18-10-pedido-eliminar

# Abrir PR a develop en GitHub → revisión → merge commit (no squash)
# Cerrar HU en Azure (status → Done)
```

**Claude Code nunca hace `git push` automáticamente.** El desarrollador revisa y ejecuta todos los comandos Git.

---

## Integración con Azure DevOps

Tablero: [dev.azure.com/campoRed/CampoRed_ProyectoIntegrador2](https://dev.azure.com/campoRed/CampoRed_ProyectoIntegrador2)

### Formato de commit obligatorio

```
feat(US-XX): descripción concisa en imperativo y minúsculas AB#<id>
```

El `AB#<id>` enlaza el commit con la HU de Azure Boards (por ejemplo, US-18 es `AB#49`).

### Prefijos de commit

| Prefijo | Uso |
|---|---|
| `feat` | Nueva funcionalidad |
| `fix` | Corrección de bug |
| `test` | Agregar o corregir tests |
| `refactor` | Refactorización sin cambio de comportamiento |
| `docs` | Documentación |
| `chore` | Tareas de mantenimiento |

---

## Despliegue

### Entornos

| Entorno | Plataforma | URL |
|---|---|---|
| Producción API | Render (plan Free, Docker) | `https://campored-backend.onrender.com` |
| Swagger UI | Render | `https://campored-backend.onrender.com/swagger-ui.html` |
| Producción frontend | Vercel | `https://campored-frontend.vercel.app` · `https://campored.rollerandres.dev` |
| Base de datos | Supabase (us-east-2) | Session Pooler |

### Conexión a Supabase

Usar siempre el **Session Pooler** (la conexión directa es solo IPv6):

```
Host:     aws-0-us-east-2.pooler.supabase.com
Port:     5432
User:     postgres.xdmodvpumekckgwdvgln
Database: postgres
```

### Deploy automático

Render construye la imagen con el `Dockerfile` y despliega solo al detectar un push en la rama configurada: `develop` hasta el release v1.0.0 y `main` después (ver [`docs/RELEASE_v1.0.0.md`](docs/RELEASE_v1.0.0.md)). Las variables de entorno se configuran en **Environment** del servicio (ver [Variables de entorno](#variables-de-entorno)).

El perfil de producción usa `spring.jpa.hibernate.ddl-auto=validate` — Hibernate **no** crea ni modifica tablas. Todo cambio de esquema se aplica a mano en Supabase **antes** del merge; si el código llega primero, el backend no arranca en Render.

### Migraciones aplicadas en Supabase

| Script | Descripción | Sprint |
|---|---|---|
| Schema inicial | Tablas `usuarios`, `fincas`; ENUMs `rol_usuario`, `municipio_enum`, `tipo_negocio_enum` | Sprint 1 |
| Tabla `negocios` | Columnas: `nombre_negocio`, `tipo_negocio`, `direccion`, `municipio`, `horario_recepcion`, `notas_acceso`, `usuario_id` | Sprint 1 |
| `src/main/resources/db/migration/US-04_canales_contacto_usuarios.sql` | Columnas `canal_whatsapp_habilitado` y `canal_llamada_habilitado` en `usuarios` | Sprint 1 |
| `sql/sprint2/` y `sql/sprint3/` | `ofertas`, índices del catálogo, `pedidos` y `registros_contacto` (ver [Scripts SQL](#scripts-sql)) | Sprints 2 y 3 |

---

## Equipo

| Nombre | Rol | Cédula |
|---|---|---|
| Cristian David Diez López | Backend — Spring Boot, arquitectura de datos, API REST | 1036967493 |
| Roller Andres Hernández López | Frontend — React, diseño UX/UI, testing | 1001226439 |

**Tutora:** Sandra Patricia Zabala Orrego
**Asignatura:** Proyecto Integrador II — Universidad de Antioquia, Facultad de Ingeniería
**Semestre:** 2026-2

---

*Sprint 3 completado — pedidos, historial de contactos, panel del productor y eliminación de ofertas (US-10, US-14, US-15, US-17, US-18, US-19, US-22, US-23, US-25) — desplegado en Render*