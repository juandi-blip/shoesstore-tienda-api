package com.shoesstore.tienda.productos.repository;

import com.shoesstore.tienda.productos.model.ProductoTalla;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductoTallaRepository extends JpaRepository<ProductoTalla, Long> {
    List<ProductoTalla> findByProductoId(Long productoId);
    Optional<ProductoTalla> findByProductoIdAndTalla(Long productoId, Double talla);
}
