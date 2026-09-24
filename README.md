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

Crea `src/main/resources/application-local.properties` (este archivo está en `.gitignore`, no se sube al repo):

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

> **Nota:** Si usas la instancia de Supabase del equipo, solicita las credenciales al equipo. La conexión directa es solo IPv6; usa siempre el **Session Pooler** (`aws-0-us-east-2.pooler.supabase.com:5432`).

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

En producción (Railway / Render) estas variables se configuran como variables de entorno del servicio. **Nunca se suben al repositorio.**

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
│   │   ├── config/             # SecurityConfig, JwtAuthFilter, CorsConfig, SwaggerConfig
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
├── id              UUID (PK)
├── correo          VARCHAR UNIQUE NOT NULL
├── contrasena_hash VARCHAR NOT NULL
├── nombre          VARCHAR NOT NULL
├── telefono        VARCHAR (opcional)
├── rol             ENUM {PRODUCTOR, COMPRADOR}
├── canal_whatsapp_habilitado  BOOLEAN DEFAULT false
├── canal_llamada_habilitado   BOOLEAN DEFAULT false
├── created_at      TIMESTAMP
│
├── finca           (solo si rol = PRODUCTOR)
└── negocio         (solo si rol = COMPRADOR)

Finca
├── id              UUID (PK)
├── usuario_id      FK → Usuario
├── nombre_finca    VARCHAR NOT NULL
├── municipio       ENUM {SONSON, SAN_CARLOS}
└── vereda          VARCHAR

Negocio
├── id              UUID (PK)
├── usuario_id      FK → Usuario
├── nombre_negocio  VARCHAR NOT NULL
├── tipo_negocio    ENUM {RESTAURANTE, TIENDA, MINIMERCADO, MAYORISTA}
├── municipio       ENUM
├── direccion       VARCHAR NOT NULL
├── horario         VARCHAR
└── notas_acceso    VARCHAR
```

**Regla:** un usuario tiene `Finca` **o** `Negocio`, nunca ambos.

---

## API — Endpoints implementados

### Autenticación — `/api/auth`

| Método | Endpoint | Descripción | Auth |
|---|---|---|---|
| `POST` | `/api/auth/registro/productor` | Registrar productor con datos de finca | ❌ Público |
| `POST` | `/api/auth/registro/comprador` | Registrar comprador con datos de negocio | ❌ Público |
| `POST` | `/api/auth/login` | Autenticar y obtener JWT | ❌ Público |

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
  "id": "uuid",
  "correo": "juan@finca.com",
  "nombre": "Juan Pérez",
  "rol": "PRODUCTOR",
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

#### POST `/api/auth/login`

```json
// Request
{
  "correo": "juan@finca.com",
  "contrasena": "SecurePass123!"
}

// Response 200
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "id": "uuid",
  "correo": "juan@finca.com",
  "rol": "PRODUCTOR"
}
```

---

### Perfiles de usuario — `/api/usuarios`

| Método | Endpoint | Descripción | Auth |
|---|---|---|---|
| `PATCH` | `/api/usuarios/perfil/productor` | Actualizar perfil del productor autenticado | ✅ PRODUCTOR |
| `PATCH` | `/api/usuarios/perfil/comprador` | Actualizar perfil del comprador autenticado | ✅ COMPRADOR |

#### PATCH `/api/usuarios/perfil/productor`

Solo se actualizan los campos enviados. Un campo ausente conserva su valor; una cadena vacía `""` borra los campos opcionales.

```json
// Request (parcial — solo los campos a actualizar)
{
  "telefono": "+573009876543",
  "canalWhatsappHabilitado": true,
  "canalLlamadaHabilitado": false
}

