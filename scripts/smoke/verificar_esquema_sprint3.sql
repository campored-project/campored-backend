-- US-25 — Verificación del esquema del Sprint 3 en Supabase (AB#56)
-- Solo lectura: no modifica nada. Ejecutar en el SQL Editor de Supabase.
--
-- El SQL Editor muestra solo el resultado de la última sentencia. La consulta 4 (resumen) junta todo en una
-- tabla con la columna ok; las consultas 1 a 3 dan el detalle y se pueden ejecutar por separado seleccionándolas.

-- 1. CHECK de pedidos y registros_contacto
SELECT c.conrelid::regclass::text AS tabla, c.conname::text AS restriccion, pg_get_constraintdef(c.oid) AS definicion
FROM pg_constraint c
WHERE c.contype = 'c'
  AND c.conrelid IN (to_regclass('public.pedidos'), to_regclass('public.registros_contacto'))
ORDER BY 1, 2;

-- 2. FK y su regla ON DELETE (ninguna debe ser CASCADE)
SELECT c.conrelid::regclass::text AS tabla,
       c.conname::text AS restriccion,
       a.attname::text AS columna,
       c.confrelid::regclass::text AS referencia,
       CASE c.confdeltype
           WHEN 'a' THEN 'NO ACTION' WHEN 'r' THEN 'RESTRICT' WHEN 'c' THEN 'CASCADE'
           WHEN 'n' THEN 'SET NULL' ELSE 'SET DEFAULT' END AS on_delete
FROM pg_constraint c
JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
WHERE c.contype = 'f'
  AND c.conrelid IN (to_regclass('public.pedidos'), to_regclass('public.registros_contacto'))
ORDER BY 1, 3;

-- 3. Índices y RLS
SELECT i.tablename::text AS tabla, i.indexname::text AS indice, i.indexdef AS definicion
FROM pg_indexes i
WHERE i.schemaname = 'public' AND i.tablename IN ('pedidos', 'registros_contacto')
ORDER BY 1, 2;

SELECT c.relname::text AS tabla, c.relrowsecurity AS rls_habilitado
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relname IN ('pedidos', 'registros_contacto')
ORDER BY 1;

-- 4. Resumen: lo esperado según sql/sprint3/ frente a lo encontrado, más el conteo de filas smoke
WITH
smoke AS (
    SELECT id, finca_id FROM usuarios WHERE correo LIKE 'smoke.%@campored.test'
),
smoke_ofertas AS (
    SELECT id FROM ofertas WHERE productor_id IN (SELECT id FROM smoke)
),
esperado (seccion, tabla, elemento) AS (
    VALUES
        ('CHECK', 'pedidos', 'chk_pedidos_jornada'),
        ('CHECK', 'pedidos', 'chk_pedidos_modalidad_entrega'),
        ('CHECK', 'pedidos', 'chk_pedidos_estado'),
        ('CHECK', 'pedidos', 'chk_pedidos_municipio_entrega'),
        ('CHECK', 'pedidos', 'chk_pedidos_cantidad'),
        ('CHECK', 'registros_contacto', 'chk_registros_contacto_canal'),
        ('FK', 'pedidos', 'oferta_id -> ofertas'),
        ('FK', 'pedidos', 'comprador_id -> usuarios'),
        ('FK', 'registros_contacto', 'comprador_id -> usuarios'),
        ('FK', 'registros_contacto', 'oferta_id -> ofertas'),
        ('FK', 'registros_contacto', 'pedido_id -> pedidos'),
        ('INDEX', 'pedidos', 'idx_pedidos_oferta_id'),
        ('INDEX', 'pedidos', 'idx_pedidos_comprador_fecha'),
        ('INDEX', 'registros_contacto', 'idx_registros_contacto_comprador_fecha'),
        ('INDEX', 'registros_contacto', 'idx_registros_contacto_oferta_id'),
        ('RLS', 'pedidos', 'row level security'),
        ('RLS', 'registros_contacto', 'row level security')
),
encontrado (seccion, tabla, elemento, detalle, ok) AS (
    SELECT 'CHECK', c.conrelid::regclass::text, c.conname::text, pg_get_constraintdef(c.oid), true
    FROM pg_constraint c
    WHERE c.contype = 'c'
      AND c.conrelid IN (to_regclass('public.pedidos'), to_regclass('public.registros_contacto'))
    UNION ALL
    -- Una FK con ON DELETE CASCADE aparece pero con ok = false
    SELECT 'FK', c.conrelid::regclass::text, a.attname::text || ' -> ' || c.confrelid::regclass::text,
           'ON DELETE ' || CASE c.confdeltype
               WHEN 'a' THEN 'NO ACTION' WHEN 'r' THEN 'RESTRICT' WHEN 'c' THEN 'CASCADE'
               WHEN 'n' THEN 'SET NULL' ELSE 'SET DEFAULT' END,
           c.confdeltype <> 'c'
    FROM pg_constraint c
    JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
    WHERE c.contype = 'f'
      AND c.conrelid IN (to_regclass('public.pedidos'), to_regclass('public.registros_contacto'))
    UNION ALL
    SELECT 'INDEX', i.tablename::text, i.indexname::text, i.indexdef, true
    FROM pg_indexes i
    WHERE i.schemaname = 'public' AND i.tablename IN ('pedidos', 'registros_contacto')
    UNION ALL
    SELECT 'RLS', c.relname::text, 'row level security',
           CASE WHEN c.relrowsecurity THEN 'habilitado' ELSE 'deshabilitado' END, c.relrowsecurity
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relname IN ('pedidos', 'registros_contacto')
),
conteo_smoke (tabla, filas) AS (
    SELECT 'usuarios', count(*) FROM smoke
    UNION ALL
    SELECT 'fincas', count(*) FROM fincas WHERE id IN (SELECT finca_id FROM smoke WHERE finca_id IS NOT NULL)
    UNION ALL
    SELECT 'negocios', count(*) FROM negocios WHERE usuario_id IN (SELECT id FROM smoke)
    UNION ALL
    SELECT 'ofertas', count(*) FROM smoke_ofertas
    UNION ALL
    SELECT 'pedidos', count(*) FROM pedidos
    WHERE comprador_id IN (SELECT id FROM smoke) OR oferta_id IN (SELECT id FROM smoke_ofertas)
    UNION ALL
    SELECT 'registros_contacto', count(*) FROM registros_contacto
    WHERE comprador_id IN (SELECT id FROM smoke) OR oferta_id IN (SELECT id FROM smoke_ofertas)
),
resumen (seccion, tabla, elemento, detalle, ok) AS (
    SELECT e.seccion, e.tabla, e.elemento, COALESCE(f.detalle, 'NO EXISTE'), COALESCE(f.ok, false)
    FROM esperado e
    LEFT JOIN encontrado f USING (seccion, tabla, elemento)
    UNION ALL
    SELECT 'SMOKE', s.tabla, 'filas smoke.%@campored.test', s.filas::text, true
    FROM conteo_smoke s
)
SELECT seccion, tabla, elemento, detalle, ok
FROM resumen
ORDER BY CASE seccion WHEN 'CHECK' THEN 1 WHEN 'FK' THEN 2 WHEN 'INDEX' THEN 3 WHEN 'RLS' THEN 4 ELSE 5 END,
         tabla, elemento;
