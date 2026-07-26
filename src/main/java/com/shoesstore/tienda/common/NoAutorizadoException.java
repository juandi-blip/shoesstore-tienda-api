package com.shoesstore.tienda.common;

// Se lanza cuando faltan credenciales validas o un token invalido/expirado.
public class NoAutorizadoException extends RuntimeException {
    public NoAutorizadoException(String mensaje) { super(mensaje); }
}
