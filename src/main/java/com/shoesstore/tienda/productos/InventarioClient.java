package com.shoesstore.tienda.productos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
        Map<?, ?> producto = restClient.get()
                .uri("/api/productos/{id}", idProductoInventario)
                .retrieve()
                .body(Map.class);
        return producto == null ? 0 : ((Number) producto.get("stock")).intValue();
    }

    /** Actualiza el stock de una variante del inventario (descuento tras un pedido). */
    @SuppressWarnings("unchecked")
    public void descontarStock(Long idProductoInventario, int nuevoStock) {
        Map<String, Object> producto = (Map<String, Object>) restClient.get()
                .uri("/api/productos/{id}", idProductoInventario)
                .retrieve()
                .body(Map.class);
        // Se envia el objeto completo de vuelta con el stock actualizado, porque
        // el PUT de shoesstore-inventario-api espera el recurso entero (ver su
        // ProductoController: no tiene un endpoint parcial de solo-stock).
        var actualizado = new java.util.HashMap<>(producto);
        actualizado.put("stock", nuevoStock);
        restClient.put()
                .uri("/api/productos/{id}", idProductoInventario)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(actualizado)
                .retrieve()
                .toBodilessEntity();
    }
}
