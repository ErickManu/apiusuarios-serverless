# Escenarios preparados, pendientes por cuota

| Script | VUs configurados | Duración configurada | Estado | Medición |
| --- | ---: | --- | --- | --- |
| `actividad2-100vus.js` | 100 | 2 minutos | PENDIENTE / BLOQUEADO | No realizada |
| `actividad2-250vus.js` | 250 | 2 minutos | PENDIENTE / BLOQUEADO | No realizada |
| `actividad2-500vus.js` | 500 | 2 minutos | PENDIENTE / BLOQUEADO | No realizada |

Cuota regional confirmada por el usuario: 10 ejecuciones simultáneas en us-east-2.
No se solicita ampliación. No se ofrecen comandos de ejecución de estos escenarios.
Los archivos declaran el escenario constant-vus de dos minutos y un diseño de
máximo un intento por VU. Esos presupuestos son configuración, no mediciones.

El bloqueo lanza un error incondicional al inicializar cada script y se repite
antes de cualquier llamada HTTP en el helper. No depende de `setup`, del JWT ni
de una variable de entorno. Cambiar --vus, --iterations o --no-setup no lo elude.
Se comprobó con mocks locales; no se ejecutó k6. El helper tampoco tiene una
función default que pueda ejecutar directamente k6.

Para una fase futura haría falta revisar expresamente el código y la estrategia
con la cuota existente. No retirar los bloqueos como parte de esta entrega.
Una rampa con concurrencia efectiva dentro del presupuesto regional sería otra
prueba y no una medición ya realizada de 100/250/500 usuarios simultáneos.

Los ensayos previos fueron controlados, no carga continua. No extrapolar
porcentajes de fallos a partir de la cuota: no existen esas mediciones.
