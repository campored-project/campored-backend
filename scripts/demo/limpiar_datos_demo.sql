-- US-25 — Limpieza de los datos demo: usuarios con correo LIKE '%@campored.demo' (AB#56)
-- Ejecutar en el SQL Editor de Supabase. Para probar sin borrar, cambia el COMMIT final por ROLLBACK.
-- Borra lo que cuelga de los usuarios demo: sus contactos, pedidos, ofertas, negocio y finca.
-- También borra pedidos y contactos de otros usuarios sobre ofertas demo, porque la FK no deja eliminar la oferta.
-- No toca usuarios smoke (@campored.test) ni usuarios reales.
-- Orden impuesto por las FK: registros_contacto -> pedidos -> ofertas -> negocios -> usuarios -> fincas.
-- El SQL Editor muestra solo el último resultado: la consulta final trae las columnas antes y despues.

BEGIN;

CREATE TEMP TABLE demo_usuarios ON COMMIT DROP AS
    SELECT id, finca_id FROM usuarios WHERE correo LIKE '%@campored.demo';

CREATE TEMP TABLE demo_ofertas ON COMMIT DROP AS
    SELECT id FROM ofertas WHERE productor_id IN (SELECT id FROM demo_usuarios);

CREATE TEMP TABLE demo_pedidos ON COMMIT DROP AS
    SELECT id FROM pedidos
    WHERE comprador_id IN (SELECT id FROM demo_usuarios)
       OR oferta_id IN (SELECT id FROM demo_ofertas);

CREATE TEMP TABLE demo_conteo ON COMMIT DROP AS
    SELECT 1 AS orden, 'registros_contacto' AS tabla, count(*) AS antes FROM registros_contacto
    WHERE comprador_id IN (SELECT id FROM demo_usuarios)
       OR oferta_id IN (SELECT id FROM demo_ofertas)
       OR pedido_id IN (SELECT id FROM demo_pedidos)
    UNION ALL SELECT 2, 'pedidos', count(*) FROM demo_pedidos
    UNION ALL SELECT 3, 'ofertas', count(*) FROM demo_ofertas
    UNION ALL SELECT 4, 'negocios', count(*) FROM negocios WHERE usuario_id IN (SELECT id FROM demo_usuarios)
    UNION ALL SELECT 5, 'usuarios', count(*) FROM demo_usuarios
    UNION ALL SELECT 6, 'fincas', count(*) FROM fincas
              WHERE id IN (SELECT finca_id FROM demo_usuarios WHERE finca_id IS NOT NULL);

DELETE FROM registros_contacto
WHERE comprador_id IN (SELECT id FROM demo_usuarios)
   OR oferta_id IN (SELECT id FROM demo_ofertas)
   OR pedido_id IN (SELECT id FROM demo_pedidos);

DELETE FROM pedidos WHERE id IN (SELECT id FROM demo_pedidos);

DELETE FROM ofertas WHERE id IN (SELECT id FROM demo_ofertas);

DELETE FROM negocios WHERE usuario_id IN (SELECT id FROM demo_usuarios);

DELETE FROM usuarios
WHERE correo LIKE '%@campored.demo'
  AND id IN (SELECT id FROM demo_usuarios);

DELETE FROM fincas WHERE id IN (SELECT finca_id FROM demo_usuarios WHERE finca_id IS NOT NULL);

-- Conteo final: despues debe ser 0 en todas las tablas
SELECT c.tabla,
       c.antes,
       CASE c.tabla
           WHEN 'registros_contacto' THEN (SELECT count(*) FROM registros_contacto
                                           WHERE comprador_id IN (SELECT id FROM demo_usuarios)
                                              OR oferta_id IN (SELECT id FROM demo_ofertas)
                                              OR pedido_id IN (SELECT id FROM demo_pedidos))
           WHEN 'pedidos'  THEN (SELECT count(*) FROM pedidos WHERE id IN (SELECT id FROM demo_pedidos))
           WHEN 'ofertas'  THEN (SELECT count(*) FROM ofertas WHERE id IN (SELECT id FROM demo_ofertas))
           WHEN 'negocios' THEN (SELECT count(*) FROM negocios WHERE usuario_id IN (SELECT id FROM demo_usuarios))
           WHEN 'usuarios' THEN (SELECT count(*) FROM usuarios WHERE correo LIKE '%@campored.demo')
           WHEN 'fincas'   THEN (SELECT count(*) FROM fincas
                                 WHERE id IN (SELECT finca_id FROM demo_usuarios WHERE finca_id IS NOT NULL))
       END AS despues
FROM demo_conteo c
ORDER BY c.orden;

COMMIT;
