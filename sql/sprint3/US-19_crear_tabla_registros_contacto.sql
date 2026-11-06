-- US-19 — Tabla del historial de contactos del comprador (Sprint 3)
-- Depende de la tabla pedidos: ejecutar DESPUÉS de US-18_crear_tabla_pedidos.sql.
-- Ejecutar manualmente en el SQL Editor de Supabase ANTES de desplegar el código: producción usa
-- ddl-auto=validate y el backend no arranca si la tabla no existe o no coincide con la entidad RegistroContacto.
-- IF NOT EXISTS permite ejecutarlo de nuevo sin error.

CREATE TABLE IF NOT EXISTS registros_contacto (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Sin ON DELETE CASCADE: el historial del comprador no debe desaparecer al borrar un registro relacionado.
    -- US-10 bloquea la eliminación de ofertas con contactos
    comprador_id   UUID NOT NULL REFERENCES usuarios (id),
    oferta_id      UUID NOT NULL REFERENCES ofertas (id),
    -- Opcional: el contacto puede hacerse desde el detalle de la oferta sin pedido
    pedido_id      UUID REFERENCES pedidos (id),
    canal          VARCHAR(20) NOT NULL,
    fecha_contacto TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT chk_registros_contacto_canal CHECK (canal IN ('WHATSAPP', 'LLAMADA'))
);

CREATE INDEX IF NOT EXISTS idx_registros_contacto_comprador_fecha ON registros_contacto (comprador_id, fecha_contacto DESC);
-- Lo usa US-10 para saber si una oferta tiene contactos antes de eliminarla
CREATE INDEX IF NOT EXISTS idx_registros_contacto_oferta_id ON registros_contacto (oferta_id);

-- Sin políticas, RLS bloquea el acceso por la API REST de Supabase (anon/authenticated); el backend
-- se conecta por JDBC con el rol postgres del Session Pooler, que omite RLS.
ALTER TABLE registros_contacto ENABLE ROW LEVEL SECURITY;
