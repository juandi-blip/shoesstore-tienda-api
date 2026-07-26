package com.shoesstore.tienda.pedidos;

import com.shoesstore.tienda.common.StockInsuficienteException;
import com.shoesstore.tienda.pedidos.dto.CrearPedidoDTO;
import com.shoesstore.tienda.pedidos.dto.ItemPedidoDTO;
import com.shoesstore.tienda.pedidos.repository.PedidoItemRepository;
import com.shoesstore.tienda.pedidos.repository.PedidoRepository;
import com.shoesstore.tienda.productos.InventarioClient;
import com.shoesstore.tienda.productos.model.ProductoTalla;
import com.shoesstore.tienda.productos.repository.ProductoTallaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock PedidoRepository pedidoRepository;
    @Mock PedidoItemRepository pedidoItemRepository;
    @Mock ProductoTallaRepository productoTallaRepository;
    @Mock InventarioClient inventarioClient;

    @Test
    void rechazaElPedidoCompletoSiUnaSolaLineaNoTieneStockYNoDescuentaNada() {
        PedidoService service = new PedidoService(pedidoRepository, pedidoItemRepository,
                productoTallaRepository, inventarioClient);

        ProductoTalla tallaConStock = new ProductoTalla();
        tallaConStock.setIdProductoInventario(101L);
        ProductoTalla tallaSinStock = new ProductoTalla();
        tallaSinStock.setIdProductoInventario(102L);

        when(productoTallaRepository.findByProductoIdAndTalla(1L, 9.0)).thenReturn(Optional.of(tallaConStock));
        when(productoTallaRepository.findByProductoIdAndTalla(1L, 10.0)).thenReturn(Optional.of(tallaSinStock));
        when(inventarioClient.consultarStock(101L)).thenReturn(5);
        when(inventarioClient.consultarStock(102L)).thenReturn(0);

        CrearPedidoDTO dto = new CrearPedidoDTO();
        dto.setMetodoPago("tarjeta");
        dto.setItems(List.of(
                new ItemPedidoDTO(1L, 9.0, 1, new BigDecimal("115")),
                new ItemPedidoDTO(1L, 10.0, 1, new BigDecimal("115"))
        ));

        assertThrows(StockInsuficienteException.class, () -> service.crearPedido(1L, dto));

        verify(inventarioClient, never()).descontarStock(anyLong(), anyInt());
        verify(pedidoRepository, never()).save(any());
    }
}
