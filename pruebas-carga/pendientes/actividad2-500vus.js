// PENDIENTE POR CUOTA. No ejecutado; sin resultados ni autorización de tráfico.
import { crearEscenarioPendiente, bloquearPorCuota } from './escenario-bloqueado.js';
const escenario = crearEscenarioPendiente(500);
export const options = escenario.options;
bloquearPorCuota(500); // Bloqueo incondicional en init, también con --no-setup.
export default function () { escenario.ejecutar(); }
