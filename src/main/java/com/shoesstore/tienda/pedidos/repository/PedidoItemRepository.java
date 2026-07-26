package com.shoesstore.tienda.pedidos.repository;

import com.shoesstore.tienda.pedidos.model.PedidoItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoItemRepository extends JpaRepository<PedidoItem, Long> {
}
