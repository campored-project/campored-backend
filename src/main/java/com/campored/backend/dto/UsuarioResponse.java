package com.campored.backend.dto;

import com.campored.backend.entity.Municipio;
import com.campored.backend.entity.Rol;
import com.campored.backend.entity.TipoNegocio;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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
    private LocalDateTime fechaRegistro;
}
