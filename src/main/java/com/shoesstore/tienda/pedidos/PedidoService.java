package com.shoesstore.tienda.pedidos;

import com.shoesstore.tienda.auth.model.Usuario;
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

import java.math.BigDecimal;
import java.util.List;
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

    public Pedido crearPedido(Long usuarioId, CrearPedidoDTO dto) {
        // 1) Resuelve la variante de inventario de cada linea y valida TODO el stock
        //    antes de descontar nada (ver spec: "valida-todo-antes-de-descontar-nada").
        List<ProductoTalla> variantes = dto.getItems().stream()
                .map(item -> productoTallaRepository.findByProductoIdAndTalla(item.getProductoId(), item.getTalla())
                        .orElseThrow(() -> new NoSuchElementException("Producto/talla no encontrado.")))
                .toList();

        for (int i = 0; i < variantes.size(); i++) {
            ProductoTalla variante = variantes.get(i);
            int cantidadPedida = dto.getItems().get(i).getCantidad();
            int stockActual = inventarioClient.consultarStock(variante.getIdProductoInventario());
            if (stockActual < cantidadPedida) {
                throw new StockInsuficienteException(
                        "Stock insuficiente para la talla " + dto.getItems().get(i).getTalla() + ".");
            }
        }

        // 2) Todas las lineas tienen stock: ahora si se descuenta, una por una.
        for (int i = 0; i < variantes.size(); i++) {
            ProductoTalla variante = variantes.get(i);
            int cantidadPedida = dto.getItems().get(i).getCantidad();
            int stockActual = inventarioClient.consultarStock(variante.getIdProductoInventario());
            inventarioClient.descontarStock(variante.getIdProductoInventario(), stockActual - cantidadPedida);
        }

        // 3) Calcula el total y persiste el pedido con sus lineas.
        BigDecimal totalProductos = dto.getItems().stream()
                .map(item -> item.getPrecioUnitario().multiply(BigDecimal.valueOf(item.getCantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

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

        for (ItemPedidoDTO itemDTO : dto.getItems()) {
            PedidoItem item = new PedidoItem();
            item.setPedido(pedido);
            var producto = new com.shoesstore.tienda.productos.model.Producto();
            producto.setId(itemDTO.getProductoId());
            item.setProducto(producto);
            item.setTalla(itemDTO.getTalla());
            item.setCantidad(itemDTO.getCantidad());
            item.setPrecioUnitario(itemDTO.getPrecioUnitario());
            pedidoItemRepository.save(item);
        }

        return pedido;
    }

    public List<Pedido> listarPedidosDeUsuario(Long usuarioId) {
        return pedidoRepository.findByUsuarioId(usuarioId);
    }

    public Pedido obtenerPedido(Long id) {
        return pedidoRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Pedido no encontrado."));
    }
}
