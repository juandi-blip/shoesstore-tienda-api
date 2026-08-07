package com.shoesstore.tienda.productos;

import com.shoesstore.tienda.imagenes.ImagenController;
import com.shoesstore.tienda.productos.dto.ProductoDetalleDTO;
import com.shoesstore.tienda.productos.dto.ProductoResumenDTO;
import com.shoesstore.tienda.productos.dto.TallaDisponibleDTO;
import com.shoesstore.tienda.productos.model.Producto;
import com.shoesstore.tienda.productos.model.ProductoTalla;
import com.shoesstore.tienda.productos.repository.ProductoRepository;
import com.shoesstore.tienda.productos.repository.ProductoTallaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final ProductoTallaRepository productoTallaRepository;
    private final InventarioClient inventarioClient;

    public ProductoService(ProductoRepository productoRepository,
                            ProductoTallaRepository productoTallaRepository,
                            InventarioClient inventarioClient) {
        this.productoRepository = productoRepository;
        this.productoTallaRepository = productoTallaRepository;
        this.inventarioClient = inventarioClient;
    }

    public List<ProductoResumenDTO> listar(String genero, String marca, String proposito) {
        return productoRepository.findAll().stream()
                .filter(p -> genero == null || genero.equalsIgnoreCase(p.getGenero()))
                .filter(p -> marca == null || marca.equalsIgnoreCase(p.getMarca()))
                .filter(p -> proposito == null || proposito.equalsIgnoreCase(p.getProposito()))
                .map(this::aResumen)
                .collect(Collectors.toList());
    }

    public ProductoDetalleDTO obtenerDetalle(Long id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Producto no encontrado."));

        List<TallaDisponibleDTO> tallas = productoTallaRepository.findByProductoId(id).stream()
                .map(this::resolverDisponibilidad)
                .collect(Collectors.toList());

        return new ProductoDetalleDTO(producto.getId(), producto.getNombre(), producto.getMarca(),
                producto.getPrecio(), producto.getGenero(), producto.getProposito(), producto.getSubcategoria(),
                producto.getColorway(), producto.isNovedad(), producto.isOutlet(), rutaImagen(id), tallas);
    }

    private ProductoResumenDTO aResumen(Producto p) {
        return new ProductoResumenDTO(p.getId(), p.getNombre(), p.getMarca(), p.getPrecio(), p.getGenero(),
                p.getProposito(), p.getSubcategoria(), p.getColorway(), p.isNovedad(), p.isOutlet(),
                rutaImagen(p.getId()));
    }

    // Nunca se expone el valor almacenado en Producto.imagen (URL de un CDN de
    // terceros, con derechos de autor): siempre se sirve la imagen propia.
    private String rutaImagen(Long id) {
        return ImagenController.RUTA_BASE + "/producto/" + id;
    }

    private TallaDisponibleDTO resolverDisponibilidad(ProductoTalla productoTalla) {
        int stock = inventarioClient.consultarStock(productoTalla.getIdProductoInventario());
        return new TallaDisponibleDTO(productoTalla.getTalla(), stock > 0);
    }

    public Producto crear(Producto producto) {
        producto.setId(null);
        return productoRepository.save(producto);
    }

    public Producto actualizar(Long id, Producto datos) {
        Producto existente = productoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Producto no encontrado."));
        existente.setNombre(datos.getNombre());
        existente.setMarca(datos.getMarca());
        existente.setPrecio(datos.getPrecio());
        existente.setGenero(datos.getGenero());
        existente.setProposito(datos.getProposito());
        existente.setSubcategoria(datos.getSubcategoria());
        existente.setColorway(datos.getColorway());
        existente.setNovedad(datos.isNovedad());
        existente.setOutlet(datos.isOutlet());
        existente.setImagen(datos.getImagen());
        return productoRepository.save(existente);
    }

    public void eliminar(Long id) { productoRepository.deleteById(id); }
}
