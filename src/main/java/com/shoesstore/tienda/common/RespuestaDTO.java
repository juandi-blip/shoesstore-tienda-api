package com.shoesstore.tienda.common;

// Envoltorio simple para mensajes de exito/error en las respuestas JSON.
public class RespuestaDTO {
    private String mensaje;

    public RespuestaDTO(String mensaje) { this.mensaje = mensaje; }
    public String getMensaje() { return mensaje; }
}
