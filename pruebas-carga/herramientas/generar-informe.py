"""Genera derivados de JSON locales. No ejecuta k6, SDK AWS ni conexiones."""
import base64
import hashlib
import html
import json
import math
import os
from pathlib import Path

BASE = Path(__file__).resolve().parents[1]
EVIDENCIAS = BASE / 'evidencias'
DERIVADAS = EVIDENCIAS / 'derivadas'
GRAFICOS = DERIVADAS / 'graficos'
INFORME = BASE / 'informe'
os.environ['MPLCONFIGDIR'] = str(BASE / 'herramientas' / '.cache' / 'matplotlib')

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

FUENTES = [
    ('Actividad 1', 'actividad1-20261016-005817.json', 10, 60, 3),
    ('Actividad 2 · 50 VUs', 'actividad2-50vus-20261016-014407.json', 50, 120, 1),
]


def metrica(reporte, nombre):
    datos = reporte['metrics'][nombre]
    return datos.get('values', datos)


def numero(valor):
    if isinstance(valor, bool) or not isinstance(valor, (int, float)) or not math.isfinite(valor):
        raise ValueError('Una métrica requerida no es numérica y finita.')
    return valor


def formato(valor, decimales=2):
    return f'{valor:,.{decimales}f}'.replace(',', '_').replace('.', ',').replace('_', ' ')


def cargar_resultados():
    resultados = []
    for actividad, archivo, vus, duracion, cupo in FUENTES:
        ruta = EVIDENCIAS / archivo
        reporte = json.loads(ruta.read_text(encoding='utf-8-sig'))
        http = metrica(reporte, 'http_reqs')
        checks = metrica(reporte, 'checks')
        fallos = metrica(reporte, 'http_req_failed')
        latencia = metrica(reporte, 'http_req_duration')
        aceptadas = metrica(reporte, 'http_req_duration{expected_response:true}')
        n = numero(http['count'])
        correctas = numero(checks['passes'])
        incorrectas = numero(checks['fails'])
        if n != correctas + incorrectas or n <= 0 or n > vus * cupo:
            raise ValueError(f'Conteos incompatibles en {archivo}.')
        if not math.isclose(numero(fallos['value']), incorrectas / n, abs_tol=1e-9):
            raise ValueError(f'El porcentaje de errores no coincide en {archivo}.')
        if not math.isclose(numero(checks['value']), correctas / n, abs_tol=1e-9):
            raise ValueError(f'El porcentaje de checks no coincide en {archivo}.')
        resumen = {k: numero(latencia[k]) for k in ('avg', 'min', 'max', 'p(95)', 'p(99)')}
        if not resumen['min'] <= resumen['avg'] <= resumen['max']:
            raise ValueError(f'Latencias incompatibles en {archivo}.')
        fallida_media = None
        if incorrectas:
            fallida_media = (n * resumen['avg'] - correctas * numero(aceptadas['avg'])) / incorrectas
        resultados.append({
            'actividad': actividad,
            'fuente_json': f'evidencias/{archivo}',
            'sha256': hashlib.sha256(ruta.read_bytes()).hexdigest(),
            'configuracion_del_guion_no_medicion': {
                'vus': vus, 'duracion_s': duracion, 'max_post_por_vu': cupo,
            },
            'mediciones_json': {
                'solicitudes': n, 'checks_202_correctos': correctas,
                'checks_fallidos': incorrectas,
                'errores_porcentaje': numero(fallos['value']) * 100,
                'throughput_post_s': numero(http['rate']),
                'latencia_global_ms': resumen,
                'latencia_aceptadas_media_ms': numero(aceptadas['avg']),
                'vus_gauge': metrica(reporte, 'vus'),
            },
            'calculos_derivados_no_muestras_individuales': {
                'latencia_fallidas_media_ms': fallida_media,
            },
        })
    return resultados


def tabla_resultados(resultados):
    filas = []
    for r in resultados:
        m = r['mediciones_json']
        l = m['latencia_global_ms']
        filas.append([
            r['actividad'], str(m['solicitudes']), str(m['checks_202_correctos']),
            str(m['checks_fallidos']), formato(m['errores_porcentaje']) + ' %',
            formato(m['throughput_post_s'], 4),
            *[formato(l[k]) for k in ('avg', 'min', 'max', 'p(95)', 'p(99)')],
            formato(m['latencia_aceptadas_media_ms']),
        ])
    return filas


