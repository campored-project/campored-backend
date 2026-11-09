// ============================================================
// FEATURE: US-25 — Despliegue final (Sprint 3)
// Autor: Cristian Diez
// Fecha: 2026-11-08
// Descripción: Carga por la API de los datos demo, con los pedidos y contactos del Sprint 3
// ============================================================
// Uso, desde la raíz del repo (Node 18+, sin dependencias):
//   DEMO_PASSWORD='...' node scripts/demo/seed-demo.mjs                 (bash)
//   $env:DEMO_PASSWORD='...'; node scripts/demo/seed-demo.mjs           (PowerShell)
//
// Variables de entorno:
//   DEMO_PASSWORD     contraseña de todos los usuarios demo. Obligatoria; sin ella no se hace ninguna petición
//   BASE_URL          URL del backend. Por defecto https://campored-backend.onrender.com
//   DEMO_RESULTADO    JSON con los ids y casos de la corrida. Por defecto ../seed-demo/resultado-demo.json
//   DEMO_USUARIOS_MD  archivo cuya sección "## Usuarios demo" se reemplaza. Por defecto ../usuarios-prueba-render.md
// Las dos salidas quedan fuera del repo. La contraseña y los tokens nunca se imprimen ni se escriben.
//
// Todo pasa por la API (BCrypt y validaciones incluidas), nunca por INSERT. Flujo completo:
//   scripts/demo/limpiar_datos_demo.sql -> este script -> scripts/demo/verificar_datos_demo.sql
//
// Idempotente: registro 409 -> login; oferta con el mismo producto y productoOtro en mis-ofertas -> se omite;
// pedido del mismo comprador sobre la misma oferta en /pedidos/recibidos -> se omite; contacto con la misma
// oferta, canal y pedido en /contactos/mios -> se omite; oferta ya agotada -> no se vuelve a agotar.
//
// Códigos de salida: 0 todo bien · 1 algún caso falló · 2 detenido en un paso bloqueante ·
// 3 la guardia encontró datos demo que no son de este seed

import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const DIR = path.dirname(fileURLToPath(import.meta.url));
const RAIZ_REPO = path.resolve(DIR, '..', '..');
const BASE_URL = (process.env.BASE_URL || 'https://campored-backend.onrender.com').replace(/\/+$/, '');
const API = `${BASE_URL}/api`;
const ARCHIVO_RESULTADO = process.env.DEMO_RESULTADO || path.resolve(RAIZ_REPO, '..', 'seed-demo', 'resultado-demo.json');
const ARCHIVO_USUARIOS = process.env.DEMO_USUARIOS_MD || path.resolve(RAIZ_REPO, '..', 'usuarios-prueba-render.md');
const ENCABEZADO_DEMO = '## Usuarios demo';
const CONTRASENA = process.env.DEMO_PASSWORD;

const SALIDA = { OK: 0, FALLOS: 1, DETENIDO: 2, DATOS_AJENOS: 3 };
const OK = '✅';
const KO = '❌';
const AVISO = '⚠';

// Misma regla que RegistroProductorRequest y RegistroCompradorRequest
const PATRON_CONTRASENA = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,64}$/;

if (!CONTRASENA) {
  console.error(`${KO} Falta la variable de entorno DEMO_PASSWORD. No se hizo ninguna petición.`);
  process.exit(SALIDA.DETENIDO);
}
if (!PATRON_CONTRASENA.test(CONTRASENA)) {
  console.error(`${KO} DEMO_PASSWORD no cumple la regla del registro: 8 a 64 caracteres con mayúsculas, minúsculas y números.`);
  process.exit(SALIDA.DETENIDO);
}

const datos = JSON.parse(await readFile(path.join(DIR, 'datos-demo.json'), 'utf8'));
const productores = new Map(datos.productores.map((p) => [p.clave, p]));
const compradores = new Map(datos.compradores.map((c) => [c.clave, c]));
const filasOferta = new Map(datos.ofertas.map((o) => [o.clave, o]));
const filasPedido = new Map(datos.pedidos.map((p) => [p.clave, p]));

// ---------------------------------------------------------------------------
// Utilidades
// ---------------------------------------------------------------------------
const hoy = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'America/Bogota', year: 'numeric', month: '2-digit', day: '2-digit',
}).format(new Date());

