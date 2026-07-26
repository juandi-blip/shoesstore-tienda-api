package com.shoesstore.tienda.pedidos;

import com.shoesstore.tienda.auth.model.Usuario;
import com.shoesstore.tienda.common.NoAutorizadoException;
import com.shoesstore.tienda.common.StockInsuficienteException;
import com.shoesstore.tienda.pedidos.dto.CrearPedidoDTO;
import com.shoesstore.tienda.pedidos.dto.ItemPedidoDTO;
import com.shoesstore.tienda.pedidos.model.Pedido;
import com.shoesstore.tienda.pedidos.model.PedidoItem;
import com.shoesstore.tienda.pedidos.repository.PedidoItemRepository;
import com.shoesstore.tienda.pedidos.repository.PedidoRepository;
import com.shoesstore.tienda.productos.InventarioClient;
import com.shoesstore.tienda.productos.model.ProductoTalla;
import com.shoesstore.tienda.productos.repository.ProductoTallaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final PedidoItemRepository pedidoItemRepository;
    private final ProductoTallaRepository productoTallaRepository;
    private final InventarioClient inventarioClient;

    public PedidoService(PedidoRepository pedidoRepository, PedidoItemRepository pedidoItemRepository,
                          ProductoTallaRepository productoTallaRepository, InventarioClient inventarioClient) {
        this.pedidoRepository = pedidoRepository;
        this.pedidoItemRepository = pedidoItemRepository;
        this.productoTallaRepository = productoTallaRepository;
        this.inventarioClient = inventarioClient;
    }

    @Transactional
    public Pedido crearPedido(Long usuarioId, CrearPedidoDTO dto) {
        List<ItemPedidoDTO> itemsDto = dto.getItems();

        // 1) Resuelve la variante de inventario de cada linea.
        List<ProductoTalla> variantes = itemsDto.stream()
                .map(item -> productoTallaRepository.findByProductoIdAndTalla(item.getProductoId(), item.getTalla())
                        .orElseThrow(() -> new NoSuchElementException("Producto/talla no encontrado.")))
                .toList();

        // 2) Agrega la cantidad pedida por variante de inventario distinta
        //    (idProductoInventario), porque el cliente puede repetir la misma
        //    talla en varias lineas del mismo pedido: hay que validar y descontar
        //    UNA sola vez por variante, contra la demanda TOTAL de esa variante,
        //    no una vez por linea contra el mismo snapshot de stock (eso permitiria
        //    sobrevender: dos lineas de 3 unidades cada una contra un stock de 5
        //    pasarian ambas validaciones individuales pero suman 6 > 5).
        Map<Long, Integer> demandaPorVariante = new LinkedHashMap<>();
        for (int i = 0; i < variantes.size(); i++) {
            Long idInventario = variantes.get(i).getIdProductoInventario();
            int cantidadPedida = itemsDto.get(i).getCantidad();
            demandaPorVariante.merge(idInventario, cantidadPedida, Integer::sum);
        }

        // 3) Valida TODO el stock (una consulta por variante distinta) antes de
        //    persistir o descontar nada (ver spec: "valida-todo-antes-de-descontar-nada").
        Map<Long, Integer> stockPorVariante = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> entrada : demandaPorVariante.entrySet()) {
            int stockActual = inventarioClient.consultarStock(entrada.getKey());
            if (stockActual < entrada.getValue()) {
                throw new StockInsuficienteException(
                        "Stock insuficiente para uno o mas productos del pedido.");
            }
            stockPorVariante.put(entrada.getKey(), stockActual);
        }

        // 4) Calcula el total y persiste el pedido con sus lineas. El precio
        //    SIEMPRE se deriva del producto en servidor, nunca del DTO del
        //    cliente (evita manipulacion de precio).
        BigDecimal totalProductos = BigDecimal.ZERO;
        for (int i = 0; i < variantes.size(); i++) {
            BigDecimal precioReal = variantes.get(i).getProducto().getPrecio();
            int cantidadPedida = itemsDto.get(i).getCantidad();
            totalProductos = totalProductos.add(precioReal.multiply(BigDecimal.valueOf(cantidadPedida)));
        }

        Pedido pedido = new Pedido();
        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);
        pedido.setUsuario(usuario);
        pedido.setNumeroOrden("SS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        pedido.setMetodoPago(dto.getMetodoPago());
        pedido.setBanco(dto.getBanco());
        pedido.setEnvioCop(dto.getEnvioCop());
        pedido.setTotalCop(totalProductos.add(dto.getEnvioCop()));
        pedido = pedidoRepository.save(pedido);

        for (int i = 0; i < variantes.size(); i++) {
            ItemPedidoDTO itemDTO = itemsDto.get(i);
            PedidoItem item = new PedidoItem();
            item.setPedido(pedido);
            var producto = new com.shoesstore.tienda.productos.model.Producto();
            producto.setId(itemDTO.getProductoId());
            item.setProducto(producto);
            item.setTalla(itemDTO.getTalla());
            item.setCantidad(itemDTO.getCantidad());
            item.setPrecioUnitario(variantes.get(i).getProducto().getPrecio());
            pedidoItemRepository.save(item);
        }

        // 5) Solo despues de que el pedido y sus lineas se persistieron con exito
        //    se descuenta el stock real en shoesstore-inventario-api, una vez por
        //    variante distinta (nunca antes: una falla de persistencia despues de
        //    descontar quemaria stock sin crear ningun pedido).
        for (Map.Entry<Long, Integer> entrada : demandaPorVariante.entrySet()) {
            Long idInventario = entrada.getKey();
            int nuevoStock = stockPorVariante.get(idInventario) - entrada.getValue();
            inventarioClient.descontarStock(idInventario, nuevoStock);
        }

        return pedido;
    }

    public List<Pedido> listarPedidosDeUsuario(Long usuarioId) {
        return pedidoRepository.findByUsuarioId(usuarioId);
    }

    public Pedido obtenerPedido(Long usuarioId, Long id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Pedido no encontrado."));
        if (!pedido.getUsuario().getId().equals(usuarioId)) {
            throw new NoAutorizadoException("No tienes acceso a este pedido.");
        }
        return pedido;
    }
}
