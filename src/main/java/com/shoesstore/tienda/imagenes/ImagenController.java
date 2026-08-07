package com.shoesstore.tienda.imagenes;

import com.shoesstore.tienda.productos.model.Producto;
import com.shoesstore.tienda.productos.repository.ProductoRepository;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

// Endpoint publico (sin auth, ver TokenAuthFilter): genera la imagen de un
// producto en el momento, sin almacenar ni referenciar nada de terceros.
@RestController
@RequestMapping("/api/imagenes")
public class ImagenController {

    private final ProductoRepository productoRepository;
    private final ImagenService imagenService;

    public ImagenController(ProductoRepository productoRepository, ImagenService imagenService) {
        this.productoRepository = productoRepository;
        this.imagenService = imagenService;
    }

    @GetMapping(value = "/producto/{id}", produces = "image/svg+xml")
    public String imagenProducto(@PathVariable Long id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Producto no encontrado."));
        return imagenService.generarSvg(producto.getMarca(), producto.getNombre());
    }
}
