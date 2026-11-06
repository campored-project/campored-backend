-- US-18 — Tabla de pedidos del comprador (Sprint 3)
-- Ejecutar manualmente en el SQL Editor de Supabase ANTES de desplegar el código: producción usa
-- ddl-auto=validate y el backend no arranca si la tabla no existe o no coincide con la entidad Pedido.
-- IF NOT EXISTS permite ejecutarlo de nuevo sin error.

CREATE TABLE IF NOT EXISTS pedidos (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Sin ON DELETE CASCADE: el historial de pedidos no debe desaparecer al borrar una oferta o un usuario.
    -- US-10 bloquea la eliminación de ofertas con pedidos
    oferta_id             UUID NOT NULL REFERENCES ofertas (id),
    comprador_id          UUID NOT NULL REFERENCES usuarios (id),
    cantidad_solicitada   NUMERIC(12, 2) NOT NULL,
    precio_unitario       NUMERIC(12, 2) NOT NULL,
    precio_total_estimado NUMERIC(14, 2) NOT NULL,
    direccion_entrega     VARCHAR(200) NOT NULL,
    municipio_entrega     VARCHAR(40) NOT NULL,
    fecha_preferida       DATE NOT NULL,
    jornada               VARCHAR(10) NOT NULL,
    modalidad_entrega     VARCHAR(30) NOT NULL,
    notas_acceso          VARCHAR(500),
    estado                VARCHAR(20) NOT NULL DEFAULT 'SOLICITADO',
    fecha_creacion        TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT chk_pedidos_jornada CHECK (jornada IN ('MANANA', 'TARDE')),
    -- AMBAS es una opción de la oferta, nunca la modalidad de un pedido
    CONSTRAINT chk_pedidos_modalidad_entrega CHECK (modalidad_entrega IN ('RECOGIDA_FINCA', 'DESPACHO_PRODUCTOR')),
    CONSTRAINT chk_pedidos_estado CHECK (estado IN ('SOLICITADO')),
    CONSTRAINT chk_pedidos_municipio_entrega CHECK (municipio_entrega IN (
        'ABEJORRAL', 'ALEJANDRIA', 'ARGELIA', 'COCORNA', 'CONCEPCION', 'EL_CARMEN_DE_VIBORAL',
        'EL_PENOL', 'EL_RETIRO', 'EL_SANTUARIO', 'GRANADA', 'GUARNE', 'GUATAPE',
        'LA_CEJA', 'LA_UNION', 'MARINILLA', 'MEDELLIN', 'NARINO', 'RIONEGRO',
        'SAN_CARLOS', 'SAN_FRANCISCO', 'SAN_LUIS', 'SAN_RAFAEL', 'SAN_VICENTE_FERRER', 'SONSON')),
    CONSTRAINT chk_pedidos_cantidad CHECK (cantidad_solicitada > 0)
);

CREATE INDEX IF NOT EXISTS idx_pedidos_oferta_id ON pedidos (oferta_id);
CREATE INDEX IF NOT EXISTS idx_pedidos_comprador_fecha ON pedidos (comprador_id, fecha_creacion DESC);

-- Sin políticas, RLS bloquea el acceso por la API REST de Supabase (anon/authenticated); el backend
-- se conecta por JDBC con el rol postgres del Session Pooler, que omite RLS.
ALTER TABLE pedidos ENABLE ROW LEVEL SECURITY;
