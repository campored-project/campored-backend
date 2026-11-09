-- US-25 — Verificación de los datos demo cargados por scripts/demo/seed-demo.mjs (AB#56)
-- Solo lectura. Ejecutar en el SQL Editor de Supabase después del seed: todas las filas deben salir con ok = true.
-- Cuenta solo lo que cuelga de usuarios con correo LIKE '%@campored.demo'; no mira usuarios smoke ni reales.
-- Los esperados salen de scripts/demo/datos-demo.json: si cambian los datos, hay que cambiarlos aquí.
-- El SQL Editor muestra solo el último resultado: todo sale en una consulta con dato, esperado, obtenido y ok.

WITH productores (orden, clave, correo, ofertas, pedidos, eliminables) AS (
    -- ofertas = municipio de la finca y "activas DISPONIBLE / activas EN_CRECIMIENTO / agotadas"
    VALUES
        (1, 'P1', 'luis.ospina@campored.demo',    'SONSON 2/1/0',     '2', '1'),
        (2, 'P2', 'gloria.arango@campored.demo',  'SONSON 2/2/0',     '1', '2'),
        (3, 'P3', 'jhon.marin@campored.demo',     'SONSON 1/2/1',     '1', '2'),
        (4, 'P4', 'maria.restrepo@campored.demo', 'SONSON 2/1/1',     '1', '2'),
        (5, 'P5', 'hernan.giraldo@campored.demo', 'SAN_CARLOS 2/1/0', '2', '1'),
        (6, 'P6', 'luz.quintero@campored.demo',   'SAN_CARLOS 2/1/1', '1', '2'),
        (7, 'P7', 'carlos.zuluaga@campored.demo', 'SAN_CARLOS 2/1/0', '1', '2'),
        (8, 'P8', 'diana.cardona@campored.demo',  'SAN_CARLOS 1/3/0', '0', '3')
),
compradores (orden, clave, correo, pedidos, contactos) AS (
    -- contactos = "WHATSAPP / LLAMADA"
    VALUES
        (1, 'C1', 'compras.fogonpaisa@campored.demo',      '3', '3/0'),
        (2, 'C2', 'compras.elsurtidor@campored.demo',      '3', '3/0'),
        (3, 'C3', 'compras.laesquina@campored.demo',       '1', '1/1'),
        (4, 'C4', 'compras.lacosecha@campored.demo',       '1', '0/2'),
        (5, 'C5', 'compras.saboresdelaloza@campored.demo', '1', '1/1'),
        (6, 'C6', 'compras.donarosa@campored.demo',        '0', '0/0')
),
demo_usuarios AS (
    SELECT id, correo, rol, finca_id, contrasena_hash FROM usuarios WHERE correo LIKE '%@campored.demo'
),
demo_ofertas AS (
    SELECT o.id, o.productor_id, o.activa, o.estado_cultivo, f.municipio
    FROM ofertas o
    JOIN fincas f ON f.id = o.finca_id
    WHERE o.productor_id IN (SELECT id FROM demo_usuarios)
),
demo_pedidos AS (
    SELECT id, oferta_id, comprador_id FROM pedidos
    WHERE comprador_id IN (SELECT id FROM demo_usuarios)
       OR oferta_id IN (SELECT id FROM demo_ofertas)
),
demo_contactos AS (
    SELECT id, oferta_id, comprador_id, pedido_id, canal FROM registros_contacto
    WHERE comprador_id IN (SELECT id FROM demo_usuarios)
       OR oferta_id IN (SELECT id FROM demo_ofertas)
),
-- Activa y sin pedidos ni contactos de nadie: DELETE /api/ofertas/{id} responde 204 (US-10)
eliminables AS (
    SELECT o.id, o.productor_id FROM demo_ofertas o
    WHERE o.activa
      AND NOT EXISTS (SELECT 1 FROM pedidos p WHERE p.oferta_id = o.id)
      AND NOT EXISTS (SELECT 1 FROM registros_contacto r WHERE r.oferta_id = o.id)
),
-- Los nombres de columna salen de los alias de la primera rama del UNION
filas AS (
    SELECT 1 AS grupo, 1 AS orden, 'usuarios @campored.demo' AS dato, '14' AS esperado,
           CAST((SELECT count(*) FROM demo_usuarios) AS VARCHAR) AS obtenido
    UNION ALL
    SELECT 1, 2, 'usuarios demo PRODUCTOR', '8',
           CAST((SELECT count(*) FROM demo_usuarios WHERE rol = 'PRODUCTOR') AS VARCHAR)
    UNION ALL
    SELECT 1, 3, 'usuarios demo COMPRADOR', '6',
           CAST((SELECT count(*) FROM demo_usuarios WHERE rol = 'COMPRADOR') AS VARCHAR)
    UNION ALL
    SELECT 1, 4, 'usuarios demo con hash BCrypt $2a$10$', '14',
           CAST((SELECT count(*) FROM demo_usuarios WHERE contrasena_hash LIKE '$2a$10$%') AS VARCHAR)
    UNION ALL
    SELECT 1, 5, 'fincas', '8',
           CAST((SELECT count(*) FROM fincas WHERE id IN (SELECT finca_id FROM demo_usuarios)) AS VARCHAR)
    UNION ALL
    SELECT 1, 6, 'negocios', '6',
           CAST((SELECT count(*) FROM negocios WHERE usuario_id IN (SELECT id FROM demo_usuarios)) AS VARCHAR)
    UNION ALL
    SELECT 1, 7, 'ofertas', '29', CAST((SELECT count(*) FROM demo_ofertas) AS VARCHAR)
    UNION ALL
    SELECT 1, 8, 'ofertas activas', '26', CAST((SELECT count(*) FROM demo_ofertas WHERE activa) AS VARCHAR)
    UNION ALL
    SELECT 1, 9, 'ofertas agotadas', '3', CAST((SELECT count(*) FROM demo_ofertas WHERE NOT activa) AS VARCHAR)
    UNION ALL
    SELECT 1, 10, 'pedidos', '9', CAST((SELECT count(*) FROM demo_pedidos) AS VARCHAR)
    UNION ALL
    SELECT 1, 11, 'pedidos sobre ofertas ya agotadas (el historial se conserva)', '1',
           CAST((SELECT count(*) FROM demo_pedidos d JOIN demo_ofertas o ON o.id = d.oferta_id
                 WHERE NOT o.activa) AS VARCHAR)
    UNION ALL
    SELECT 1, 12, 'registros_contacto', '12', CAST((SELECT count(*) FROM demo_contactos) AS VARCHAR)
    UNION ALL
    SELECT 1, 13, 'registros_contacto con pedido', '9',
           CAST((SELECT count(*) FROM demo_contactos WHERE pedido_id IS NOT NULL) AS VARCHAR)
    UNION ALL
    SELECT 1, 14, 'registros_contacto sin pedido (llamada desde el detalle)', '3',
           CAST((SELECT count(*) FROM demo_contactos WHERE pedido_id IS NULL) AS VARCHAR)
    UNION ALL
    SELECT 2, 1, 'ofertas SONSON (activas/agotadas)', '13/2',
           CAST((SELECT count(*) FROM demo_ofertas WHERE municipio = 'SONSON' AND activa) AS VARCHAR) || '/'
           || CAST((SELECT count(*) FROM demo_ofertas WHERE municipio = 'SONSON' AND NOT activa) AS VARCHAR)
    UNION ALL
    SELECT 2, 2, 'ofertas SAN_CARLOS (activas/agotadas)', '13/1',
           CAST((SELECT count(*) FROM demo_ofertas WHERE municipio = 'SAN_CARLOS' AND activa) AS VARCHAR) || '/'
           || CAST((SELECT count(*) FROM demo_ofertas WHERE municipio = 'SAN_CARLOS' AND NOT activa) AS VARCHAR)
    UNION ALL
    SELECT 3, p.orden, 'ofertas ' || p.clave || ' (municipio disponibles/en crecimiento/agotadas)', p.ofertas,
           COALESCE((SELECT f.municipio FROM fincas f WHERE f.id = u.finca_id), '-') || ' '
           || CAST((SELECT count(*) FROM demo_ofertas o
                    WHERE o.productor_id = u.id AND o.activa AND o.estado_cultivo = 'DISPONIBLE') AS VARCHAR) || '/'
           || CAST((SELECT count(*) FROM demo_ofertas o
                    WHERE o.productor_id = u.id AND o.activa AND o.estado_cultivo = 'EN_CRECIMIENTO') AS VARCHAR) || '/'
           || CAST((SELECT count(*) FROM demo_ofertas o WHERE o.productor_id = u.id AND NOT o.activa) AS VARCHAR)
    FROM productores p
    LEFT JOIN demo_usuarios u ON u.correo = p.correo
    UNION ALL
    SELECT 4, p.orden, 'pedidos recibidos ' || p.clave, p.pedidos,
           CAST((SELECT count(*) FROM demo_pedidos d JOIN demo_ofertas o ON o.id = d.oferta_id
                 WHERE o.productor_id = u.id) AS VARCHAR)
    FROM productores p
    LEFT JOIN demo_usuarios u ON u.correo = p.correo
    UNION ALL
    SELECT 5, p.orden, 'ofertas eliminables ' || p.clave || ' (activas, sin pedidos ni contactos)', p.eliminables,
           CAST((SELECT count(*) FROM eliminables e WHERE e.productor_id = u.id) AS VARCHAR)
    FROM productores p
    LEFT JOIN demo_usuarios u ON u.correo = p.correo
    UNION ALL
    SELECT 5, 9, 'ofertas eliminables en total', '15', CAST((SELECT count(*) FROM eliminables) AS VARCHAR)
    UNION ALL
    SELECT 6, c.orden, 'pedidos hechos por ' || c.clave, c.pedidos,
           CAST((SELECT count(*) FROM demo_pedidos d WHERE d.comprador_id = u.id) AS VARCHAR)
    FROM compradores c
    LEFT JOIN demo_usuarios u ON u.correo = c.correo
    UNION ALL
    SELECT 7, c.orden, 'contactos ' || c.clave || ' (WHATSAPP/LLAMADA)', c.contactos,
           CAST((SELECT count(*) FROM demo_contactos r WHERE r.comprador_id = u.id AND r.canal = 'WHATSAPP') AS VARCHAR)
           || '/'
           || CAST((SELECT count(*) FROM demo_contactos r WHERE r.comprador_id = u.id AND r.canal = 'LLAMADA') AS VARCHAR)
    FROM compradores c
    LEFT JOIN demo_usuarios u ON u.correo = c.correo
)
SELECT dato, esperado, obtenido, esperado = obtenido AS ok
FROM filas
ORDER BY grupo, orden;
