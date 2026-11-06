package com.campored.backend.dto;

import com.campored.backend.entity.Municipio;
import com.campored.backend.entity.Rol;
import com.campored.backend.entity.TipoNegocio;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponse {

    private UUID id;
    private String nombre;
    private String correo;
    private String telefono;
    private String whatsapp;
    private boolean canalWhatsappHabilitado;
    private boolean canalLlamadaHabilitado;
    private Rol rol;
    private String nombreFinca;
    private Municipio municipio;
    private String vereda;
    private String nombreNegocio;
    private TipoNegocio tipoNegocio;
    private String direccion;
    private String horarioRecepcion;
    private String notasAcceso;
    @Schema(description = "Fecha y hora de registro en hora de Colombia", example = "2026-11-05T20:27:43.501287-05:00")
    private OffsetDateTime fechaRegistro;
}
