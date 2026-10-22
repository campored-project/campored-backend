package com.campored.backend.util;

import com.campored.backend.dto.UsuarioResponse;
import com.campored.backend.entity.Finca;
import com.campored.backend.entity.Negocio;
import com.campored.backend.entity.Usuario;

public final class UsuarioMapper {

    private UsuarioMapper() {
    }

    public static UsuarioResponse toResponse(Usuario usuario) {
        UsuarioResponse.UsuarioResponseBuilder builder = UsuarioResponse.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .correo(usuario.getCorreo())
                .telefono(usuario.getTelefono())
                .whatsapp(usuario.getWhatsapp())
                .canalWhatsappHabilitado(usuario.isCanalWhatsappHabilitado())
                .canalLlamadaHabilitado(usuario.isCanalLlamadaHabilitado())
                .rol(usuario.getRol())
                .fechaRegistro(usuario.getFechaRegistro());

        Finca finca = usuario.getFinca();
        if (finca != null) {
            builder.nombreFinca(finca.getNombreFinca())
                    .municipio(finca.getMunicipio())
                    .vereda(finca.getVereda());
        }

        Negocio negocio = usuario.getNegocio();
        if (negocio != null) {
            builder.nombreNegocio(negocio.getNombreNegocio())
                    .tipoNegocio(negocio.getTipoNegocio())
                    .direccion(negocio.getDireccion())
                    .municipio(negocio.getMunicipio())
                    .horarioRecepcion(negocio.getHorarioRecepcion())
                    .notasAcceso(negocio.getNotasAcceso());
        }
        return builder.build();
    }
}