CABECERAS = ['Actividad', 'POST', 'Aceptadas', 'Fallidas', 'Errores', 'POST/s',
             'Media ms', 'Mín. ms', 'Máx. ms', 'P95 ms', 'P99 ms', 'Media aceptadas ms']


def tabla_md(cabeceras, filas):
    return '\n'.join([
        '| ' + ' | '.join(cabeceras) + ' |',
        '| ' + ' | '.join('---' for _ in cabeceras) + ' |',
        *['| ' + ' | '.join(fila) + ' |' for fila in filas],
    ])


def guardar_figura(figura, nombre):
    figura.savefig(GRAFICOS / f'{nombre}.png', dpi=170, bbox_inches='tight', facecolor='white')
    figura.savefig(GRAFICOS / f'{nombre}.svg', bbox_inches='tight', metadata={'Date': None})
    plt.close(figura)


def generar_graficos(resultados):
    plt.rcParams.update({
        'font.family': 'DejaVu Sans', 'font.size': 11, 'axes.spines.top': False,
        'axes.spines.right': False, 'axes.titleweight': 'bold',
        'svg.hashsalt': 'practica-k6-datos-reales',
    })
    nombres = ['A1 · 10 VUs\n30 POST', 'A2 · 50 VUs\n50 POST']
    mediciones = [r['mediciones_json'] for r in resultados]
    fig, axes = plt.subplots(1, 2, figsize=(12, 5.1), gridspec_kw={'width_ratios': [1.25, 1]})
    x = [0, 1]
    aceptadas = [m['checks_202_correctos'] for m in mediciones]
    fallidas = [m['checks_fallidos'] for m in mediciones]
    axes[0].bar(x, aceptadas, color='#15806b', label='Aceptadas (check HTTP 202)')
    axes[0].bar(x, fallidas, bottom=aceptadas, color='#c94f54', label='Fallidas (sin código exacto de A2)')
    for i, (ok, error) in enumerate(zip(aceptadas, fallidas)):
        axes[0].text(i, ok / 2, str(ok), ha='center', va='center', color='white', weight='bold')
        if error:
            axes[0].text(i, ok + error / 2, str(error), ha='center', va='center', color='white', weight='bold')
        axes[0].text(i, ok + error + 1, f'Total: {ok + error}', ha='center')
    axes[0].set(xticks=x, xticklabels=nombres, ylabel='Solicitudes HTTP', ylim=(0, 62), title='Solicitudes aceptadas y fallidas')
    axes[0].legend(loc='upper center', bbox_to_anchor=(.5, -.24), frameon=False, fontsize=9)
    porcentajes = [m['errores_porcentaje'] for m in mediciones]
    bars = axes[1].bar(x, porcentajes, color=['#15806b', '#c94f54'])
    axes[1].bar_label(bars, labels=[f'{v:.0f} %' for v in porcentajes], padding=5)
    axes[1].set(xticks=x, xticklabels=['Actividad 1', 'Actividad 2'], ylabel='Errores (%)', ylim=(0, 105), title='Porcentaje de errores')
    fig.suptitle('Resultados reales de los dos ensayos controlados', fontsize=16)
    fig.subplots_adjust(bottom=.27, top=.82, wspace=.3)
    guardar_figura(fig, '01-solicitudes-y-errores')

    fig, ax = plt.subplots(figsize=(11.5, 5.5))
    etiquetas = ['Media global', 'Media de aceptadas', 'P95 global', 'P99 global']
    for i, (m, color) in enumerate(zip(mediciones, ['#275d8c', '#d69125'])):
        l = m['latencia_global_ms']
        valores = [l['avg'], m['latencia_aceptadas_media_ms'], l['p(95)'], l['p(99)']]
        posiciones = [j + (-.19 if i == 0 else .19) for j in range(4)]
        bars = ax.bar(posiciones, [v / 1000 for v in valores], width=.36, color=color, label=f'Actividad {i + 1}')
        ax.bar_label(bars, labels=[formato(v / 1000) for v in valores], padding=4, fontsize=10)
    ax.set(xticks=range(4), xticklabels=etiquetas, ylabel='Latencia HTTP (segundos)', ylim=(0, 32), title='La media global baja por rechazos rápidos; las aceptadas siguen lentas')
    ax.legend(frameon=False, loc='upper left')
    fig.text(.5, .025, 'Agregados originales: no reconstruyen una distribución ni una serie temporal. Máximo 50 muestras.', ha='center', fontsize=10)
    fig.tight_layout(rect=(0, .07, 1, 1))
    guardar_figura(fig, '02-latencias')

    fig, ax = plt.subplots(figsize=(10, 4.2))
    tasas = [m['throughput_post_s'] for m in mediciones]
    bars = ax.barh([1, 0], tasas, color=['#275d8c', '#d69125'], height=.5)
    ax.bar_label(bars, labels=[formato(v, 4) + ' POST/s' for v in tasas], padding=7)
    ax.set(yticks=[1, 0], yticklabels=['Actividad 1 · 10 VUs', 'Actividad 2 · 50 VUs'], xlabel='Throughput global del resumen k6 (POST/s)', xlim=(0, .57), title='Throughput observado con presupuestos y tiempos distintos')
    fig.text(.5, .025, 'Incluye espera del escenario y margen final. No equivale a capacidad máxima ni tráfico sostenido.', ha='center', fontsize=9)
    fig.tight_layout(rect=(0, .1, 1, 1))
    guardar_figura(fig, '03-throughput-controlado')


