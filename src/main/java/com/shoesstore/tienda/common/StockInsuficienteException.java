package com.shoesstore.tienda.common;

// Se lanza cuando una o mas lineas de un pedido no tienen stock suficiente
// en shoesstore-inventario-api. El manejador global ya sabe traducirla a 409
// (ver ManejadorErrores, Task 2).
public class StockInsuficienteException extends RuntimeException {
    public StockInsuficienteException(String mensaje) { super(mensaje); }
}
