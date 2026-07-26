package com.shoesstore.tienda.productos.dto;

public class TallaDisponibleDTO {
    private Double talla;
    private boolean disponible;

    public TallaDisponibleDTO(Double talla, boolean disponible) {
        this.talla = talla;
        this.disponible = disponible;
    }

    public Double getTalla() { return talla; }
    public boolean isDisponible() { return disponible; }
}