def escribir_informe(resultados):
    tabla = tabla_md(CABECERAS, tabla_resultados(resultados))
    media_fallida = resultados[1]['calculos_derivados_no_muestras_individuales']['latencia_fallidas_media_ms']
    texto = f'''# Pruebas de Carga y Análisis de Rendimiento con Grafana k6

Informe técnico preparado el 16 de octubre de 2026. Autor, materia, docente y
formato académico final: pendientes de completar con datos reales.

## 1. Objetivo y alcance

Evaluar el comportamiento de POST /notifications/send en el flujo API Gateway →
Lambda Spring Boot → SNS → SQS → Lambda consumidora → SES Simulator, manteniendo
la cuota regional de 10 ejecuciones simultáneas y sin ampliarla.
Los scripts usaron JWT por variable de entorno y el destinatario público del
simulador. No se incluyen credenciales, endpoints reales ni cuerpos de solicitudes.

## 2. Procedencia y método

Los únicos ensayos comparados son los JSON originales de las actividades 1 y 2.
Las verificaciones individuales se conservan como antecedentes separados.
El [manifiesto SHA-256](../evidencias/originales/manifest-sha256.json) documenta
integridad y rutas. La [comparativa JSON](../evidencias/derivadas/comparativa.json)
separa configuración, mediciones, cálculos derivados y confirmaciones del usuario.

| Ensayo | VUs configurados | Duración configurada | Presupuesto |
| --- | ---: | --- | --- |
| Actividad 1 | 10 | 1 minuto | Máximo 3 POST/VU, 30 totales |
| Actividad 2, escenario 1 | 50 | 2 minutos | Máximo 1 POST/VU, 50 totales |

Ambos fueron ensayos controlados, no carga sostenida. La duración configurada no
es la duración de tráfico continuo. A2 generó una ráfaga al comienzo y luego
esperó. No es una comparación que aísle solo el número de VUs.

## 3. Resultados reales de actividades 1 y 2

{tabla}

Fuentes: [A1](../evidencias/actividad1-20261016-005817.json) y
[A2](../evidencias/actividad2-50vus-20261016-014407.json).
Todas las latencias de la tabla son milisegundos. Throughput es el valor global
del resumen, incluidos fallos y tiempo de espera; no representa capacidad máxima.

![Solicitudes y errores reales](../evidencias/derivadas/graficos/01-solicitudes-y-errores.png)

La Actividad 1 terminó con 30 checks HTTP 202 correctos y cero errores. La
Actividad 2 tuvo 10 checks correctos y 40 fallidos. El usuario confirmó actividad
de SNS/SQS/consumidor/SES en CloudWatch; aún faltan las exportaciones o capturas
para adjuntarlas como evidencia independiente de los resúmenes.

![Latencias HTTP reales](../evidencias/derivadas/graficos/02-latencias.png)

La caída de la media global en A2 no es una mejora del servicio. Las respuestas
aceptadas promediaron 25 295,21 ms. La media del grupo fallido, calculada por
diferencia entre agregados, fue **{formato(media_fallida)} ms**. No es una serie
de muestras individuales y no permite asignar códigos ni tiempos a cada fallo.
P95/P99 se informan como descriptivos, con una muestra pequeña y controles distintos.

![Throughput real de los resúmenes](../evidencias/derivadas/graficos/03-throughput-controlado.png)

El agregado `vus` de A1 contiene mínimo 2, máximo 10 y último valor 2. Sin serie
temporal no puede ubicarse la bajada respecto del minuto activo o del margen
final. La configuración de 10 VUs se documenta como configuración y el máximo
observado como medición; el resumen no prueba una serie constante por sí solo.

## 4. Cuello de botella y evidencia AWS

El usuario confirmó una cuota regional de 10, un pico de 40 throttles y máximo
de concurrencia de 10 en la prueba de 50 VUs. Esta confirmación respalda rechazo
por agotamiento de concurrencia como causa operativa del episodio. Se registra
como **confirmación del usuario en CloudWatch**, no como dato extraído de k6 ni
como una nueva consulta AWS realizada al preparar el informe.

Los arranques lentos de Spring Boot ocupan entornos durante más tiempo y pueden
agravar el problema. Las funciones comparten la cuota regional; no debe asignarse
todo el presupuesto al backend olvidando al consumidor. Terraform no declara
concurrencia reservada/provisionada que sustituya esa cuota regional.

Los códigos HTTP exactos de las 40 fallidas siguen desconocidos. No se inventan
500 ni 429. Un 202 confirma aceptación en SNS, no entrega final de SES.

## 5. Actividad 2: escenarios restantes

| VUs | Duración configurada | Estado | Solicitudes/latencias reales |
| ---: | --- | --- | --- |
| 100 | 2 minutos | Pendiente y bloqueado por cuota | No medidas |
| 250 | 2 minutos | Pendiente y bloqueado por cuota | No medidas |
| 500 | 2 minutos | Pendiente y bloqueado por cuota | No medidas |

Los [scripts pendientes](../pendientes/README.md) tienen bloqueo incondicional
en inicialización y antes de HTTP. No existe variable de entorno para activarlos.
No se ejecutaron, no tienen archivos de resultados y no se grafican como ceros.

## 6. Actividad 4: degradación observada

Se presenta [el análisis de la ráfaga de 50 VUs](ACTIVIDAD4-DEGRADACION.md).
**No se realizó una prueba de estrés incremental completa.** Solo puede afirmarse
degradación y rechazo bajo la configuración observada; no un punto de ruptura
intrínseco ni capacidad estimada para 100/250/500 VUs.

## 7. Actividad 5: recuperación histórica

La [guía de recuperación](ACTIVIDAD5-RECUPERACION.md) define datos y criterio de
estabilidad propuesto. El tiempo real queda **pendiente** por falta de timestamps
y series SQS/Lambda antes/después. No equivale a 120 s de k6, 180 s de visibilidad
SQS, ni al tiempo HTTP de una respuesta. No se generó una curva de recuperación.

## 8. Mejoras propuestas, sin ampliar la cuota

Planificar la concurrencia efectiva y distribuir intentos por debajo de la cuota,
con margen para consumidor y otras funciones. Revisar inicialización asíncrona
del adaptador, medir inicio JPA/Neon y diferir clientes no usados por esta ruta.
Conservar inicialmente la validación de esquema y el pool pequeño; no aumentar
conexiones a ciegas. Separar frío/caliente con registros, mantener presupuestos
de POST y usar el simulador. El registro por estado quedó preparado en el script
de 50 VUs, pero no se ejecutó otra prueba para obtenerlo.

No se cambió el backend, Terraform ni AWS. No se recomienda resolver el problema
con timeouts mayores o reintentos automáticos que puedan duplicar notificaciones.

## 9. Pendientes de la entrega

Adjuntar cuota/throttles/concurrencia de CloudWatch, evidencia asíncrona revisada,
ventanas exactas y series históricas para recuperación. Recuperar los códigos
de error solo si existían logs/consola; si no, mantenerlos desconocidos. Completar
datos académicos, URL del repositorio y requisitos de la rúbrica. Si se exige
Actividad 3, falta su enunciado; no se inventó una actividad no solicitada.
La [guía GitHub](../../docs/ENTREGA-GITHUB.md) describe exclusiones y revisión local.

## 10. Referencias y límites

[Métricas Lambda](https://docs.aws.amazon.com/lambda/latest/dg/monitoring-metrics-types.html),
[errores de integración](https://docs.aws.amazon.com/lambda/latest/dg/services-apigateway-errors.html),
[métricas SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-available-cloudwatch-metrics.html).
No hay mediciones simuladas, series reconstruidas, resultados de escenarios
pendientes ni una estimación inventada de recuperación. El informe se reproduce
localmente con `herramientas/generar-informe.py`, sin k6 ni conexiones AWS.
'''
    (INFORME / 'INFORME-TECNICO.md').write_text(texto, encoding='utf-8')


