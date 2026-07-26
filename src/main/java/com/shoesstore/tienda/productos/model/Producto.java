package com.shoesstore.tienda.productos.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

// Un modelo de zapatilla del catalogo publico (no una unidad de inventario:
// eso vive en shoesstore-inventario-api, referenciado desde ProductoTalla).
@Entity
@Table(name = "productos")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 60)
    private String marca;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(length = 20)
    private String genero;

    @Column(length = 40)
    private String proposito;

    @Column(length = 40)
    private String subcategoria;

    @Column(length = 80)
    private String colorway;

    private boolean novedad;

    private boolean outlet;

    @Column(length = 500)
    private String imagen;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public String getGenero() { return genero; }
    public void setGenero(String genero) { this.genero = genero; }
    public String getProposito() { return proposito; }
    public void setProposito(String proposito) { this.proposito = proposito; }
    public String getSubcategoria() { return subcategoria; }
    public void setSubcategoria(String subcategoria) { this.subcategoria = subcategoria; }
    public String getColorway() { return colorway; }
    public void setColorway(String colorway) { this.colorway = colorway; }
    public boolean isNovedad() { return novedad; }
    public void setNovedad(boolean novedad) { this.novedad = novedad; }
    public boolean isOutlet() { return outlet; }
    public void setOutlet(boolean outlet) { this.outlet = outlet; }
    public String getImagen() { return imagen; }
    public void setImagen(String imagen) { this.imagen = imagen; }
}
