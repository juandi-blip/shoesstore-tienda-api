package com.shoesstore.tienda.common;

// Se lanza cuando shoesstore-inventario-api no responde, responde con error, o
// devuelve un cuerpo con forma inesperada (p. ej. sin la clave "stock"). El
// manejador global la traduce a 503 (ver ManejadorErrores).
public class InventarioNoDisponibleException extends RuntimeException {
    public InventarioNoDisponibleException(String mensaje) { super(mensaje); }
}