def escribir_html(resultados):
    filas = tabla_resultados(resultados)
    tabla = '<table><thead><tr>' + ''.join(f'<th>{html.escape(c)}</th>' for c in CABECERAS)
    tabla += '</tr></thead><tbody>' + ''.join('<tr>' + ''.join(f'<td>{html.escape(c)}</td>' for c in f) + '</tr>' for f in filas) + '</tbody></table>'
    figuras = ''
    for nombre, titulo in [
        ('01-solicitudes-y-errores', 'Aceptación y errores'),
        ('02-latencias', 'Latencias de las mediciones reales'),
        ('03-throughput-controlado', 'Throughput con presupuestos distintos'),
    ]:
        contenido = base64.b64encode((GRAFICOS / f'{nombre}.png').read_bytes()).decode('ascii')
        figuras += f'<figure><img alt="{titulo}" src="data:image/png;base64,{contenido}"><figcaption>{titulo}. Fuente: JSON k6 originales.</figcaption></figure>'
    pagina = '''<!doctype html><html lang="es"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Informe de rendimiento · k6 y AWS</title><style>
body{font:16px/1.65 system-ui,sans-serif;background:#f1f5f9;color:#182d42;margin:0}main{max-width:1180px;margin:auto;padding:42px 26px}
header{background:#14344f;color:white;padding:32px;border-radius:16px}h1{font-size:34px;line-height:1.18;margin:10px 0}h2{font-size:23px}small{letter-spacing:.08em}
section,figure{background:white;border-radius:12px;margin:24px 0;padding:24px;border:1px solid #d8e2ec}figure img{width:100%;height:auto}figcaption{color:#53677a;font-size:13px}
.estado{display:inline-block;background:#d9efe7;color:#155647;padding:4px 12px;border-radius:20px;font-weight:650}.pendiente{background:#fff1d2;color:#734d08;padding:18px;border-radius:8px}
.scroll{overflow-x:auto}table{border-collapse:collapse;min-width:1060px;font-size:13px}th,td{padding:12px 9px;text-align:right;border-bottom:1px solid #dce4ec}th{background:#eaf0f6}th:first-child,td:first-child{text-align:left}
a{color:#1c608f}header p{color:#dce8f3}.nota{color:#53677a}footer{font-size:13px;color:#53677a;margin-top:30px}
</style><main><header><small>PRÁCTICA UNIVERSITARIA · 16 OCTUBRE 2026</small><h1>Pruebas de carga y análisis de rendimiento</h1>
<p>Grafana k6 · API Gateway · Lambda · SNS · SQS · SES Simulator</p><span class="estado">Solo mediciones reales</span></header>
<section><h2>Dos ensayos controlados, un límite regional</h2><p>A1: 30 solicitudes aceptadas, 0 % de errores. A2: 10 aceptadas y 40 fallidas, 80 % de errores.
El usuario confirmó cuota regional de 10, 40 throttles y concurrencia máxima de 10 en CloudWatch; las exportaciones aún deben adjuntarse.</p>
<p class="nota">Configuración: A1, 10 VUs / 1 minuto / máximo 3 POST por VU. A2, 50 VUs / 2 minutos / máximo 1 POST por VU.
No fueron pruebas de carga sostenida ni comparaciones que aislaran únicamente la concurrencia.</p></section>
<section><h2>Comparativa extraída de los JSON</h2><div class="scroll">TABLA</div><p class="nota">Latencias en ms, throughput global en POST/s. Las respuestas aceptadas de A2 promediaron 25,30 s; la media global menor se explica por rechazos rápidos.</p></section>
FIGURAS
<section><h2>Actividad 4 · degradación observada</h2><p>La concurrencia regional agotada fue el cuello de botella confirmado por el usuario para esta ráfaga.
Los arranques lentos de Spring prolongan la ocupación de los entornos. No se conocen los códigos HTTP exactos de las 40 fallidas.</p>
<p>No se realizó un estrés incremental completo ni se midió el punto de ruptura de la aplicación sin esa cuota.</p></section>
<section><h2>Actividad 5 · recuperación</h2><p class="pendiente">Tiempo real pendiente: faltan timestamps y series históricas de SQS/Lambda. No hay una curva de recuperación medida.</p>
<p>Se preparó un método para comparar con el nivel base y confirmar tres muestras estables. Un periodo sin tráfico ni throttles no demuestra recuperación HTTP.</p></section>
<section><h2>Escenarios pendientes</h2><p>100, 250 y 500 VUs · 2 minutos configurados cada uno · <strong>bloqueados y sin ejecutar</strong> por cuota de 10.
No se asignan porcentajes, latencias ni resultados supuestos.</p><h2>Qué falta</h2><p>Exportaciones CloudWatch, ventanas y series de recuperación, códigos si existen registros, datos académicos y URL del repositorio.</p>
<p><a href="INFORME-TECNICO.md">Informe técnico completo en Markdown</a> · <a href="ACTIVIDAD4-DEGRADACION.md">Actividad 4</a> · <a href="ACTIVIDAD5-RECUPERACION.md">Actividad 5</a></p></section>
<footer>Artefacto local con imágenes incorporadas; no carga scripts, fuentes ni datos externos. Originales conservados; sin nuevas solicitudes ni cambios AWS.</footer></main></html>'''
    (INFORME / 'RESUMEN-VISUAL.html').write_text(pagina.replace('TABLA', tabla).replace('FIGURAS', figuras), encoding='utf-8')


