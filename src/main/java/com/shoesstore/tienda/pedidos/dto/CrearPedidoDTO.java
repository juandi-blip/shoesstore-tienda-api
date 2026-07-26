package com.shoesstore.tienda.pedidos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public class CrearPedidoDTO {
    @NotBlank(message = "El metodo de pago es obligatorio.")
    private String metodoPago;
    private String banco;

    @NotNull(message = "El costo de envio es obligatorio.")
    @PositiveOrZero(message = "El costo de envio no puede ser negativo.")
    private BigDecimal envioCop = BigDecimal.ZERO;

    @NotEmpty(message = "El pedido debe tener al menos un producto.")
    @Valid
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
