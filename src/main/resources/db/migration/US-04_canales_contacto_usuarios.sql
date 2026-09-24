-- US-04 — Canales de contacto del productor
-- Ejecución manual en Supabase (SQL Editor) antes de desplegar: producción usa ddl-auto=validate
-- y no arranca si faltan estas columnas. IF NOT EXISTS permite repetirlo sin error.

ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS canal_whatsapp_habilitado BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS canal_llamada_habilitado BOOLEAN NOT NULL DEFAULT false;
