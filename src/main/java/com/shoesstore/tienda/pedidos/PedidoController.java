package com.shoesstore.tienda.pedidos;

import com.shoesstore.tienda.pedidos.dto.CrearPedidoDTO;
import com.shoesstore.tienda.pedidos.model.Pedido;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    public ResponseEntity<Pedido> crear(HttpServletRequest request, @RequestBody CrearPedidoDTO dto) {
        Long usuarioId = (Long) request.getAttribute("usuarioId");
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.crearPedido(usuarioId, dto));
    }

    @GetMapping
    public List<Pedido> listar(HttpServletRequest request) {
        Long usuarioId = (Long) request.getAttribute("usuarioId");
        return pedidoService.listarPedidosDeUsuario(usuarioId);
    }

    @GetMapping("/{id}")
    public Pedido obtener(@PathVariable Long id) {
        return pedidoService.obtenerPedido(id);
    }
}
