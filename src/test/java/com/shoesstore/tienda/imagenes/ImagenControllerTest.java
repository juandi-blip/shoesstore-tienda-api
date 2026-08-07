package com.shoesstore.tienda.imagenes;

import com.shoesstore.tienda.productos.model.Producto;
import com.shoesstore.tienda.productos.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImagenControllerTest {

    @Mock ProductoRepository productoRepository;

    @Test
    void devuelveSvgConMarcaYNombreDelProductoEncontrado() {
        ImagenController controller = new ImagenController(productoRepository, new ImagenService());
        Producto producto = new Producto();
        producto.setId(1L);
        producto.setMarca("Nike");
        producto.setNombre("Air Force 1 '07");
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));

        String svg = controller.imagenProducto(1L);

        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.contains("NIKE"));
    }

    @Test
    void lanzaNoSuchElementExceptionSiElProductoNoExiste() {
        ImagenController controller = new ImagenController(productoRepository, new ImagenService());
        when(productoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> controller.imagenProducto(99L));
    }
}