// Response 200
{
  "id": "uuid",
  "nombre": "Juan Pérez",
  "correo": "juan@finca.com",
  "telefono": "+573009876543",
  "canalWhatsappHabilitado": true,
  "canalLlamadaHabilitado": false,
  "rol": "PRODUCTOR"
}
```

> **Regla de canales:** si `canalWhatsappHabilitado` o `canalLlamadaHabilitado` es `true`, el campo `telefono` debe estar presente en el perfil. La validación opera sobre el estado final del perfil, no solo sobre el body enviado.

#### PATCH `/api/usuarios/perfil/comprador`

```json
// Request
{
  "direccion": "Cra 5 #12-34",
  "municipio": "SAN_CARLOS",
  "horario": "Lunes a viernes 8am–5pm",
  "notas_acceso": "Tocar al portero",
  "telefono": "+573001112233",
  "canalWhatsappHabilitado": true
}
```

---

### Códigos de respuesta generales

| Código | Significado |
|---|---|
| `200` | OK |
| `201` | Recurso creado |
| `400` | Validación fallida — el body incluye `errores` con detalle por campo |
| `401` | Token ausente, inválido, expirado o con firma incorrecta |
| `403` | Token válido pero rol insuficiente |
| `404` | Recurso no encontrado |
| `409` | Conflicto — correo ya registrado |
| `500` | Error interno del servidor |

---

## Seguridad

### Autenticación JWT

- Algoritmo: **HS256**
- Expiración por defecto: **24 horas** (`jwt.expiration=86400000`)
- El principal del token es el **UUID** del usuario (claim `uid`), no el correo
- Payload del token: `sub` (correo), `uid` (UUID), `rol`, `iat`, `exp` — sin datos sensibles
- Contraseñas: cifradas con **BCrypt costo 10** (conforme a OWASP 2021)
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

Todo lo demás requiere JWT válido en el header `Authorization: Bearer <token>`.

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
| Resultado | ✅ 78 / 0 — BUILD SUCCESS |
| Cobertura de líneas | **96.4 %** |
| Cobertura service | 100 % |
| Cobertura controller | 100 % |
| Cobertura config / util | 100 % |

### Cobertura mínima exigida (CLAUDE.md)

| Capa | Mínimo |
|---|---|
| Service | 80 % |
| Controller | 70 % |

### Qué se prueba

- `UsuarioServiceTest` — registro de productor/comprador, actualización de perfil, validación de canales de contacto, correo duplicado, municipio inválido
- `JwtServiceTest` — generación del token, claims (`sub`, `uid`, `rol`), expiración de 24 h, BCrypt costo ≥ 10
- `UsuarioControllerIntegrationTest` — flujo completo HTTP de perfiles PATCH, control por rol
- `SecurityIntegrationTest` — 401 sin token, token mal formado, esquema Basic, firma de otra clave, token expirado; 403 por rol incorrecto

> Los tests del perfil de producción usan `@ActiveProfiles("test")` con `jwt.expiration=3600000` (1 h). Un test independiente en `JwtServiceTest` verifica que el valor por defecto del perfil de producción siga siendo 24 h.

---

## Flujo de trabajo Git

### Ramas

| Rama | Propósito |
|---|---|
| `main` | Producción — solo recibe merge desde `develop` al cierre de cada sprint |
| `develop` | Integración — todas las features se mergean aquí |
| `US-XX-descripcion` | Rama de feature por historia de usuario |

### Ciclo por historia de usuario

```bash
# 1. Partir de develop actualizado
git checkout develop
git pull origin develop

# 2. Crear rama de feature
git checkout -b US-06-publicar-oferta

# 3. Desarrollar + tests

# 4. Commit (propuesto por Claude Code, ejecutado por el desarrollador)
git add src/...
git commit -m "feat(US-06): ... Fixes: #US-06"

# 5. Push y abrir PR → develop en GitHub
git push origin US-06-publicar-oferta

# 6. Code review → merge
# 7. Cerrar HU en Azure (status → Done)
```

**Claude Code nunca hace `git push` automáticamente.** El desarrollador revisa y ejecuta todos los comandos Git.

---

## Integración con Azure DevOps

El tablero del proyecto está en:
[dev.azure.com/campoRed/CampoRed_ProyectoIntegrador2](https://dev.azure.com/campoRed/CampoRed_ProyectoIntegrador2)

### Formato de commit obligatorio

```
feat(US-XX): Descripción concisa en imperativo

- Detalle técnico 1
- Detalle técnico 2
- Tests: N en verde, cobertura X%

Fixes: #US-XX
```

La línea `Fixes: #US-XX` vincula el commit a la historia de usuario en Azure. Al mergear el PR a `develop`, Azure detecta el cierre automáticamente.

### Prefijos de commit

| Prefijo | Uso |
|---|---|
| `feat` | Nueva funcionalidad |
| `fix` | Corrección de bug |
| `test` | Agregar o corregir tests |
| `refactor` | Refactorización sin cambio de comportamiento |
| `docs` | Documentación |
| `chore` | Tareas de mantenimiento (deps, gitignore, config) |

---

## Despliegue

### Producción (Supabase + Railway/Render)

La BD en producción corre en **Supabase** (region `us-east-2`). Usar siempre el **Session Pooler** para la conexión:

```
Host:     aws-0-us-east-2.pooler.supabase.com
Port:     5432
User:     postgres.xdmodvpumekckgwdvgln
Database: postgres
```

La conexión directa de Supabase es solo IPv6 y no funciona en la red del equipo.

El perfil de producción usa `spring.jpa.hibernate.ddl-auto=validate`. Las migraciones de esquema se aplican **manualmente** en el SQL Editor de Supabase antes de cada despliegue, usando los scripts en `src/main/resources/db/migration/`.

### Migraciones aplicadas

| Script | Descripción | Aplicado |
|---|---|---|
| `US-04_canales_contacto_usuarios.sql` | Agrega `canal_whatsapp_habilitado` y `canal_llamada_habilitado` a `usuarios` | ✅ Sprint 1 |

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

*Sprint actual: Sprint 1 completado — autenticación JWT + perfiles sectorizados (US-01 al US-05, US-21)*
