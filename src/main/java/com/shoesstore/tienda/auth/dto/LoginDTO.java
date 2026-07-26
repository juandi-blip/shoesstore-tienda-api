package com.shoesstore.tienda.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginDTO {
    @NotBlank(message = "El nombre de usuario es obligatorio.")
    private String nombreUsuario;
    @NotBlank(message = "La contrasena es obligatoria.")
    private String contrasena;

    public LoginDTO() {}
    public LoginDTO(String nombreUsuario, String contrasena) {
        this.nombreUsuario = nombreUsuario;
        this.contrasena = contrasena;
    }

    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }
    public String getContrasena() { return contrasena; }
    public void setContrasena(String contrasena) { this.contrasena = contrasena; }
}
