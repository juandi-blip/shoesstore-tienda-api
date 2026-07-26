package com.shoesstore.tienda.pedidos.dto;

import java.math.BigDecimal;

public class ItemPedidoDTO {
    private Long productoId;
    private Double talla;
    private Integer cantidad;
    private BigDecimal precioUnitario;

    public ItemPedidoDTO() {}
    public ItemPedidoDTO(Long productoId, Double talla, Integer cantidad, BigDecimal precioUnitario) {
        this.productoId = productoId;
        this.talla = talla;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
    }

    public Long getProductoId() { return productoId; }
    public void setProductoId(Long productoId) { this.productoId = productoId; }
    public Double getTalla() { return talla; }
    public void setTalla(Double talla) { this.talla = talla; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }
}
