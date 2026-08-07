package com.shoesstore.tienda.productos;

import com.shoesstore.tienda.productos.dto.ProductoDetalleDTO;
import com.shoesstore.tienda.productos.dto.ProductoResumenDTO;
import com.shoesstore.tienda.productos.model.Producto;
import com.shoesstore.tienda.productos.model.ProductoTalla;
import com.shoesstore.tienda.productos.repository.ProductoRepository;
import com.shoesstore.tienda.productos.repository.ProductoTallaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock ProductoRepository productoRepository;
    @Mock ProductoTallaRepository productoTallaRepository;
    @Mock InventarioClient inventarioClient;

    @Test
    void detalleMarcaTallaComoNoDisponibleSiElInventarioNoTieneStock() {
        ProductoService service = new ProductoService(productoRepository, productoTallaRepository, inventarioClient);

        Producto producto = new Producto();
        producto.setId(1L);
        producto.setNombre("Air Force 1 '07");
        producto.setPrecio(new BigDecimal("115"));

        ProductoTalla talla9 = new ProductoTalla();
        talla9.setTalla(9.0);
        talla9.setIdProductoInventario(101L);
        ProductoTalla talla10 = new ProductoTalla();
        talla10.setTalla(10.0);
        talla10.setIdProductoInventario(102L);

        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));
        when(productoTallaRepository.findByProductoId(1L)).thenReturn(List.of(talla9, talla10));
        when(inventarioClient.consultarStock(101L)).thenReturn(0);
        when(inventarioClient.consultarStock(102L)).thenReturn(5);

        ProductoDetalleDTO detalle = service.obtenerDetalle(1L);

        assertFalse(detalle.getTallas().get(0).isDisponible());
        assertTrue(detalle.getTallas().get(1).isDisponible());
    }

    @Test
    void detalleExponeRutaDeImagenPropiaEnVezDeLaUrlAlmacenada() {
        ProductoService service = new ProductoService(productoRepository, productoTallaRepository, inventarioClient);

        Producto producto = new Producto();
        producto.setId(7L);
        producto.setImagen("https://static.sneakerjagers.com/products/660x660/999999.jpg");

        when(productoRepository.findById(7L)).thenReturn(Optional.of(producto));
        when(productoTallaRepository.findByProductoId(7L)).thenReturn(List.of());

        ProductoDetalleDTO detalle = service.obtenerDetalle(7L);

        assertEquals("/api/imagenes/producto/7", detalle.getImagen());
    }

    @Test
    void listarExponeRutaDeImagenPropiaParaCadaProducto() {
        ProductoService service = new ProductoService(productoRepository, productoTallaRepository, inventarioClient);

        Producto producto = new Producto();
        producto.setId(3L);
        producto.setImagen("https://static.sneakerjagers.com/products/660x660/111111.jpg");

        when(productoRepository.findAll()).thenReturn(List.of(producto));

        List<ProductoResumenDTO> lista = service.listar(null, null, null);

        assertEquals(1, lista.size());
        assertEquals("/api/imagenes/producto/3", lista.get(0).getImagen());
    }
}
