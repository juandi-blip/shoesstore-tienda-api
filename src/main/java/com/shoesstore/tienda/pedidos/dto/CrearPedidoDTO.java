package com.shoesstore.tienda.pedidos.dto;

import java.math.BigDecimal;
import java.util.List;

public class CrearPedidoDTO {
    private String metodoPago;
    private String banco;
    private BigDecimal envioCop = BigDecimal.ZERO;
    private List<ItemPedidoDTO> items;

    public String getMetodoPago() { return metodoPago; }
    public void setMetodoPago(String metodoPago) { this.metodoPago = metodoPago; }
    public String getBanco() { return banco; }
    public void setBanco(String banco) { this.banco = banco; }
    public BigDecimal getEnvioCop() { return envioCop; }
    public void setEnvioCop(BigDecimal envioCop) { this.envioCop = envioCop; }
    public List<ItemPedidoDTO> getItems() { return items; }
    public void setItems(List<ItemPedidoDTO> items) { this.items = items; }
}
