package com.shoesstore.tienda.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class RegistroDTO {
    @NotBlank(message = "El nombre de usuario es obligatorio.")
    private String nombreUsuario;
    @NotBlank(message = "La contrasena es obligatoria.")
    private String contrasena;
    private String nombreCompleto;
    private String email;

    public RegistroDTO() {}
    public RegistroDTO(String nombreUsuario, String contrasena, String nombreCompleto, String email) {
        this.nombreUsuario = nombreUsuario;
        this.contrasena = contrasena;
        this.nombreCompleto = nombreCompleto;
        this.email = email;
    }

    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }
    public String getContrasena() { return contrasena; }
    public void setContrasena(String contrasena) { this.contrasena = contrasena; }
    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
