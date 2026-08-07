package com.shoesstore.tienda.productos.dto;

import java.math.BigDecimal;

// Forma de un producto en el listado del catalogo (sin tallas: eso solo se
// resuelve en el detalle, porque implica una llamada al inventario por talla).
public class ProductoResumenDTO {
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

    public ProductoResumenDTO(Long id, String nombre, String marca, BigDecimal precio, String genero,
                               String proposito, String subcategoria, String colorway, boolean novedad,
                               boolean outlet, String imagen) {
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
}
