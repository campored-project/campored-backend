-- US-11 / US-13 — Índices para el catálogo público de ofertas (Sprint 2)
-- Ejecutar manualmente en Supabase SQL Editor: producción usa ddl-auto=validate y Hibernate no crea índices.
-- IF NOT EXISTS permite ejecutarlo de nuevo sin error.
-- Ya existen (US-06): idx_ofertas_finca_id, idx_ofertas_productor_id e idx_ofertas_activa_estado,
-- este último cubre el filtro por estado_cultivo sobre ofertas activas.

-- Orden por defecto del catálogo: más recientes primero, solo ofertas activas
CREATE INDEX IF NOT EXISTS idx_ofertas_activas_fecha_creacion
    ON ofertas (fecha_creacion DESC)
    WHERE activa = true;

CREATE INDEX IF NOT EXISTS idx_ofertas_activas_producto
    ON ofertas (producto)
    WHERE activa = true;

CREATE INDEX IF NOT EXISTS idx_ofertas_activas_modalidad_logistica
    ON ofertas (modalidad_logistica)
    WHERE activa = true;

-- Filtro por municipio: el catálogo llega a fincas por ofertas.finca_id
CREATE INDEX IF NOT EXISTS idx_fincas_municipio
    ON fincas (municipio);
