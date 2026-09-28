# Contribuir

## Flujo de trabajo

1. Actualizar `main`.
2. Crear una rama con nombre descriptivo, por ejemplo `feature/evaluaciones`.
3. Implementar un cambio acotado y respaldarlo con pruebas.
4. Ejecutar `make test`.
5. Crear un pull request explicando qué cambia y cómo se verificó.

No se deben subir directamente a `main` cambios funcionales grandes.

## Convenciones

- Código y nombres técnicos en inglés.
- Textos de interfaz y documentación académica en español.
- Clases en `PascalCase`; métodos y variables en `camelCase`.
- Una clase pública por archivo.
- Las reglas del negocio pertenecen a `domain`, no a los controladores.
- Los cambios del esquema se realizan mediante una migración Flyway nueva; no se edita una migración ya compartida.
- Nunca se versionan contraseñas, `.env` ni credenciales reales.

## Commits

Usar mensajes breves en modo imperativo:

```text
Add weighted compatibility strategy
Fix duplicate player validation
Document evaluation lifecycle
```

## Pull requests

Cada PR debe incluir:

- Problema resuelto.
- Decisión de diseño relevante.
- Evidencia de pruebas.
- Capturas si modifica la interfaz.
- Actualización del `SPEC.md` cuando cambie el alcance acordado.

