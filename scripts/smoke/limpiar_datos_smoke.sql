-- US-25 — Limpieza de los datos creados por scripts/smoke/smoke-sprint3.ps1 (AB#56)
-- Ejecutar en el SQL Editor de Supabase DESPUÉS de verificar_esquema_sprint3.sql.
--
-- Solo borra lo que cuelga de usuarios con correo LIKE 'smoke.%@campored.test': sus contactos, pedidos, ofertas,
-- negocio y finca. Si un usuario real hubiera pedido o contactado una oferta smoke, ese pedido o contacto también
-- se borra, porque cuelga de la oferta smoke y la FK no deja eliminarla mientras exista.
--
-- Orden impuesto por las FK (sql/sprint2, sql/sprint3 y las entidades):
--   registros_contacto -> pedidos, ofertas, usuarios
--   pedidos            -> ofertas, usuarios
--   ofertas            -> usuarios, fincas
--   negocios           -> usuarios
--   usuarios           -> fincas (usuarios.finca_id)
-- por eso: registros_contacto -> pedidos -> ofertas -> negocios -> usuarios -> fincas.
--
-- Para probarlo sin borrar nada, cambia el COMMIT final por ROLLBACK.
-- El SQL Editor muestra solo el último resultado: la consulta final trae las columnas antes y despues.

BEGIN;

-- Los ids se toman del patrón de correo antes de borrar: las fincas solo se encuentran a través de usuarios.finca_id
CREATE TEMP TABLE smoke_usuarios ON COMMIT DROP AS
    SELECT id, finca_id FROM usuarios WHERE correo LIKE 'smoke.%@campored.test';

CREATE TEMP TABLE smoke_ofertas ON COMMIT DROP AS
    SELECT id FROM ofertas WHERE productor_id IN (SELECT id FROM smoke_usuarios);

CREATE TEMP TABLE smoke_pedidos ON COMMIT DROP AS
    SELECT id FROM pedidos
    WHERE comprador_id IN (SELECT id FROM smoke_usuarios)
       OR oferta_id IN (SELECT id FROM smoke_ofertas);

CREATE TEMP TABLE smoke_conteo ON COMMIT DROP AS
    SELECT 1 AS orden, 'registros_contacto' AS tabla, count(*) AS antes FROM registros_contacto
    WHERE comprador_id IN (SELECT id FROM smoke_usuarios)
       OR oferta_id IN (SELECT id FROM smoke_ofertas)
       OR pedido_id IN (SELECT id FROM smoke_pedidos)
    UNION ALL
    SELECT 2, 'pedidos', count(*) FROM smoke_pedidos
    UNION ALL
    SELECT 3, 'ofertas', count(*) FROM smoke_ofertas
    UNION ALL
    SELECT 4, 'negocios', count(*) FROM negocios WHERE usuario_id IN (SELECT id FROM smoke_usuarios)
    UNION ALL
    SELECT 5, 'usuarios', count(*) FROM smoke_usuarios
    UNION ALL
    SELECT 6, 'fincas', count(*) FROM fincas WHERE id IN (SELECT finca_id FROM smoke_usuarios WHERE finca_id IS NOT NULL);

-- Conteo inicial
SELECT tabla, antes FROM smoke_conteo ORDER BY orden;

DELETE FROM registros_contacto
WHERE comprador_id IN (SELECT id FROM smoke_usuarios)
   OR oferta_id IN (SELECT id FROM smoke_ofertas)
   OR pedido_id IN (SELECT id FROM smoke_pedidos);

DELETE FROM pedidos
WHERE id IN (SELECT id FROM smoke_pedidos);

DELETE FROM ofertas
WHERE id IN (SELECT id FROM smoke_ofertas);

DELETE FROM negocios
WHERE usuario_id IN (SELECT id FROM smoke_usuarios);

DELETE FROM usuarios
WHERE correo LIKE 'smoke.%@campored.test'
  AND id IN (SELECT id FROM smoke_usuarios);

DELETE FROM fincas
WHERE id IN (SELECT finca_id FROM smoke_usuarios WHERE finca_id IS NOT NULL);

-- Conteo final: despues debe ser 0 en todas las tablas
SELECT c.tabla,
       c.antes,
       CASE c.tabla
           WHEN 'registros_contacto' THEN (SELECT count(*) FROM registros_contacto
                                           WHERE comprador_id IN (SELECT id FROM smoke_usuarios)
                                              OR oferta_id IN (SELECT id FROM smoke_ofertas)
                                              OR pedido_id IN (SELECT id FROM smoke_pedidos))
           WHEN 'pedidos' THEN (SELECT count(*) FROM pedidos WHERE id IN (SELECT id FROM smoke_pedidos))
           WHEN 'ofertas' THEN (SELECT count(*) FROM ofertas WHERE id IN (SELECT id FROM smoke_ofertas))
           WHEN 'negocios' THEN (SELECT count(*) FROM negocios WHERE usuario_id IN (SELECT id FROM smoke_usuarios))
           WHEN 'usuarios' THEN (SELECT count(*) FROM usuarios WHERE correo LIKE 'smoke.%@campored.test')
           WHEN 'fincas' THEN (SELECT count(*) FROM fincas
                               WHERE id IN (SELECT finca_id FROM smoke_usuarios WHERE finca_id IS NOT NULL))
       END AS despues
FROM smoke_conteo c
ORDER BY c.orden;

COMMIT;
