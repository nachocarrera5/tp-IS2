# ScoutMatch

MVP web para buscar y evaluar jugadores de fútbol a partir de necesidades deportivas. El proyecto corresponde al trabajo final de Ingeniería de Software 2.

El sistema construye perfiles desde evaluaciones de scouts y calcula un ranking explicable mediante criterios ponderados. La decisión final permanece en manos del cuerpo técnico.

## Estado

MVP funcional. Incluye gestión de jugadores, importación CSV controlada, evaluaciones versionadas,
necesidades deportivas, ranking explicable, seguimiento de candidatos, seguridad por roles,
pruebas automatizadas, contenedores y CI.

La especificación funcional acordada está en [SPEC.md](SPEC.md). Antes de implementar una funcionalidad, el equipo debe verificar que sea consistente con ese documento.

## Stack

- Java 21
- Spring Boot 4.1
- Spring MVC + Thymeleaf
- Spring Security
- Spring JDBC
- PostgreSQL + Flyway
- Maven
- Thymeleaf + CSS propio
- JUnit 5

## Inicio rápido con Docker

Requisitos: Docker Desktop o Docker Engine con Compose.

```bash
cp .env.example .env
docker compose up --build
```

Abrir [http://localhost:8080](http://localhost:8080).

Usuarios de demostración:

| Rol | Usuario | Contraseña |
|---|---|---|
| Director técnico | `dt@demo.local` | `demo1234` |
| Coordinador | `coordinador@demo.local` | `demo1234` |
| Scout | `scout@demo.local` | `demo1234` |

Las cuentas son únicamente para desarrollo local. También se puede registrar una cuenta nueva desde la pantalla de acceso y elegir uno de los tres roles del MVP.

## Flujos disponibles

- El coordinador crea y edita jugadores, importa CSV, define necesidades con filtros y criterios,
  ejecuta rankings y administra el seguimiento de candidatos.
- El scout carga borradores, los confirma y corrige una evaluación mediante una nueva versión.
- El director técnico consulta jugadores, rankings, explicaciones y candidatos sin modificar datos.
- El perfil de cada jugador promedia primero las observaciones de cada scout y luego los promedios
  entre scouts, para evitar que quien carga más informes tenga mayor peso.

## Comandos útiles

```bash
make up        # inicia aplicación y PostgreSQL
make down      # detiene los contenedores
make logs      # sigue los logs de la aplicación
make test      # ejecuta las pruebas dentro de Maven/Java 21
make package   # genera el JAR
make db        # inicia únicamente PostgreSQL
```

También puede ejecutarse sin Docker si están instalados Java 21, Maven 3.9+ y PostgreSQL:

```bash
mvn spring-boot:run
```

## Estructura

```text
src/main/java/ar/edu/is2/scouting/
├── config/            configuración transversal
├── domain/            modelo y reglas del negocio
│   ├── player/
│   ├── search/
│   └── user/
└── web/               controladores MVC

src/main/resources/
├── db/migration/      migraciones Flyway
├── static/            estilos y recursos públicos
└── templates/         vistas Thymeleaf
```

La aplicación se plantea como un monolito modular. Las reglas del dominio no deben depender de controladores, vistas ni repositorios.

## Documentación

- [Especificación funcional](SPEC.md)
- [Arquitectura](docs/architecture.md)
- [ADR 0001: monolito modular](docs/adr/0001-modular-monolith.md)
- [Guía de contribución](CONTRIBUTING.md)

## Calidad

Cada push y pull request ejecuta compilación, pruebas y empaquetado en GitHub Actions. JaCoCo genera el reporte de cobertura en `target/site/jacoco/`.

## Licencia

Proyecto académico. No se concede una licencia de uso comercial por el momento.
