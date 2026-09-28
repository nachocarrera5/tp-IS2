# ADR 0001: monolito modular

- Estado: aceptado
- Fecha: 2026-09-28

## Contexto

El MVP será desarrollado por un equipo pequeño, se ejecutará localmente y debe demostrar diseño orientado a objetos sin sumar complejidad operativa innecesaria.

## Decisión

Implementar una aplicación web monolítica con Spring Boot y módulos delimitados por paquetes. PostgreSQL será la única base de datos y Thymeleaf renderizará la interfaz en el servidor.

## Consecuencias

Ventajas:

- Un único proceso para desarrollar, probar y ejecutar.
- Transacciones simples.
- Menor costo de infraestructura.
- Adecuado para el tamaño del equipo y del MVP.

Costos:

- Los límites modulares dependen de disciplina interna.
- Escalar módulos de forma independiente no será inmediato.
- Si el producto creciera a múltiples clubes, la arquitectura debería reevaluarse.

