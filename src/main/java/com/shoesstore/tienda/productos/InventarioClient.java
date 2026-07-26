package com.shoesstore.tienda.productos;

import com.shoesstore.tienda.common.InventarioNoDisponibleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

// Unico punto de contacto con shoesstore-inventario-api. Nadie mas en esta
// aplicacion llama directamente a ese servicio.
@Component
public class InventarioClient {

    private final RestClient restClient;

    public InventarioClient(@Value("${inventario.api.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** Consulta el stock actual de una variante (fila) del inventario. */
    public int consultarStock(Long idProductoInventario) {
        Map<?, ?> producto;
        try {
            producto = restClient.get()
                    .uri("/api/productos/{id}", idProductoInventario)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException ex) {
            throw new InventarioNoDisponibleException(
                    "No se pudo consultar el inventario. Intenta de nuevo mas tarde.");
        }
        if (producto == null) {
            return 0;
        }
        Object stock = producto.get("stock");
        if (!(stock instanceof Number)) {
            throw new InventarioNoDisponibleException(
                    "Respuesta invalida del servicio de inventario.");
        }
        return ((Number) stock).intValue();
    }

    /** Actualiza el stock de una variante del inventario (descuento tras un pedido). */
    @SuppressWarnings("unchecked")
    public void descontarStock(Long idProductoInventario, int nuevoStock) {
        Map<String, Object> producto;
        try {
            producto = (Map<String, Object>) restClient.get()
                    .uri("/api/productos/{id}", idProductoInventario)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException ex) {
            throw new InventarioNoDisponibleException(
                    "No se pudo consultar el inventario. Intenta de nuevo mas tarde.");
        }
        if (producto == null) {
            throw new java.util.NoSuchElementException("Producto de inventario no encontrado: " + idProductoInventario);
        }
        // Se envia el objeto completo de vuelta con el stock actualizado, porque
        // el PUT de shoesstore-inventario-api espera el recurso entero (ver su
        // ProductoController: no tiene un endpoint parcial de solo-stock).
        var actualizado = new java.util.HashMap<>(producto);
        actualizado.put("stock", nuevoStock);
        try {
            restClient.put()
                    .uri("/api/productos/{id}", idProductoInventario)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(actualizado)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new InventarioNoDisponibleException(
                    "No se pudo actualizar el inventario. Intenta de nuevo mas tarde.");
        }
    }
}
