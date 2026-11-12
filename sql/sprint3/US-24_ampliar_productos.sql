-- US-24 — Ampliación del catálogo de productos (Sprint 3)
-- Ejecutar en el SQL Editor de Supabase ANTES de desplegar el backend con el enum ampliado.
-- Solo amplía la lista de valores permitidos: el código desplegado hoy sigue funcionando.
-- Se puede ejecutar de nuevo sin error.

BEGIN;

-- Borra la restricción de valores de producto, sin importar con qué nombre se creó en Supabase
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'ofertas'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%TOMATE_CHONTO%'
    LOOP
        EXECUTE format('ALTER TABLE ofertas DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;

ALTER TABLE ofertas ADD CONSTRAINT chk_ofertas_producto CHECK (producto IN (
    'TOMATE_CHONTO', 'CAFE_PERGAMINO', 'MORA_CASTILLA', 'CILANTRO',
    'FRIJOL_CARGAMANTO', 'AGUACATE', 'PLATANO',
    'ARVEJA_VERDE', 'CACAO', 'CEBOLLA', 'GRANADILLA', 'GULUPA',
    'LIMON_TAHITI', 'LULO', 'MARACUYA', 'PANELA', 'PAPA_CAPIRA',
    'PIMENTON', 'PINA', 'TOMATE_ARBOL', 'UCHUVA', 'YUCA',
    'OTRO'));

COMMIT;

-- Verificación: una sola fila, con los 23 valores
SELECT conname, pg_get_constraintdef(oid) AS definicion
FROM pg_constraint
WHERE conrelid = 'ofertas'::regclass
  AND contype = 'c'
  AND pg_get_constraintdef(oid) LIKE '%TOMATE_CHONTO%';
