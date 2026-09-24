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
- [Flujo de trabajo Git](#flujo-de-trabajo-git)
- [Integración con Azure DevOps](#integración-con-azure-devops)
- [Despliegue](#despliegue)
- [Equipo](#equipo)

---

## Descripción

CampoRed conecta pequeños productores agrícolas de **Sonsón** y **San Carlos** (Antioquia) con compradores comerciales (restaurantes, tiendas y mayoristas) del Oriente Antioqueño y el Área Metropolitana de Medellín, eliminando intermediarios en la cadena de distribución local.

Este repositorio contiene el backend del MVP: una API REST modular construida con Spring Boot 3 sobre PostgreSQL, con autenticación JWT, control de acceso por roles (PRODUCTOR / COMPRADOR) y cobertura de tests del **96.4 %** de líneas.

**Producción:**
- **API:** `https://campored-backend-production.up.railway.app/api`
- **Swagger UI:** `https://campored-backend-production.up.railway.app/swagger-ui.html`

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

# JPA — crea/actualiza tablas automáticamente en local
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.profiles.active=local
```

> **Nota:** Si usas la instancia de Supabase del equipo, solicita las credenciales. La conexión directa es solo IPv6; usa siempre el **Session Pooler** (`aws-0-us-east-2.pooler.supabase.com:5432`).

---

## Variables de entorno

| Variable | Descripción | Ejemplo |
|---|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC de la base de datos | `jdbc:postgresql://host:5432/campored_db` |
| `SPRING_DATASOURCE_USERNAME` | Usuario de la BD | `postgres.xxxxxxxx` |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña de la BD | `***` |
| `JWT_SECRET` | Clave para firmar tokens (mínimo 256 bits) | `your-super-secret-key...` |
| `JWT_EXPIRATION` | Duración del token en ms (por defecto 24 h) | `86400000` |
| `SPRING_PROFILES_ACTIVE` | Perfil activo | `local` / `prod` |

En producción (Railway) estas variables se configuran como variables de entorno del servicio. **Nunca se suben al repositorio.**

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
│   │   ├── config/             # SecurityConfig, JwtAuthFilter, CorsConfig
│   │   ├── controller/         # REST Controllers (AuthController, UsuarioController)
│   │   ├── dto/                # Request/Response objects
│   │   │   └── validation/     # Anotaciones y validadores personalizados
│   │   ├── entity/             # Entidades JPA (Usuario, Finca, Negocio)
│   │   ├── exception/          # GlobalExceptionHandler, excepciones personalizadas
│   │   ├── repository/         # Interfaces JpaRepository
│   │   ├── service/            # Lógica de negocio (UsuarioService, JwtService)
│   │   └── util/               # Mappers (UsuarioMapper)
│   └── resources/
│       ├── application.properties          # Config base (compartida)
│       ├── application-local.properties    # Config local (gitignored)
│       └── db/migration/                   # Scripts SQL manuales para Supabase
│
└── test/
    └── java/com/campored/backend/
        ├── config/             # SecurityIntegrationTest
        ├── controller/         # UsuarioControllerIntegrationTest
        └── service/            # UsuarioServiceTest, JwtServiceTest
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

> **Regla de canales:** si `canalWhatsappHabilitado` o `canalLlamadaHabilitado` es `true`, el campo `telefono` debe estar presente en el perfil.

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

### Valores válidos (enums)

| Campo | Valores aceptados |
|---|---|
| `municipio` | `SONSON`, `SAN_CARLOS` |
| `tipoNegocio` | `RESTAURANTE`, `TIENDA`, `MINIMERCADO`, `MAYORISTA` |

### Códigos de respuesta

| Código | Significado |
|---|---|
| `200` | OK |
| `201` | Recurso creado |
| `400` | Validación fallida — campo `errores` con detalle por campo |
| `401` | Token ausente, inválido, expirado o con firma incorrecta |
| `403` | Token válido pero rol insuficiente |
| `404` | Recurso no encontrado |
| `409` | Correo ya registrado |
| `500` | Error interno del servidor |

Formato del `400`:
```json
{
  "errores": {
    "correo": "El correo ya está registrado",
    "contrasena": "Mínimo 8 caracteres"
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
- `GET /swagger-ui/**` y `GET /v3/api-docs/**`

---

## Testing

### Ejecutar tests

```bash
# Todos los tests
.\mvnw.cmd clean test

# Un test específico
.\mvnw.cmd test -Dtest=UsuarioServiceTest

# Con reporte de cobertura JaCoCo
.\mvnw.cmd clean verify
# Reporte en: target/site/jacoco/index.html
```

### Estado actual — Sprint 1

| Métrica | Valor |
|---|---|
| Total de tests | **78** |
| Resultado | 78 / 0 — BUILD SUCCESS |
| Cobertura de líneas | **96.4 %** |
| Cobertura service | 100 % |
| Cobertura controller | 100 % |
| Cobertura config / util | 100 % |

### Cobertura mínima exigida

| Capa | Mínimo |
|---|---|
| Service | 80 % |
| Controller | 70 % |

### Suites de tests

- `UsuarioServiceTest` — registro productor/comprador, actualización de perfil, validación de canales, correo duplicado, municipio inválido
- `JwtServiceTest` — generación del token, claims (`sub`, `uid`, `rol`), expiración 24 h, BCrypt costo >= 10
- `UsuarioControllerIntegrationTest` — flujo HTTP completo de perfiles PATCH, control por rol
- `SecurityIntegrationTest` — 401 sin token, token mal formado, esquema Basic, firma incorrecta, token expirado; 403 por rol incorrecto

---

## Flujo de trabajo Git

### Ramas

| Rama | Propósito |
|---|---|
| `main` | Producción — solo recibe merge desde `develop` al cierre de sprint |
| `develop` | Integración — todas las features se mergean aquí |
| `US-XX-descripcion` | Rama de feature por historia de usuario |

### Ciclo por historia de usuario

```bash
git checkout develop
git pull origin develop
git checkout -b US-06-publicar-oferta

# Desarrollar + tests

git add src/...
git commit -m "feat(US-06): ... Fixes: #US-06"
git push origin US-06-publicar-oferta

# Abrir PR a develop en GitHub
# Code review → merge
# Cerrar HU en Azure (status → Done)
```

**Claude Code nunca hace `git push` automáticamente.** El desarrollador revisa y ejecuta todos los comandos Git.

---

## Integración con Azure DevOps

Tablero: [dev.azure.com/campoRed/CampoRed_ProyectoIntegrador2](https://dev.azure.com/campoRed/CampoRed_ProyectoIntegrador2)

### Formato de commit obligatorio

```
feat(US-XX): Descripción concisa en imperativo

- Detalle técnico 1
- Detalle técnico 2
- Tests: N en verde, cobertura X%

Fixes: #US-XX
```

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
| Producción API | Railway | `https://campored-backend-production.up.railway.app` |
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

Railway despliega automáticamente al detectar un push a la rama `develop`. Tiempo estimado: ~2 minutos.

El perfil de producción usa `spring.jpa.hibernate.ddl-auto=validate` — Hibernate **no** crea ni modifica tablas. Toda migración de esquema debe aplicarse manualmente en Supabase antes del deploy.

### Migraciones aplicadas en Supabase

Los scripts están en `src/main/resources/db/migration/`.

| Script | Descripción | Sprint |
|---|---|---|
| Schema inicial | Tablas `usuarios`, `fincas`; ENUMs `rol_usuario`, `municipio_enum`, `tipo_negocio_enum` | Sprint 1 |
| Tabla `negocios` | Columnas: `nombre_negocio`, `tipo_negocio`, `direccion`, `municipio`, `horario_recepcion`, `notas_acceso`, `usuario_id` | Sprint 1 |
| `US-04_canales_contacto_usuarios.sql` | Columnas `canal_whatsapp_habilitado` y `canal_llamada_habilitado` en `usuarios` | Sprint 1 |

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

*Sprint 1 completado — autenticación JWT + perfiles sectorizados (US-01, US-02, US-03, US-04, US-05, US-21) — desplegado en Railway*