def main():
    for directorio in [DERIVADAS, GRAFICOS, INFORME, EVIDENCIAS / 'originales']:
        directorio.mkdir(parents=True, exist_ok=True)
    resultados = cargar_resultados()
    manifest = []
    for ruta in sorted(EVIDENCIAS.glob('*.json')):
        manifest.append({'ruta': ruta.relative_to(BASE).as_posix(), 'bytes': ruta.stat().st_size,
                         'sha256': hashlib.sha256(ruta.read_bytes()).hexdigest()})
    (EVIDENCIAS / 'originales' / 'manifest-sha256.json').write_text(json.dumps({
        'tipo': 'integridad de originales locales; no certifica fecha de AWS', 'archivos': manifest,
    }, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (DERIVADAS / 'comparativa.json').write_text(json.dumps({
        'tipo': 'derivado de resúmenes existentes; no ejecución nueva',
        'resultados': resultados,
        'confirmaciones_usuario_no_exportaciones_cloudwatch': {
            'fuente': 'mensaje del usuario de 2026-10-16', 'region': 'us-east-2',
            'cuota_regional_lambda': 10, 'throttles_actividad2': 40,
            'concurrencia_maxima_actividad2': 10, 'exportacion_adjunta': False,
        },
        'pendientes': {
            'codigos_http_40_fallidas': None, 'tiempo_recuperacion_real_s': None,
            'escenarios_no_ejecutados_vus': [100, 250, 500],
        },
    }, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    generar_graficos(resultados)
    escribir_informe(resultados)
    escribir_html(resultados)
    print('Informe, tablas y tres gráficos generados desde dos JSON originales. Sin red, k6 ni AWS.')


if __name__ == '__main__':
    main()
