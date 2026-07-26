package com.shoesstore.tienda.auth.dto;

// Respuesta de login/registro exitoso: el token que el cliente debe reenviar.
public class SesionDTO {
    private String token;
    private String nombreUsuario;

    public SesionDTO(String token, String nombreUsuario) {
        this.token = token;
        this.nombreUsuario = nombreUsuario;
    }

    public String getToken() { return token; }
    public String getNombreUsuario() { return nombreUsuario; }
}
