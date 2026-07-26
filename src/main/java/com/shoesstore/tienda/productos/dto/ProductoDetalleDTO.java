package com.shoesstore.tienda.productos.dto;

import java.math.BigDecimal;
import java.util.List;

public class ProductoDetalleDTO {
    private Long id;
    private String nombre;
    private String marca;
    private BigDecimal precio;
    private String genero;
    private String proposito;
    private String subcategoria;
    private String colorway;
    private boolean novedad;
    private boolean outlet;
    private String imagen;
    private List<TallaDisponibleDTO> tallas;

    public ProductoDetalleDTO(Long id, String nombre, String marca, BigDecimal precio, String genero,
                               String proposito, String subcategoria, String colorway, boolean novedad,
                               boolean outlet, String imagen, List<TallaDisponibleDTO> tallas) {
        this.id = id;
        this.nombre = nombre;
        this.marca = marca;
        this.precio = precio;
        this.genero = genero;
        this.proposito = proposito;
        this.subcategoria = subcategoria;
        this.colorway = colorway;
        this.novedad = novedad;
        this.outlet = outlet;
        this.imagen = imagen;
        this.tallas = tallas;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getMarca() { return marca; }
    public BigDecimal getPrecio() { return precio; }
    public String getGenero() { return genero; }
    public String getProposito() { return proposito; }
    public String getSubcategoria() { return subcategoria; }
    public String getColorway() { return colorway; }
    public boolean isNovedad() { return novedad; }
    public boolean isOutlet() { return outlet; }
    public String getImagen() { return imagen; }
    public List<TallaDisponibleDTO> getTallas() { return tallas; }
}
