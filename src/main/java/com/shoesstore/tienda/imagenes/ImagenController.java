package com.shoesstore.tienda.imagenes;

import com.shoesstore.tienda.productos.model.Producto;
import com.shoesstore.tienda.productos.repository.ProductoRepository;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

// Endpoint publico (sin auth, ver TokenAuthFilter): genera la imagen de un
// producto en el momento, sin almacenar ni referenciar nada de terceros.
@RestController
@RequestMapping(ImagenController.RUTA_BASE)
public class ImagenController {

    public static final String RUTA_BASE = "/api/imagenes";

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
