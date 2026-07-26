package com.shoesstore.tienda.auth.dto;

import java.time.LocalDateTime;

public class PerfilDTO {
    private String nombreUsuario;
    private String nombreCompleto;
    private String email;
    private LocalDateTime fechaRegistro;

    public PerfilDTO(String nombreUsuario, String nombreCompleto, String email, LocalDateTime fechaRegistro) {
        this.nombreUsuario = nombreUsuario;
        this.nombreCompleto = nombreCompleto;
        this.email = email;
        this.fechaRegistro = fechaRegistro;
    }

    public String getNombreUsuario() { return nombreUsuario; }
    public String getNombreCompleto() { return nombreCompleto; }
    public String getEmail() { return email; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
}