function sumarDias(fechaIso, dias) {
  const d = new Date(`${fechaIso}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + dias);
  return d.toISOString().slice(0, 10);
}

const dormir = (ms) => new Promise((resolver) => setTimeout(resolver, ms));
const claveProducto = (producto, productoOtro) => `${producto}|${(productoOtro ?? '').trim().toLowerCase()}`;
const nombreProducto = (fila) => (fila.productoOtro ? fila.productoOtro : fila.producto);
const tieneTexto = (valor) => typeof valor === 'string' && valor.trim() !== '';

// Igual que OfertaMapper.toProductorPublico: canal habilitado y con número
function canalDisponible(productor, canal) {
  return canal === 'WHATSAPP'
    ? productor.canalWhatsappHabilitado && tieneTexto(productor.whatsapp)
    : productor.canalLlamadaHabilitado && tieneTexto(productor.telefono);
}

function ocultarSecretos(texto) {
  return String(texto)
    .split(CONTRASENA).join('***')
    .replace(/("contrasena"\s*:\s*")[^"]*"/g, '$1***"')
    .replace(/("token"\s*:\s*")[^"]*"/g, '$1***"');
}

async function api(metodo, ruta, { token, body } = {}) {
  const headers = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const cuerpo = body === undefined ? undefined : JSON.stringify(body);
  if (cuerpo !== undefined) headers['Content-Type'] = 'application/json';
  const peticion = ocultarSecretos(`${metodo} ${ruta}${token ? ' [Bearer]' : ''}${cuerpo ? ` ${cuerpo}` : ''}`);
  // Solo las lecturas se reintentan: repetir un POST que sí llegó al backend duplicaría un pedido o un contacto
  const intentos = metodo === 'GET' ? 2 : 1;
  for (let intento = 1; ; intento++) {
    let status = 0;
    let texto = '';
    try {
      const res = await fetch(`${API}${ruta}`, { method: metodo, headers, body: cuerpo, signal: AbortSignal.timeout(90_000) });
      status = res.status;
      texto = await res.text();
    } catch (e) {
      texto = `Error de red: ${e.message}`;
    }
    if (intento < intentos && (status === 0 || [502, 503, 504].includes(status))) {
      console.log(`  ${AVISO} reintento tras ${status || 'error de red'}: ${metodo} ${ruta}`);
      await dormir(5_000);
      continue;
    }
    let json = null;
    try { json = texto ? JSON.parse(texto) : null; } catch { /* cuerpo que no es JSON */ }
    return { status, json, texto, peticion };
  }
}

const casos = [];
function caso(nombre, esperado, obtenido, ok, r) {
  const registro = { nombre, esperado: String(esperado), obtenido: String(obtenido), ok: Boolean(ok) };
  if (!ok && r) {
    registro.detalle = { peticion: r.peticion, respuesta: ocultarSecretos(`HTTP ${r.status} ${r.texto}`).slice(0, 2000) };
  }
  casos.push(registro);
  console.log(`${ok ? OK : KO} ${nombre} | esperado ${esperado} · obtenido ${obtenido}`);
  return Boolean(ok);
}

function imprimirFallos() {
  for (const f of casos.filter((c) => !c.ok)) {
    console.log(`\n${KO} ${f.nombre}`);
    if (f.detalle) console.log(`   PETICIÓN : ${f.detalle.peticion}\n   RESPUESTA: ${f.detalle.respuesta}`);
  }
}

function detener(motivo) {
  console.error(`\n${KO} Paso bloqueante: ${motivo}. Se detiene el seed.`);
  imprimirFallos();
  process.exit(SALIDA.DETENIDO);
}

function seccion(titulo) {
  console.log(`\n--- ${titulo} ---`);
}

// ---------------------------------------------------------------------------
// Validación previa de datos-demo.json, sin red
// ---------------------------------------------------------------------------
function validarDatos() {
  const errores = [];
  const duplicadas = (lista) => lista.filter((x, i) => lista.indexOf(x) !== i);
  for (const [nombre, lista] of [['productores', datos.productores], ['compradores', datos.compradores],
    ['ofertas', datos.ofertas], ['pedidos', datos.pedidos]]) {
    const repetidas = duplicadas(lista.map((x) => x.clave));
    if (repetidas.length) errores.push(`claves repetidas en ${nombre}: ${repetidas.join(', ')}`);
  }

  const productoPorProductor = datos.ofertas.map((o) => `${o.productor}|${claveProducto(o.producto, o.productoOtro)}`);
  for (const repetida of duplicadas(productoPorProductor)) {
    errores.push(`producto repetido para un productor (rompe la idempotencia): ${repetida}`);
  }

  for (const o of datos.ofertas) {
    if (!productores.has(o.productor)) errores.push(`${o.clave}: productor ${o.productor} inexistente`);
    if (o.descripcion.length < 150 || o.descripcion.length > 300) {
      errores.push(`${o.clave}: descripción de ${o.descripcion.length} caracteres (se esperan 150 a 300)`);
    }
    if (o.estadoCultivo === 'EN_CRECIMIENTO' && !(o.diasCosecha >= 1)) errores.push(`${o.clave}: EN_CRECIMIENTO sin diasCosecha futuro`);
    if (o.estadoCultivo === 'DISPONIBLE' && o.diasCosecha !== null) errores.push(`${o.clave}: DISPONIBLE con diasCosecha`);
    if ((o.producto === 'OTRO') !== tieneTexto(o.productoOtro)) errores.push(`${o.clave}: productoOtro solo y siempre con OTRO`);
  }

  for (const pd of datos.pedidos) {
    const oferta = filasOferta.get(pd.oferta);
    if (!compradores.has(pd.comprador)) errores.push(`${pd.clave}: comprador ${pd.comprador} inexistente`);
    if (!oferta) { errores.push(`${pd.clave}: oferta ${pd.oferta} inexistente`); continue; }
    if (!(pd.cantidadSolicitada > 0 && pd.cantidadSolicitada <= oferta.cantidadDisponible)) {
      errores.push(`${pd.clave}: cantidad ${pd.cantidadSolicitada} fuera de (0, ${oferta.cantidadDisponible}]`);
    }
    if (!(pd.diasFechaPreferida >= 2 && pd.diasFechaPreferida <= 10)) errores.push(`${pd.clave}: fecha preferida fuera de hoy+2..hoy+10`);
    if (oferta.estadoCultivo === 'EN_CRECIMIENTO' && pd.diasFechaPreferida < oferta.diasCosecha) {
      errores.push(`${pd.clave}: fecha preferida antes de la cosecha de ${oferta.clave}`);
    }
    if (!['MANANA', 'TARDE'].includes(pd.jornada)) errores.push(`${pd.clave}: jornada ${pd.jornada} inválida`);
    const modalidadValida = oferta.modalidadLogistica === 'AMBAS'
      ? ['RECOGIDA_FINCA', 'DESPACHO_PRODUCTOR'].includes(pd.modalidadEntrega)
      : pd.modalidadEntrega === null || pd.modalidadEntrega === oferta.modalidadLogistica;
    if (!modalidadValida) errores.push(`${pd.clave}: modalidad ${pd.modalidadEntrega} no habilitada en ${oferta.clave}`);
  }

  const claveContacto = (ct) => `${ct.comprador}|${ct.oferta}|${ct.canal}|${ct.pedido ?? ''}`;
  for (const repetida of duplicadas(datos.contactos.map(claveContacto))) errores.push(`contacto repetido: ${repetida}`);
  for (const ct of datos.contactos) {
    const oferta = filasOferta.get(ct.oferta);
    if (!compradores.has(ct.comprador)) errores.push(`contacto de ${ct.comprador}: comprador inexistente`);
    if (!oferta) { errores.push(`contacto sobre ${ct.oferta}: oferta inexistente`); continue; }
    const productor = productores.get(oferta.productor);
    if (!canalDisponible(productor, ct.canal)) errores.push(`contacto ${ct.comprador} -> ${ct.oferta}: ${oferta.productor} no tiene ${ct.canal}`);
    if (ct.pedido) {
      const pd = filasPedido.get(ct.pedido);
      if (!pd) errores.push(`contacto con pedido ${ct.pedido} inexistente`);
      else if (pd.comprador !== ct.comprador || pd.oferta !== ct.oferta) errores.push(`contacto con ${ct.pedido} de otro comprador u oferta`);
      const esperado = canalDisponible(productor, 'WHATSAPP') ? 'WHATSAPP' : 'LLAMADA';
      if (ct.canal !== esperado) errores.push(`contacto de ${ct.pedido}: debe ser ${esperado}`);
    }
  }
  for (const pd of datos.pedidos) {
    const n = datos.contactos.filter((ct) => ct.pedido === pd.clave).length;
    if (n !== 1) errores.push(`${pd.clave}: tiene ${n} contactos con su pedidoId (se espera 1)`);
  }

  for (const p of datos.productores) {
    if (!eliminablesDe(p.clave).length) errores.push(`${p.clave}: no le queda ninguna oferta activa sin pedidos ni contactos`);
  }
  return errores;
}

function bloqueoDe(claveOferta) {
  const conPedido = datos.pedidos.some((pd) => pd.oferta === claveOferta);
  const conContacto = datos.contactos.some((ct) => ct.oferta === claveOferta);
  if (conPedido && conContacto) return 'pedido y contacto';
  if (conPedido) return 'pedido';
  if (conContacto) return 'contacto';
  return null;
}

function eliminablesDe(claveProductor) {
  return datos.ofertas.filter((o) => o.productor === claveProductor && !o.agotar && !bloqueoDe(o.clave)).map((o) => o.clave);
}

const erroresDatos = validarDatos();
if (erroresDatos.length) {
  console.error(`${KO} datos-demo.json no cumple las reglas actuales:`);
  erroresDatos.forEach((e) => console.error(`   - ${e}`));
  process.exit(SALIDA.DETENIDO);
}

async function leerResultadoPrevio() {
  if (!existsSync(ARCHIVO_RESULTADO)) return null;
  try { return JSON.parse(await readFile(ARCHIVO_RESULTADO, 'utf8')); } catch { return null; }
}

console.log('======================================================================');
console.log(' Seed de datos demo — CampoRed (US-25)');
console.log(` URL base : ${BASE_URL}`);
console.log(` Hoy (America/Bogota): ${hoy}`);
console.log(` Datos    : ${datos.productores.length} productores, ${datos.compradores.length} compradores, `
  + `${datos.ofertas.length} ofertas, ${datos.pedidos.length} pedidos, ${datos.contactos.length} contactos`);
console.log('======================================================================');

// ---------------------------------------------------------------------------
// 0. Despertar Render: GET /v3/api-docs cada 10 s, hasta 120 s
// ---------------------------------------------------------------------------
seccion('0. Despertar el servicio');
{
  const inicio = Date.now();
  let despierto = false;
  for (;;) {
    const t = Math.round((Date.now() - inicio) / 1000);
    try {
      const r = await fetch(`${BASE_URL}/v3/api-docs`, { signal: AbortSignal.timeout(30_000) });
      despierto = r.status === 200;
      console.log(`  t=${t}s -> HTTP ${r.status}`);
    } catch {
      console.log(`  t=${t}s -> sin respuesta`);
    }
    if (despierto || Date.now() - inicio >= 120_000) break;
    await dormir(10_000);
  }
  if (!despierto) detener('el servicio no respondió 200 en /v3/api-docs en 120 s');
}

// ---------------------------------------------------------------------------
// 1. Guardia: si P1 ya inicia sesión y no es de una corrida anterior de este seed, siguen los datos viejos
// ---------------------------------------------------------------------------
seccion('1. Guardia de datos viejos');
const previo = await leerResultadoPrevio();
{
  const p1 = productores.get('P1');
  const r = await api('POST', '/auth/login', { body: { correo: p1.correo, contrasena: CONTRASENA } });
  if (r.status === 200) {
    const idActual = r.json?.usuario?.id;
    if (!idActual || previo?.usuarios?.P1?.id !== idActual) {
      console.error(`\n${KO} ${p1.correo} ya inicia sesión en ${BASE_URL} y su id no está en ${ARCHIVO_RESULTADO}.`);
      console.error('   Siguen los datos demo anteriores. Ejecuta scripts/demo/limpiar_datos_demo.sql en Supabase y vuelve a');
      console.error('   correr el seed. No se creó ni modificó nada.');
      process.exit(SALIDA.DATOS_AJENOS);
    }
    caso(`Guardia: ${p1.correo}`, '401, o 200 de una corrida anterior de este seed',
      `200, corrida del ${previo.fecha} (se reanuda sin duplicar)`, true);
  } else if (r.status === 401) {
    caso(`Guardia: ${p1.correo}`, '401, o 200 de una corrida anterior de este seed', '401 (no existe)', true);
  } else {
    caso(`Guardia: ${p1.correo}`, '401 o 200', r.status, false, r);
    detener('la guardia no pudo saber si existen datos demo');
  }
}

// ---------------------------------------------------------------------------
// 2. Usuarios y ofertas
// ---------------------------------------------------------------------------
seccion('2. Productores, compradores y ofertas');
const sesiones = {}; // clave -> { token, usuario, creado }

async function registrarOEntrar(clave, ruta, cuerpo) {
  const nombre = `Registro ${clave} ${cuerpo.correo}`;
  const r = await api('POST', ruta, { body: cuerpo });
  if (r.status === 201 && r.json?.token) {
    sesiones[clave] = { token: r.json.token, usuario: r.json.usuario, creado: true };
    return caso(nombre, '201 o 409 + login', '201', true);
  }
  if (r.status === 409) {
    const l = await api('POST', '/auth/login', { body: { correo: cuerpo.correo, contrasena: CONTRASENA } });
    const ok = l.status === 200 && Boolean(l.json?.token);
    if (ok) sesiones[clave] = { token: l.json.token, usuario: l.json.usuario, creado: false };
    return caso(nombre, '201 o 409 + login', `409 -> login ${l.status}`, ok, l);
  }
  return caso(nombre, '201 o 409 + login', r.status, false, r);
}

for (const p of datos.productores) {
  const cuerpo = {
    correo: p.correo, contrasena: CONTRASENA, nombre: p.nombre, nombreFinca: p.nombreFinca,
    vereda: p.vereda, municipio: p.municipio, telefono: p.telefono,
    ...(p.whatsapp ? { whatsapp: p.whatsapp } : {}),
  };
  if (!(await registrarOEntrar(p.clave, '/auth/registro/productor', cuerpo))) detener(`sin sesión para ${p.clave}`);
  const r = await api('PATCH', '/usuarios/perfil/productor', {
    token: sesiones[p.clave].token,
    body: { canalWhatsappHabilitado: p.canalWhatsappHabilitado, canalLlamadaHabilitado: p.canalLlamadaHabilitado },
  });
  const u = r.json ?? {};
  const ok = r.status === 200 && u.canalWhatsappHabilitado === p.canalWhatsappHabilitado
    && u.canalLlamadaHabilitado === p.canalLlamadaHabilitado;
  if (ok) sesiones[p.clave].usuario = u;
  caso(`Canales ${p.clave}`, `200 wa=${p.canalWhatsappHabilitado} ll=${p.canalLlamadaHabilitado}`,
    `${r.status} wa=${u.canalWhatsappHabilitado} ll=${u.canalLlamadaHabilitado}`, ok, r);
  if (!ok) detener(`no se pudieron fijar los canales de ${p.clave}`);
}

for (const c of datos.compradores) {
  const cuerpo = {
    correo: c.correo, contrasena: CONTRASENA, nombre: c.nombre, nombreNegocio: c.nombreNegocio,
    tipoNegocio: c.tipoNegocio, direccion: c.direccion, municipio: c.municipio,
    horarioRecepcion: c.horarioRecepcion, telefono: c.telefono, whatsapp: c.whatsapp,
    ...(c.notasAcceso ? { notasAcceso: c.notasAcceso } : {}),
  };
  if (!(await registrarOEntrar(c.clave, '/auth/registro/comprador', cuerpo))) detener(`sin sesión para ${c.clave}`);
}

const ofertas = {}; // clave -> { id, estado, nueva }
for (const p of datos.productores) {
  const token = sesiones[p.clave].token;
  const mis = await api('GET', '/ofertas/mis-ofertas', { token });
  if (mis.status !== 200) {
    caso(`mis-ofertas ${p.clave}`, 200, mis.status, false, mis);
    detener(`no se pudieron leer las ofertas de ${p.clave}`);
  }
  const existentes = new Map(mis.json.map((o) => [claveProducto(o.producto, o.productoOtro), o]));
  for (const fila of datos.ofertas.filter((o) => o.productor === p.clave)) {
    const nombre = `Oferta ${fila.clave}`;
    const previa = existentes.get(claveProducto(fila.producto, fila.productoOtro));
    if (previa) {
      ofertas[fila.clave] = { id: previa.id, estado: previa.estadoPublicacion, nueva: false };
      caso(nombre, '201 o ya existe', 'ya existe (omitida)', true);
      continue;
    }
    const cuerpo = {
      producto: fila.producto,
      ...(fila.productoOtro ? { productoOtro: fila.productoOtro } : {}),
      descripcion: fila.descripcion,
      cantidadDisponible: fila.cantidadDisponible,
      unidadMedida: fila.unidadMedida,
      precioUnitario: fila.precioUnitario,
      estadoCultivo: fila.estadoCultivo,
      ...(fila.estadoCultivo === 'EN_CRECIMIENTO' ? { fechaEstimadaCosecha: sumarDias(hoy, fila.diasCosecha) } : {}),
      modalidadLogistica: fila.modalidadLogistica,
    };
    const r = await api('POST', '/ofertas', { token, body: cuerpo });
    const ok = r.status === 201 && Boolean(r.json?.id);
    if (ok) ofertas[fila.clave] = { id: r.json.id, estado: r.json.estadoPublicacion, nueva: true };
    caso(nombre, '201 o ya existe', r.status, ok, r);
  }
}
const sinOferta = datos.ofertas.filter((o) => !ofertas[o.clave]).map((o) => o.clave);
if (sinOferta.length) detener(`faltan ofertas: ${sinOferta.join(', ')}`);

// ---------------------------------------------------------------------------
// 3. Pedidos y contactos (antes de agotar: una oferta agotada ya no recibe pedidos)
// ---------------------------------------------------------------------------
seccion('3. Pedidos y contactos');
const pedidos = {}; // clave -> { id, nuevo, respuesta }

// PedidoResponse no trae el id del comprador: se reconoce por el nombre del negocio, único en los datos demo
const recibidos = {};
for (const clave of new Set(datos.pedidos.map((pd) => filasOferta.get(pd.oferta).productor))) {
  const r = await api('GET', '/pedidos/recibidos', { token: sesiones[clave].token });
  if (r.status !== 200) {
    caso(`pedidos recibidos ${clave}`, 200, r.status, false, r);
    detener(`no se pudieron leer los pedidos de ${clave}`);
  }
  recibidos[clave] = r.json;
}

for (const pd of datos.pedidos) {
  const fila = filasOferta.get(pd.oferta);
  const sesion = sesiones[pd.comprador];
  const negocio = compradores.get(pd.comprador);
  const ofertaId = ofertas[pd.oferta].id;
  const nombre = `Pedido ${pd.clave} ${pd.comprador} -> ${pd.oferta}`;
  const existente = recibidos[fila.productor].find((x) => x.oferta?.id === ofertaId
    && x.comprador?.nombreNegocio === (sesion.usuario?.nombreNegocio ?? negocio.nombreNegocio));
  if (existente) {
    pedidos[pd.clave] = { id: existente.id, nuevo: false, respuesta: existente };
    caso(nombre, '201 o ya existe', 'ya existe (omitido)', true);
    continue;
  }
  // Dirección, municipio y notas salen del negocio guardado, como la precarga del checkout
  const notas = sesion.usuario?.notasAcceso ?? negocio.notasAcceso;
  const cuerpo = {
    ofertaId,
    cantidadSolicitada: pd.cantidadSolicitada,
    direccionEntrega: sesion.usuario?.direccion ?? negocio.direccion,
    municipioEntrega: sesion.usuario?.municipio ?? negocio.municipio,
    fechaPreferida: sumarDias(hoy, pd.diasFechaPreferida),
    jornada: pd.jornada,
    ...(pd.modalidadEntrega ? { modalidadEntrega: pd.modalidadEntrega } : {}),
    ...(pd.conNotasAcceso && notas ? { notasAcceso: notas } : {}),
  };
  const r = await api('POST', '/pedidos', { token: sesion.token, body: cuerpo });
  const modalidad = pd.modalidadEntrega ?? fila.modalidadLogistica;
  const total = pd.cantidadSolicitada * fila.precioUnitario;
  const ok = r.status === 201 && r.json?.estado === 'SOLICITADO' && r.json?.modalidadEntrega === modalidad
    && Number(r.json?.precioTotalEstimado) === total;
  if (ok) pedidos[pd.clave] = { id: r.json.id, nuevo: true, respuesta: r.json };
  caso(nombre, `201 ${modalidad} total ${total}`,
    `${r.status} ${r.json?.modalidadEntrega ?? ''} total ${r.json?.precioTotalEstimado ?? ''}`, ok, r);
}

const contactos = []; // { comprador, oferta, canal, pedido, id, nuevo }
const misContactos = {};
for (const clave of new Set(datos.contactos.map((ct) => ct.comprador))) {
  const r = await api('GET', '/contactos/mios', { token: sesiones[clave].token });
  if (r.status !== 200) {
    caso(`contactos ${clave}`, 200, r.status, false, r);
    detener(`no se pudieron leer los contactos de ${clave}`);
  }
  misContactos[clave] = r.json;
}

for (const ct of datos.contactos) {
  const nombre = `Contacto ${ct.comprador} ${ct.canal} -> ${ct.oferta}${ct.pedido ? ` (${ct.pedido})` : ' (sin pedido)'}`;
  const ofertaId = ofertas[ct.oferta].id;
  const pedidoId = ct.pedido ? pedidos[ct.pedido]?.id : null;
  if (ct.pedido && !pedidoId) {
    caso(nombre, '201 o ya existe', `sin id de ${ct.pedido}`, false);
    continue;
  }
  const existente = misContactos[ct.comprador].find((x) => x.ofertaId === ofertaId && x.canal === ct.canal
    && (x.pedidoId ?? null) === pedidoId);
  if (existente) {
    contactos.push({ ...ct, id: existente.id, nuevo: false });
    caso(nombre, '201 o ya existe', 'ya existe (omitido)', true);
    continue;
  }
  const r = await api('POST', '/contactos', {
    token: sesiones[ct.comprador].token,
    body: { ofertaId, canal: ct.canal, ...(pedidoId ? { pedidoId } : {}) },
  });
  const ok = r.status === 201 && r.json?.ofertaId === ofertaId && r.json?.canal === ct.canal
    && (r.json?.pedidoId ?? null) === pedidoId;
  if (ok) contactos.push({ ...ct, id: r.json.id, nuevo: true });
  caso(nombre, '201 o ya existe', r.status, ok, r);
}

// ---------------------------------------------------------------------------
// 4. Agotar las ofertas que ya estaban agotadas en el Sprint 2
// ---------------------------------------------------------------------------
seccion('4. Ofertas agotadas');
for (const fila of datos.ofertas.filter((o) => o.agotar)) {
  const nombre = `Agotar ${fila.clave}`;
  const oferta = ofertas[fila.clave];
  if (oferta.estado === 'AGOTADA') {
    caso(nombre, '200 o ya AGOTADA', 'ya AGOTADA (omitida)', true);
    continue;
  }
  const r = await api('PATCH', `/ofertas/${oferta.id}/estado`, {
    token: sesiones[fila.productor].token, body: { estadoPublicacion: 'AGOTADA' },
  });
  const ok = r.status === 200 && r.json?.estadoPublicacion === 'AGOTADA';
  if (ok) oferta.estado = 'AGOTADA';
  caso(nombre, '200 AGOTADA', `${r.status} ${r.json?.estadoPublicacion ?? ''}`, ok, r);
}

// ---------------------------------------------------------------------------
// 5. Verificación por la API
// ---------------------------------------------------------------------------
seccion('5. Verificación');
const idsPedidos = (claves) => claves.map((k) => pedidos[k]?.id).filter(Boolean).sort();

{
  const c1 = compradores.get('C1');
  const r = await api('POST', '/auth/login', { body: { correo: c1.correo, contrasena: CONTRASENA } });
  caso('Login C1', '200 COMPRADOR', `${r.status} ${r.json?.usuario?.rol ?? ''}`,
    r.status === 200 && r.json?.usuario?.rol === 'COMPRADOR', r);
}

for (const c of datos.compradores) {
  const r = await api('GET', '/contactos/mios', { token: sesiones[c.clave].token });
  const esperados = datos.contactos.filter((ct) => ct.comprador === c.clave);
  const lista = r.json ?? [];
  const fechas = lista.map((x) => x.fechaContacto);
  const descendente = fechas.every((f, i) => i === 0 || Date.parse(fechas[i - 1]) >= Date.parse(f));
  const sinNumeros = lista.every((x) => !('whatsapp' in x) && !('telefono' in x) && !('whatsappContacto' in x));
  const conPedido = lista.filter((x) => x.pedidoId).length;
  caso(`Mis contactos ${c.clave}`, `${esperados.length} (${esperados.filter((x) => x.pedido).length} con pedido), descendente`,
    `${r.status} ${lista.length} (${conPedido} con pedido) descendente=${descendente}`,
    r.status === 200 && lista.length === esperados.length && conPedido === esperados.filter((x) => x.pedido).length
      && descendente && sinNumeros, r);
}

for (const p of datos.productores) {
  const r = await api('GET', '/pedidos/recibidos', { token: sesiones[p.clave].token });
  const esperados = idsPedidos(datos.pedidos.filter((pd) => filasOferta.get(pd.oferta).productor === p.clave).map((pd) => pd.clave));
  const obtenidos = (r.json ?? []).map((x) => x.id).sort();
  caso(`Pedidos recibidos ${p.clave}`, `${esperados.length} pedidos demo`, `${r.status} ${obtenidos.length}`,
    r.status === 200 && JSON.stringify(obtenidos) === JSON.stringify(esperados), r);
}

{
  const todas = [];
  let r = null;
  for (let pagina = 0; ; pagina++) {
    r = await api('GET', `/ofertas?size=50&page=${pagina}`);
    if (r.status !== 200) break;
    todas.push(...r.json.content);
    if (r.json.last) break;
  }
  const enCatalogo = new Set(todas.map((o) => o.id));
  const activas = datos.ofertas.filter((o) => !o.agotar);
  const agotadas = datos.ofertas.filter((o) => o.agotar);
  const faltan = activas.filter((o) => !enCatalogo.has(ofertas[o.clave].id)).map((o) => o.clave);
  const sobran = agotadas.filter((o) => enCatalogo.has(ofertas[o.clave].id)).map((o) => o.clave);
  caso('Catálogo público', `${activas.length} ofertas demo activas y ninguna de las ${agotadas.length} agotadas`,
    `${r.status} activas=${activas.length - faltan.length} agotadas=${sobran.length}${faltan.length ? ` faltan=[${faltan}]` : ''}`,
    r.status === 200 && !faltan.length && !sobran.length, r);
}

{
  const r = await api('GET', `/ofertas/${ofertas['P4-AGUACATE'].id}`, { token: sesiones.C1.token });
  const pr = r.json?.productor ?? {};
  caso('Detalle P4-AGUACATE como C1 (solo llamada)', 'llamada=true wa=false, solo teléfono',
    `${r.status} llamada=${pr.llamadaDisponible} wa=${pr.whatsappDisponible}`,
    r.status === 200 && pr.llamadaDisponible === true && pr.whatsappDisponible === false
      && pr.telefonoContacto === productores.get('P4').telefono && pr.whatsappContacto === null, r);
}
{
  const r = await api('GET', `/ofertas/${ofertas['P3-CAFE'].id}`, { token: sesiones.C1.token });
  const pr = r.json?.productor ?? {};
  caso('Detalle P3-CAFE como C1 (solo WhatsApp)', 'wa=true llamada=false, solo WhatsApp',
    `${r.status} wa=${pr.whatsappDisponible} llamada=${pr.llamadaDisponible}`,
    r.status === 200 && pr.whatsappDisponible === true && pr.llamadaDisponible === false
      && pr.whatsappContacto === productores.get('P3').whatsapp && pr.telefonoContacto === null, r);
}
if (pedidos.PD8) {
  const r = await api('GET', `/pedidos/${pedidos.PD8.id}`, { token: sesiones.C4.token });
  const pr = r.json?.productor ?? {};
  caso('Pedido PD8 como C4 (productor solo llamada)', 'llamada=true wa=false, solo teléfono',
    `${r.status} llamada=${pr.llamadaDisponible} wa=${pr.whatsappDisponible}`,
    r.status === 200 && pr.llamadaDisponible === true && pr.whatsappDisponible === false
      && pr.telefonoContacto === productores.get('P4').telefono && pr.whatsappContacto === null, r);
}

// ---------------------------------------------------------------------------
// 6. Salidas: resultado-demo.json y la sección "Usuarios demo"
// ---------------------------------------------------------------------------
const fallos = casos.filter((c) => !c.ok);
const ofertasSalida = Object.fromEntries(datos.ofertas.map((fila) => {
  const o = ofertas[fila.clave];
  const bloqueo = bloqueoDe(fila.clave);
  return [fila.clave, {
    id: o.id, productor: fila.productor, producto: fila.producto, productoOtro: fila.productoOtro,
    estadoCultivo: fila.estadoCultivo, modalidadLogistica: fila.modalidadLogistica, estado: o.estado,
    bloqueo, eliminable: !bloqueo, nueva: o.nueva,
  }];
}));
const pedidosSalida = Object.fromEntries(datos.pedidos.map((pd) => {
  const p = pedidos[pd.clave];
  const fila = filasOferta.get(pd.oferta);
  return [pd.clave, {
    id: p?.id ?? null, comprador: pd.comprador, oferta: pd.oferta, productor: fila.productor,
    cantidadSolicitada: pd.cantidadSolicitada, unidadMedida: fila.unidadMedida,
    fechaPreferida: p?.respuesta?.fechaPreferida ?? null, jornada: pd.jornada,
    modalidadEntrega: p?.respuesta?.modalidadEntrega ?? null,
    precioTotalEstimado: p?.respuesta?.precioTotalEstimado ?? null, nuevo: p?.nuevo ?? null,
  }];
}));
const usuariosSalida = Object.fromEntries(Object.entries(sesiones).map(([clave, s]) => [clave, {
  id: s.usuario?.id ?? null, correo: s.usuario?.correo ?? null, rol: s.usuario?.rol ?? null, creado: s.creado,
}]));
const eliminables = Object.fromEntries(datos.productores.map((p) => [p.clave, eliminablesDe(p.clave)]));
const contar = (lista) => ({ total: lista.length, nuevos: lista.filter(Boolean).length });
const resumen = {
  usuarios: contar(Object.values(sesiones).map((s) => s.creado)),
  ofertas: { ...contar(Object.values(ofertas).map((o) => o.nueva)),
    activas: Object.values(ofertas).filter((o) => o.estado !== 'AGOTADA').length,
    agotadas: Object.values(ofertas).filter((o) => o.estado === 'AGOTADA').length },
  pedidos: contar(Object.values(pedidos).map((p) => p.nuevo)),
  contactos: contar(contactos.map((c) => c.nuevo)),
  eliminablesPorProductor: Object.fromEntries(Object.entries(eliminables).map(([k, v]) => [k, v.length])),
};

await mkdir(path.dirname(ARCHIVO_RESULTADO), { recursive: true });
await writeFile(ARCHIVO_RESULTADO, `${JSON.stringify({
  fecha: hoy, baseUrl: BASE_URL, generado: new Date().toISOString(), resumen,
  usuarios: usuariosSalida, ofertas: ofertasSalida, pedidos: pedidosSalida, contactos, casos,
}, null, 2)}\n`, 'utf8');

const md = [];
const etiquetaOferta = (fila) => (fila.productoOtro ? `OTRO "${fila.productoOtro}"` : fila.producto);
md.push(ENCABEZADO_DEMO, '');
md.push(`Cargados con \`scripts/demo/seed-demo.mjs\` el ${hoy} contra ${BASE_URL}. La contraseña de todos es la de `
  + '`DEMO_PASSWORD` con que se corrió el seed; no se guarda en este archivo. Teléfonos ficticios.', '');
md.push('Para recargarlos: `scripts/demo/limpiar_datos_demo.sql` → `scripts/demo/seed-demo.mjs` → '
  + '`scripts/demo/verificar_datos_demo.sql`. Ninguna limpieza toca los usuarios smoke (`@campored.test`).', '');
md.push('### Productores', '', '| # | Nombre | Correo | Finca | Municipio | Canales |', '|---|---|---|---|---|---|');
for (const p of datos.productores) {
  const canales = [p.canalWhatsappHabilitado && 'WhatsApp', p.canalLlamadaHabilitado && 'Llamada'].filter(Boolean).join(' y ');
  md.push(`| ${p.clave} | ${p.nombre} | ${p.correo} | ${p.nombreFinca} (${p.vereda}) | ${p.municipio} | ${canales} |`);
}
md.push('', '### Compradores', '', '| # | Nombre | Correo | Negocio | Tipo | Municipio |', '|---|---|---|---|---|---|');
for (const c of datos.compradores) {
  md.push(`| ${c.clave} | ${c.nombre} | ${c.correo} | ${c.nombreNegocio} | ${c.tipoNegocio} | ${c.municipio} |`);
}
md.push('', '### Ofertas por productor', '',
  '"Eliminable" = sin pedidos ni contactos: `DELETE /api/ofertas/{id}` responde 204 (US-10). "Bloqueada" responde 422.', '');
for (const p of datos.productores) {
  md.push(`**${p.clave} — ${p.nombreFinca}** (${p.municipio})`, '');
  for (const fila of datos.ofertas.filter((o) => o.productor === p.clave)) {
    const o = ofertasSalida[fila.clave];
    const estado = o.estado === 'AGOTADA' ? '**AGOTADA**' : 'ACTIVA';
    const borrado = o.bloqueo ? `bloqueada (${o.bloqueo})` : '**eliminable**';
    md.push(`- \`${o.id}\` ${etiquetaOferta(fila)} — ${fila.estadoCultivo}, ${fila.modalidadLogistica}, `
      + `${fila.cantidadDisponible} ${fila.unidadMedida} a ${fila.precioUnitario} COP — ${estado} — ${borrado}`);
  }
  md.push('');
}
md.push('### Pedidos y contactos por comprador', '');
for (const c of datos.compradores) {
  md.push(`**${c.clave} — ${c.nombreNegocio}** (${c.correo})`, '');
  const susPedidos = datos.pedidos.filter((pd) => pd.comprador === c.clave);
  const susContactos = datos.contactos.filter((ct) => ct.comprador === c.clave);
  if (!susPedidos.length && !susContactos.length) {
    md.push('Sin pedidos ni contactos: muestra el estado vacío de Mis contactos.', '');
    continue;
  }
  for (const pd of susPedidos) {
    const p = pedidosSalida[pd.clave];
    const fila = filasOferta.get(pd.oferta);
    md.push(`- Pedido ${pd.clave} \`${p.id}\`: ${etiquetaOferta(fila)} de ${fila.productor} `
      + `(${productores.get(fila.productor).nombreFinca}), ${pd.cantidadSolicitada} ${fila.unidadMedida}, `
      + `${p.fechaPreferida} ${pd.jornada}, ${p.modalidadEntrega}, total ${p.precioTotalEstimado} COP`);
  }
  for (const ct of susContactos) {
    const fila = filasOferta.get(ct.oferta);
    md.push(`- Contacto ${ct.canal} con ${fila.productor} sobre ${etiquetaOferta(fila)}`
      + `${ct.pedido ? ` desde el pedido ${ct.pedido}` : ', sin pedido (desde el detalle de la oferta)'}`);
  }
  md.push('');
}
const seccionMd = md.join('\n').trimEnd();
let contenido = existsSync(ARCHIVO_USUARIOS) ? await readFile(ARCHIVO_USUARIOS, 'utf8') : '';
const encabezado = /^## Usuarios demo[ \t]*\r?$/m.exec(contenido);
if (encabezado) {
  // Se reemplaza hasta el siguiente encabezado de nivel 2 (los ### son de la propia sección) o hasta el final
  const desde = encabezado.index + encabezado[0].length;
  const siguiente = contenido.slice(desde).search(/\r?\n## /);
  const resto = siguiente >= 0 ? contenido.slice(desde + siguiente) : '';
  contenido = `${contenido.slice(0, encabezado.index)}${seccionMd}\n${resto}`;
} else {
  contenido = `${contenido.trimEnd()}${contenido ? '\n\n' : ''}${seccionMd}\n`;
}
await mkdir(path.dirname(ARCHIVO_USUARIOS), { recursive: true });
await writeFile(ARCHIVO_USUARIOS, contenido, 'utf8');

imprimirFallos();
const formato = (x) => `${x.total} (${x.nuevos} nuevos en esta corrida)`;
console.log('\n======================================================================');
console.log(` RESUMEN · ${BASE_URL} · ${hoy}`);
console.log('======================================================================');
console.log(` Casos     : ${casos.length - fallos.length} pasados · ${fallos.length} fallidos (de ${casos.length})`);
console.log(` Usuarios  : ${formato(resumen.usuarios)}`);
console.log(` Ofertas   : ${formato(resumen.ofertas)} · activas ${resumen.ofertas.activas} · agotadas ${resumen.ofertas.agotadas}`);
console.log(` Pedidos   : ${formato(resumen.pedidos)}`);
console.log(` Contactos : ${formato(resumen.contactos)}`);
console.log(` Eliminables por productor: ${Object.entries(resumen.eliminablesPorProductor).map(([k, v]) => `${k}=${v}`).join(' ')}`);
console.log(` Resultado : ${ARCHIVO_RESULTADO}`);
console.log(` Usuarios  : sección "${ENCABEZADO_DEMO}" de ${ARCHIVO_USUARIOS}`);
console.log('======================================================================');
process.exitCode = fallos.length ? SALIDA.FALLOS : SALIDA.OK;
