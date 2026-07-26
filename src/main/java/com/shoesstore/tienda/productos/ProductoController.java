package com.shoesstore.tienda.productos;

import com.shoesstore.tienda.productos.dto.ProductoDetalleDTO;
import com.shoesstore.tienda.productos.model.Producto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    public List<Producto> listar(@RequestParam(required = false) String genero,
                                  @RequestParam(required = false) String marca,
                                  @RequestParam(required = false) String proposito) {
        return productoService.listar(genero, marca, proposito);
    }

    @GetMapping("/{id}")
    public ProductoDetalleDTO obtener(@PathVariable Long id) {
        return productoService.obtenerDetalle(id);
    }

    @PostMapping
    public ResponseEntity<Producto> crear(@RequestBody Producto producto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productoService.crear(producto));
    }

    @PutMapping("/{id}")
    public Producto actualizar(@PathVariable Long id, @RequestBody Producto producto) {
        return productoService.actualizar(id, producto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        productoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
