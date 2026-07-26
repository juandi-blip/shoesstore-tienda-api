package com.shoesstore.tienda.productos.model;

import jakarta.persistence.*;

// Una talla concreta de un producto, ligada a su variante real en el
// sistema de inventario (shoesstore-inventario-api) via idProductoInventario.
@Entity
@Table(name = "producto_tallas")
public class ProductoTalla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private Double talla;

    @Column(name = "id_producto_inventario", nullable = false)
    private Long idProductoInventario;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) { this.producto = producto; }
    public Double getTalla() { return talla; }
    public void setTalla(Double talla) { this.talla = talla; }
    public Long getIdProductoInventario() { return idProductoInventario; }
    public void setIdProductoInventario(Long idProductoInventario) { this.idProductoInventario = idProductoInventario; }
}
