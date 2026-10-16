# Índice de evidencias

Los JSON originales se conservan en esta carpeta, sin moverlos ni modificarlos.
[originales/README.md](originales/README.md) los clasifica; el manifiesto generado
`originales/manifest-sha256.json` permite comprobar su integridad.

```text
evidencias/
  *.json                      Resúmenes originales de k6
  originales/                 Catálogo y hashes de los originales
  derivadas/comparativa.json   Métricas extraídas; no mediciones nuevas
  derivadas/graficos/          PNG y SVG basados solo en los dos JSON de actividades
  cloudwatch/                 Exportaciones/capturas sanitizadas pendientes
  plantillas/                 Registro de recuperación todavía sin completar
```

Los gráficos no incluyen puntos simulados de 100/250/500 VUs, ni una curva de
recuperación sin serie temporal. La cuota de 10, los 40 throttles y el máximo de
concurrencia de 10 se documentan como confirmados por el usuario en CloudWatch;
el [PDF definitivo](../informe/Informe%20De%20Pruebas%20Paralelo%201-21-2841.pdf)
incluye capturas de CloudWatch, cuota y consola. No hay exportaciones históricas
independientes adjuntas para calcular recuperación o reconstruir estados HTTP.

El generador vuelve a crear únicamente el informe y sus derivados. No modifica
los originales y no ejecuta tráfico. Preservar futuros originales por separado,
con ventana, zona horaria, unidad, estadística y recurso; revisar su contenido
antes de publicarlos. Los logs `.log` se excluyen de Git por defecto.
