package com.shoesstore.tienda.productos.repository;

import com.shoesstore.tienda.productos.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
}
