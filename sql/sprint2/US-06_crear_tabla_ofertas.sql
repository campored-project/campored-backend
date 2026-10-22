-- US-06 — Tabla de ofertas del productor (Sprint 2)
-- Referencia histórica: la tabla ya fue creada manualmente en Supabase (SQL Editor).
-- Producción usa ddl-auto=validate, así que este esquema debe coincidir con la entidad Oferta.
-- IF NOT EXISTS permite ejecutarlo de nuevo sin error.

CREATE TABLE IF NOT EXISTS ofertas (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    productor_id           UUID NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    finca_id               UUID NOT NULL REFERENCES fincas (id) ON DELETE CASCADE,
    producto               VARCHAR(30) NOT NULL,
    producto_otro          VARCHAR(100),
    descripcion            VARCHAR(500),
    cantidad_disponible    NUMERIC(12, 2) NOT NULL,
    unidad_medida          VARCHAR(30) NOT NULL,
    precio_unitario        NUMERIC(12, 2) NOT NULL,
    estado_cultivo         VARCHAR(30) NOT NULL,
    fecha_estimada_cosecha DATE,
    modalidad_logistica    VARCHAR(30) NOT NULL,
    activa                 BOOLEAN NOT NULL DEFAULT true,
    fecha_creacion         TIMESTAMP NOT NULL DEFAULT now(),
    fecha_actualizacion    TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT chk_ofertas_producto CHECK (producto IN (
        'TOMATE_CHONTO', 'CAFE_PERGAMINO', 'MORA_CASTILLA', 'CILANTRO',
        'FRIJOL_CARGAMANTO', 'AGUACATE', 'PLATANO', 'OTRO')),
    CONSTRAINT chk_ofertas_unidad_medida CHECK (unidad_medida IN (
        'KILOGRAMO', 'ARROBA', 'CARGA', 'BULTO', 'CANASTILLA', 'TONELADA', 'UNIDAD')),
    CONSTRAINT chk_ofertas_estado_cultivo CHECK (estado_cultivo IN ('EN_CRECIMIENTO', 'DISPONIBLE')),
    CONSTRAINT chk_ofertas_modalidad_logistica CHECK (modalidad_logistica IN (
        'RECOGIDA_FINCA', 'DESPACHO_PRODUCTOR', 'AMBAS')),
    CONSTRAINT chk_ofertas_cantidad CHECK (cantidad_disponible > 0),
    CONSTRAINT chk_ofertas_precio CHECK (precio_unitario >= 1),
    CONSTRAINT chk_ofertas_fecha_cosecha CHECK (
        estado_cultivo <> 'DISPONIBLE' OR fecha_estimada_cosecha IS NULL),
    CONSTRAINT chk_ofertas_producto_otro CHECK (
        producto = 'OTRO' OR producto_otro IS NULL)
);

CREATE INDEX IF NOT EXISTS idx_ofertas_productor_id ON ofertas (productor_id);
CREATE INDEX IF NOT EXISTS idx_ofertas_finca_id ON ofertas (finca_id);
CREATE INDEX IF NOT EXISTS idx_ofertas_activa_estado ON ofertas (activa, estado_cultivo);

-- Sin políticas, RLS bloquea el acceso por la API REST de Supabase (anon/authenticated); el backend
-- se conecta por JDBC con el rol postgres del Session Pooler, que omite RLS.
ALTER TABLE ofertas ENABLE ROW LEVEL SECURITY;
