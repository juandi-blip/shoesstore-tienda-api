# Frontend-Backend Integration + Own Image Module — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the `shoes'sStore 2.0` React frontend to the real `shoesstore-tienda-api` backend (products, images, auth, orders), replacing the local `productos.json` + hotlinked third-party CDN images with a self-owned backend image endpoint and real HTTP calls.

**Architecture:** Backend gets a new zero-storage `imagenes` module that generates deterministic placeholder SVGs server-side (no third-party image hosting = no copyright risk), and the two existing `Producto` list/detail responses are changed to point at that module instead of the stored third-party URL. The frontend gets a thin `src/services/` layer (`apiClient` → `productosService` / `authService` / `pedidosService`) that becomes the single seam between React state and the network; `useProductos`, `CartContext`, `SessionContext`, `LoginPage`, `RegistroPage`, `PagoPage`, and `ProductoDetallePage` are rewired to go through it instead of the static JSON import / mock session.

**Tech Stack:** Backend: Spring Boot 3.2.5, Java 17, JUnit 5 + Mockito (unit tests only, no MockMvc slices in this codebase — follow that convention). Frontend: React 19 + Vite, Vitest + `@testing-library/react`, native `fetch` (no axios).

**Spec:** `docs/superpowers/specs/2026-08-07-frontend-backend-integration-design.md` (this repo).

## Global Constraints

- Backend base URL in dev: `http://localhost:8081`. Frontend must read it from `VITE_API_URL` (default `http://localhost:8081`), never hardcode it outside `apiClient.js`.
- Backend error responses are always JSON `{ "mensaje": "..." }` with a non-2xx HTTP status (see `ManejadorErrores`). The frontend `apiClient` must surface `mensaje` as the thrown error's `message`.
- Auth: protected endpoints require header `Authorization: Bearer <token>`. Public GET routes today: `/api/productos/**`. This plan adds `/api/imagenes/**` to that public allowlist (task 2) — `<img>` tags cannot send custom headers, so this endpoint must stay public.
- No new runtime dependencies (no axios, no image libraries) — native `fetch` on the frontend, plain Spring MVC on the backend.
- Never reintroduce a call to `static.sneakerjagers.com` or any other third-party image CDN.
- Two repositories are touched. Backend paths below are relative to `D:\juandiplay\cursito html\sena\shoesstore-tienda-api`. Frontend paths below are relative to `D:\juandiplay\cursito html\sena\shoes'sStore 2.0`. Each is written out in full the first time it appears in a task and abbreviated as `(backend)` / `(frontend)` afterward.
- Backend test command: `mvn test` (run from the backend repo root). Frontend test command: `pnpm test` (runs `vitest run`, from the frontend repo root).

---

## Task 1: Backend — `ImagenService` (SVG placeholder generator)

**Files:**
- Create: `src/main/java/com/shoesstore/tienda/imagenes/ImagenService.java`
- Test: `src/test/java/com/shoesstore/tienda/imagenes/ImagenServiceTest.java`

**Interfaces:**
- Produces: `ImagenService.generarSvg(String marca, String nombre) -> String` (returns raw SVG markup, no XML declaration). Used by Task 2's `ImagenController`.

- [ ] **Step 1: Write the failing test**

```java
package com.shoesstore.tienda.imagenes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImagenServiceTest {

    private final ImagenService service = new ImagenService();

    @Test
    void incluyeMarcaEnMayusculasYNombreTalCual() {
        String svg = service.generarSvg("Nike", "Air Force 1 '07");
        assertTrue(svg.contains("NIKE"));
        assertTrue(svg.contains("Air Force 1 &apos;07"));
    }

    @Test
    void usaValoresPorDefectoCuandoMarcaYNombreSonNulos() {
        String svg = service.generarSvg(null, null);
        assertTrue(svg.contains("SHOES.STORE"));
        assertTrue(svg.contains("Imagen no disponible"));
    }

    @Test
    void usaValoresPorDefectoCuandoMarcaYNombreEstanVacios() {
        String svg = service.generarSvg("  ", "");
        assertTrue(svg.contains("SHOES.STORE"));
    }

    @Test
    void escapaCaracteresEspecialesXml() {
        String svg = service.generarSvg("A&B", "<script>alert('x')</script>");
        assertTrue(svg.contains("A&amp;B"));
        assertTrue(svg.contains("&lt;script&gt;"));
        assertFalse(svg.contains("<script>"));
    }

    @Test
    void produceMarcadoSvgValidoQueEmpiezaYTerminaCorrectamente() {
        String svg = service.generarSvg("Adidas", "Superstar");
        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.endsWith("</svg>"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (from the backend repo root): `mvn test -Dtest=ImagenServiceTest`
Expected: FAIL — compile error, `ImagenService` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.shoesstore.tienda.imagenes;

import org.springframework.stereotype.Service;

// Genera imagenes de producto propias (SVG determinista) para no depender
// de CDNs de terceros con derechos de autor. Ver docs/superpowers/specs/
// 2026-08-07-frontend-backend-integration-design.md.
@Service
public class ImagenService {

    public String generarSvg(String marca, String nombre) {
        String marcaSegura = escapar(esVacio(marca) ? "SHOES.STORE" : marca.toUpperCase());
        String nombreSeguro = escapar(esVacio(nombre) ? "Imagen no disponible" : nombre);
        return "<svg xmlns='http://www.w3.org/2000/svg' width='660' height='660' viewBox='0 0 660 660'>"
                + "<rect width='660' height='660' fill='#161616'/>"
                + "<rect x='24' y='24' width='612' height='612' rx='24' fill='none' stroke='#2a2a2a' stroke-width='2'/>"
                + "<text x='50%' y='45%' text-anchor='middle' fill='#3d3d3d' font-family='Poppins,system-ui,sans-serif' font-size='52' font-weight='700' letter-spacing='6'>" + marcaSegura + "</text>"
                + "<text x='50%' y='54%' text-anchor='middle' fill='#565656' font-family='Poppins,system-ui,sans-serif' font-size='26' font-weight='500'>" + nombreSeguro + "</text>"
                + "<text x='50%' y='63%' text-anchor='middle' fill='#3d3d3d' font-family='Poppins,system-ui,sans-serif' font-size='16'>Imagen no disponible</text>"
                + "</svg>";
    }

    private boolean esVacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private String escapar(String texto) {
        return texto
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("'", "&apos;")
                .replace("\"", "&quot;");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ImagenServiceTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/shoesstore/tienda/imagenes/ImagenService.java src/test/java/com/shoesstore/tienda/imagenes/ImagenServiceTest.java
git commit -m "feat: add ImagenService, a zero-storage SVG placeholder generator"
```

---

## Task 2: Backend — `ImagenController` + make `/api/imagenes` publicly readable

**Files:**
- Create: `src/main/java/com/shoesstore/tienda/imagenes/ImagenController.java`
- Test: `src/test/java/com/shoesstore/tienda/imagenes/ImagenControllerTest.java`
- Modify: `src/main/java/com/shoesstore/tienda/common/TokenAuthFilter.java:53-59`

**Interfaces:**
- Consumes: `ImagenService.generarSvg(String, String) -> String` (Task 1), `ProductoRepository.findById(Long) -> Optional<Producto>` (existing).
- Produces: `GET /api/imagenes/producto/{id}` → `200 image/svg+xml` body, or propagates `NoSuchElementException` (→ 404 via the existing `ManejadorErrores`) when the product doesn't exist.

- [ ] **Step 1: Write the failing test**

```java
package com.shoesstore.tienda.imagenes;

import com.shoesstore.tienda.productos.model.Producto;
import com.shoesstore.tienda.productos.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImagenControllerTest {

    @Mock ProductoRepository productoRepository;

    @Test
    void devuelveSvgConMarcaYNombreDelProductoEncontrado() {
        ImagenController controller = new ImagenController(productoRepository, new ImagenService());
        Producto producto = new Producto();
        producto.setId(1L);
        producto.setMarca("Nike");
        producto.setNombre("Air Force 1 '07");
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));

        String svg = controller.imagenProducto(1L);

        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.contains("NIKE"));
    }

    @Test
    void lanzaNoSuchElementExceptionSiElProductoNoExiste() {
        ImagenController controller = new ImagenController(productoRepository, new ImagenService());
        when(productoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> controller.imagenProducto(99L));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ImagenControllerTest`
Expected: FAIL — compile error, `ImagenController` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.shoesstore.tienda.imagenes;

import com.shoesstore.tienda.productos.model.Producto;
import com.shoesstore.tienda.productos.repository.ProductoRepository;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

// Endpoint publico (sin auth, ver TokenAuthFilter): genera la imagen de un
// producto en el momento, sin almacenar ni referenciar nada de terceros.
@RestController
@RequestMapping("/api/imagenes")
public class ImagenController {

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
```

Now fix `TokenAuthFilter` so `<img>` requests (which never carry an `Authorization` header) aren't rejected. Open `src/main/java/com/shoesstore/tienda/common/TokenAuthFilter.java` and replace the `esRutaPublica` method (currently lines 53-59):

```java
    private boolean esRutaPublica(HttpServletRequest request) {
        String path = request.getRequestURI();
        String metodo = request.getMethod();
        if ("OPTIONS".equals(metodo)) return true;
        if (path.equals("/api/auth/registro") || path.equals("/api/auth/login")) return true;
        if (!"GET".equals(metodo)) return false;
        return path.startsWith("/api/productos") || path.startsWith("/api/imagenes");
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ImagenControllerTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Manual verification (both other backend services must be running)**

Start `shoesstore-tienda-api` (`mvn spring-boot:run`, port 8081) and run:

```bash
curl -i http://localhost:8081/api/imagenes/producto/1
```

Expected: `HTTP/1.1 200`, `Content-Type: image/svg+xml`, body starting with `<svg`. No `Authorization` header was sent — confirms the public-route fix works.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/shoesstore/tienda/imagenes/ImagenController.java src/test/java/com/shoesstore/tienda/imagenes/ImagenControllerTest.java src/main/java/com/shoesstore/tienda/common/TokenAuthFilter.java
git commit -m "feat: expose GET /api/imagenes/producto/{id} as a public route"
```

---

## Task 3: Backend — Point `Producto` responses at the own image endpoint

**Files:**
- Create: `src/main/java/com/shoesstore/tienda/productos/dto/ProductoResumenDTO.java`
- Modify: `src/main/java/com/shoesstore/tienda/productos/ProductoService.java`
- Modify: `src/main/java/com/shoesstore/tienda/productos/ProductoController.java:21-26`
- Test: `src/test/java/com/shoesstore/tienda/productos/ProductoServiceTest.java`

**Interfaces:**
- Produces: `ProductoService.listar(...) -> List<ProductoResumenDTO>` (was `List<Producto>`). `ProductoDetalleDTO.getImagen()` and `ProductoResumenDTO.getImagen()` now always return `"/api/imagenes/producto/" + id"` — never the value stored in the `imagen` DB column.

- [ ] **Step 1: Write the failing tests**

Add these two tests to the existing `src/test/java/com/shoesstore/tienda/productos/ProductoServiceTest.java` (keep the existing `detalleMarcaTallaComoNoDisponibleSiElInventarioNoTieneStock` test as-is, add these below it, and add the `ProductoResumenDTO` and `java.math.BigDecimal` imports at the top if not already present):

```java
    @Test
    void detalleExponeRutaDeImagenPropiaEnVezDeLaUrlAlmacenada() {
        ProductoService service = new ProductoService(productoRepository, productoTallaRepository, inventarioClient);

        Producto producto = new Producto();
        producto.setId(7L);
        producto.setImagen("https://static.sneakerjagers.com/products/660x660/999999.jpg");

        when(productoRepository.findById(7L)).thenReturn(Optional.of(producto));
        when(productoTallaRepository.findByProductoId(7L)).thenReturn(List.of());

        ProductoDetalleDTO detalle = service.obtenerDetalle(7L);

        assertEquals("/api/imagenes/producto/7", detalle.getImagen());
    }

    @Test
    void listarExponeRutaDeImagenPropiaParaCadaProducto() {
        ProductoService service = new ProductoService(productoRepository, productoTallaRepository, inventarioClient);

        Producto producto = new Producto();
        producto.setId(3L);
        producto.setImagen("https://static.sneakerjagers.com/products/660x660/111111.jpg");

        when(productoRepository.findAll()).thenReturn(List.of(producto));

        List<ProductoResumenDTO> lista = service.listar(null, null, null);

        assertEquals(1, lista.size());
        assertEquals("/api/imagenes/producto/3", lista.get(0).getImagen());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=ProductoServiceTest`
Expected: FAIL — compile error, `ProductoResumenDTO` does not exist and `listar` still returns `List<Producto>`.

- [ ] **Step 3: Create `ProductoResumenDTO`**

```java
package com.shoesstore.tienda.productos.dto;

import java.math.BigDecimal;

// Forma de un producto en el listado del catalogo (sin tallas: eso solo se
// resuelve en el detalle, porque implica una llamada al inventario por talla).
public class ProductoResumenDTO {
    private Long id;
    private String nombre;
    private String marca;
    private BigDecimal precio;
    private String genero;
    private String proposito;
    private String subcategoria;
    private String colorway;
    private boolean novedad;
    private boolean outlet;
    private String imagen;

    public ProductoResumenDTO(Long id, String nombre, String marca, BigDecimal precio, String genero,
                               String proposito, String subcategoria, String colorway, boolean novedad,
                               boolean outlet, String imagen) {
        this.id = id;
        this.nombre = nombre;
        this.marca = marca;
        this.precio = precio;
        this.genero = genero;
        this.proposito = proposito;
        this.subcategoria = subcategoria;
        this.colorway = colorway;
        this.novedad = novedad;
        this.outlet = outlet;
        this.imagen = imagen;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getMarca() { return marca; }
    public BigDecimal getPrecio() { return precio; }
    public String getGenero() { return genero; }
    public String getProposito() { return proposito; }
    public String getSubcategoria() { return subcategoria; }
    public String getColorway() { return colorway; }
    public boolean isNovedad() { return novedad; }
    public boolean isOutlet() { return outlet; }
    public String getImagen() { return imagen; }
}
```

- [ ] **Step 4: Rewrite `ProductoService`**

Replace the full contents of `src/main/java/com/shoesstore/tienda/productos/ProductoService.java`:

```java
package com.shoesstore.tienda.productos;

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
        return "/api/imagenes/producto/" + id;
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
```

- [ ] **Step 5: Update `ProductoController.listar`'s return type**

In `src/main/java/com/shoesstore/tienda/productos/ProductoController.java`, replace lines 21-26:

```java
    @GetMapping
    public List<ProductoResumenDTO> listar(@RequestParam(required = false) String genero,
                                            @RequestParam(required = false) String marca,
                                            @RequestParam(required = false) String proposito) {
        return productoService.listar(genero, marca, proposito);
    }
```

And add the import at the top of the file: `import com.shoesstore.tienda.productos.dto.ProductoResumenDTO;`

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn test -Dtest=ProductoServiceTest`
Expected: PASS (3 tests). Then run the full suite: `mvn test` — expected: all green (no other class referenced `Producto` as the list response type).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/shoesstore/tienda/productos/dto/ProductoResumenDTO.java src/main/java/com/shoesstore/tienda/productos/ProductoService.java src/main/java/com/shoesstore/tienda/productos/ProductoController.java src/test/java/com/shoesstore/tienda/productos/ProductoServiceTest.java
git commit -m "feat: serve the own image endpoint instead of the stored third-party image URL"
```

---

## Task 4: Frontend — `apiClient.js` (HTTP foundation)

All remaining tasks are in `D:\juandiplay\cursito html\sena\shoes'sStore 2.0` unless noted. Run tests with `pnpm test` from that directory.

**Files:**
- Create: `src/services/apiClient.js`
- Test: `src/services/apiClient.test.js`
- Create: `.env.example`

**Interfaces:**
- Produces: `API_BASE_URL: string`, `class ApiError extends Error { status: number }`, `apiClient.get(path, opts?) -> Promise<any>`, `apiClient.post(path, body, opts?) -> Promise<any>` where `opts` may include `{ token?: string }`. Used by every `services/*` module in later tasks.

- [ ] **Step 1: Write the failing test**

```js
import { describe, it, expect, vi, afterEach } from 'vitest'
import { apiClient, ApiError, API_BASE_URL } from './apiClient'

afterEach(() => {
  vi.unstubAllGlobals()
})

function mockFetchOnce(respuesta) {
  vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(respuesta)))
}

describe('apiClient.get', () => {
  it('devuelve el JSON cuando la respuesta es exitosa', async () => {
    mockFetchOnce({ ok: true, status: 200, json: () => Promise.resolve([{ id: 1 }]) })
    const datos = await apiClient.get('/api/productos')
    expect(datos).toEqual([{ id: 1 }])
    expect(fetch).toHaveBeenCalledWith(`${API_BASE_URL}/api/productos`, expect.objectContaining({ method: 'GET' }))
  })

  it('lanza ApiError con el mensaje del backend cuando la respuesta no es ok', async () => {
    mockFetchOnce({ ok: false, status: 404, json: () => Promise.resolve({ mensaje: 'Producto no encontrado.' }) })
    await expect(apiClient.get('/api/productos/999')).rejects.toThrow('Producto no encontrado.')
  })

  it('lanza ApiError cuando fetch rechaza (sin conexion)', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('network down'))))
    await expect(apiClient.get('/api/productos')).rejects.toBeInstanceOf(ApiError)
  })

  it('devuelve null en respuestas 204 sin intentar parsear body', async () => {
    mockFetchOnce({ ok: true, status: 204, json: () => Promise.reject(new Error('no deberia llamarse')) })
    const datos = await apiClient.get('/api/auth/logout')
    expect(datos).toBeNull()
  })
})

describe('apiClient.post', () => {
  it('envia el body como JSON y el header Content-Type', async () => {
    mockFetchOnce({ ok: true, status: 201, json: () => Promise.resolve({ token: 'abc' }) })
    await apiClient.post('/api/auth/login', { nombreUsuario: 'ana', contrasena: '123' })
    expect(fetch).toHaveBeenCalledWith(
      `${API_BASE_URL}/api/auth/login`,
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ nombreUsuario: 'ana', contrasena: '123' }),
        headers: expect.objectContaining({ 'Content-Type': 'application/json' }),
      })
    )
  })

  it('agrega el header Authorization cuando se pasa token', async () => {
    mockFetchOnce({ ok: true, status: 201, json: () => Promise.resolve({}) })
    await apiClient.post('/api/pedidos', { items: [] }, { token: 'mi-token' })
    expect(fetch).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({ headers: expect.objectContaining({ Authorization: 'Bearer mi-token' }) })
    )
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm test apiClient`
Expected: FAIL — `apiClient.js` does not exist.

- [ ] **Step 3: Write the implementation**

```js
export const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8081'

export class ApiError extends Error {
  constructor(mensaje, status) {
    super(mensaje)
    this.name = 'ApiError'
    this.status = status
  }
}

async function solicitar(path, { method = 'GET', body, token } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers['Authorization'] = `Bearer ${token}`

  let respuesta
  try {
    respuesta = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError('No se pudo conectar con el servidor. Intenta de nuevo.', 0)
  }

  if (respuesta.status === 204) return null

  let datos = null
  try {
    datos = await respuesta.json()
  } catch {
    datos = null
  }

  if (!respuesta.ok) {
    throw new ApiError(datos?.mensaje || 'Ocurrió un error inesperado.', respuesta.status)
  }
  return datos
}

export const apiClient = {
  get: (path, opts) => solicitar(path, { ...opts, method: 'GET' }),
  post: (path, body, opts) => solicitar(path, { ...opts, method: 'POST', body }),
}
```

Create `.env.example` at the project root:

```
VITE_API_URL=http://localhost:8081
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm test apiClient`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/services/apiClient.js src/services/apiClient.test.js .env.example
git commit -m "feat: add apiClient, the shared HTTP layer for the backend integration"
```

---

## Task 5: Frontend — Test safety net (no test may hit the real network)

Several existing tests (`Navbar.test.jsx`, `HomePage.test.jsx`) render trees that will start depending on `fetch` once later tasks land (through `CartProvider`/`useProductos`), but don't assert on network-derived data, so they won't be rewritten. This task adds a default `fetch` stub so they keep passing without modification, before any component starts calling `productosService`.

**Files:**
- Create: `src/test/setupTests.js`
- Modify: `vite.config.js:27-30`

- [ ] **Step 1: Create the setup file**

```js
import '@testing-library/jest-dom'
import { afterEach, beforeEach, vi } from 'vitest'

// Red de seguridad: ningun test debe golpear una red real. Los tests que
// necesitan datos concretos del backend sobreescriben este stub con
// vi.stubGlobal('fetch', ...) o con vi.mock(...) sobre el service que usan.
beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn(() =>
    Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([]) })
  ))
})

afterEach(() => {
  vi.unstubAllGlobals()
})
```

- [ ] **Step 2: Wire it into Vitest**

In `vite.config.js`, replace the `test` block (currently lines 27-30):

```js
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setupTests.js'],
  },
```

- [ ] **Step 3: Run the full suite to verify nothing broke**

Run: `pnpm test`
Expected: PASS — same results as before this task (no component calls `productosService` yet, so this is a no-op today; it only becomes load-bearing starting Task 8).

- [ ] **Step 4: Commit**

```bash
git add src/test/setupTests.js vite.config.js
git commit -m "test: add a default fetch stub so tests never hit the real network"
```

---

## Task 6: Frontend — `productosService.js`

**Files:**
- Create: `src/services/productosService.js`
- Test: `src/services/productosService.test.js`

**Interfaces:**
- Consumes: `apiClient.get` (Task 4).
- Produces: `listarProductos() -> Promise<Array<object>>` (cached promise — concurrent calls share one request), `obtenerProducto(id) -> Promise<object>`, `limpiarCacheProductos()` (test-only cache reset). Every returned product's `imagen` field is an **absolute** URL (`${API_BASE_URL}${ruta relativa del backend}`).

- [ ] **Step 1: Write the failing test**

```js
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { listarProductos, obtenerProducto, limpiarCacheProductos } from './productosService'
import { API_BASE_URL } from './apiClient'

beforeEach(() => {
  limpiarCacheProductos()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function mockFetchJson(datos) {
  vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(datos) })))
}

describe('listarProductos', () => {
  it('convierte rutas de imagen relativas en URLs absolutas del backend', async () => {
    mockFetchJson([{ id: 1, nombre: 'Air Force 1', imagen: '/api/imagenes/producto/1' }])
    const lista = await listarProductos()
    expect(lista[0].imagen).toBe(`${API_BASE_URL}/api/imagenes/producto/1`)
  })

  it('cachea la promesa: dos llamadas concurrentes solo hacen una peticion', async () => {
    mockFetchJson([])
    await Promise.all([listarProductos(), listarProductos()])
    expect(fetch).toHaveBeenCalledTimes(1)
  })
})

describe('obtenerProducto', () => {
  it('pide el detalle por id y normaliza su imagen', async () => {
    mockFetchJson({ id: 5, nombre: 'Dunk Low', imagen: '/api/imagenes/producto/5', tallas: [] })
    const producto = await obtenerProducto(5)
    expect(fetch).toHaveBeenCalledWith(`${API_BASE_URL}/api/productos/5`, expect.anything())
    expect(producto.imagen).toBe(`${API_BASE_URL}/api/imagenes/producto/5`)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm test productosService`
Expected: FAIL — `productosService.js` does not exist.

- [ ] **Step 3: Write the implementation**

```js
import { apiClient, API_BASE_URL } from './apiClient'

let catalogoPromise = null

function normalizarImagen(producto) {
  if (typeof producto.imagen === 'string' && producto.imagen.startsWith('/')) {
    return { ...producto, imagen: `${API_BASE_URL}${producto.imagen}` }
  }
  return producto
}

/** Lista el catálogo público. Cachea la promesa: llamadas concurrentes comparten una sola petición. */
export function listarProductos() {
  if (!catalogoPromise) {
    catalogoPromise = apiClient.get('/api/productos')
      .then((lista) => lista.map(normalizarImagen))
      .catch((error) => {
        catalogoPromise = null
        throw error
      })
  }
  return catalogoPromise
}

/** Obtiene el detalle de un producto (incluye disponibilidad real de tallas). */
export async function obtenerProducto(id) {
  const producto = await apiClient.get(`/api/productos/${id}`)
  return normalizarImagen(producto)
}

/** Solo para pruebas: limpia la caché en memoria del catálogo. */
export function limpiarCacheProductos() {
  catalogoPromise = null
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm test productosService`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/services/productosService.js src/services/productosService.test.js
git commit -m "feat: add productosService, cached fetch of the real product catalog"
```

---

## Task 7: Frontend — Rewire `useProductos` to fetch from the backend

**Files:**
- Modify: `src/hooks/useProductos.js`
- Modify: `src/hooks/useProductos.test.js` (append; keep the existing `filtrarYOrdenarProductos` tests untouched)

**Interfaces:**
- Consumes: `productosService.listarProductos` (Task 6).
- Produces: `useProductos() -> { productos: Array<object>, cargando: boolean, error: Error|null }` (was `{ productos }` synchronously from static JSON). `filtrarYOrdenarProductos` keeps its exact signature — unaffected.

- [ ] **Step 1: Write the failing test**

Append to the bottom of `src/hooks/useProductos.test.js` (this file currently has no React/testing-library imports — add these three lines at the very top, above the existing `import { describe, it, expect } from 'vitest'`, and add the `vi.mock` call right after them so Vitest can hoist it):

```js
import { renderHook, waitFor } from '@testing-library/react'
import { vi } from 'vitest'

vi.mock('../services/productosService', () => ({
  listarProductos: vi.fn(),
}))
```

Then, after the existing `import { filtrarYOrdenarProductos } from './useProductos'` line, add:

```js
import { listarProductos } from '../services/productosService'
import { useProductos } from './useProductos'
```

And append this new `describe` block at the end of the file:

```js
describe('useProductos', () => {
  it('empieza cargando y expone el catálogo una vez resuelto', async () => {
    listarProductos.mockResolvedValue([{ id: 1, nombre: 'Air Force 1' }])
    const { result } = renderHook(() => useProductos())
    expect(result.current.cargando).toBe(true)
    await waitFor(() => expect(result.current.cargando).toBe(false))
    expect(result.current.productos).toEqual([{ id: 1, nombre: 'Air Force 1' }])
  })

  it('expone el error si la petición falla y deja productos como []', async () => {
    listarProductos.mockRejectedValue(new Error('fallo de red'))
    const { result } = renderHook(() => useProductos())
    await waitFor(() => expect(result.current.cargando).toBe(false))
    expect(result.current.error).toBeInstanceOf(Error)
    expect(result.current.productos).toEqual([])
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm test useProductos`
Expected: FAIL — `useProductos` still returns `{ productos }` synchronously from the static import, no `cargando`/`error`.

- [ ] **Step 3: Rewrite `useProductos.js`**

Replace lines 1 and 57-63 (the `import` and the `useProductos` function) — keep `filtrarYOrdenarProductos` (lines 3-55) exactly as-is:

```js
import { useState, useEffect } from 'react'
import { listarProductos } from '../services/productosService'
```

```js
/**
 * Expone el catálogo completo de productos, obtenido del backend.
 * @returns {{productos: Array<object>, cargando: boolean, error: Error|null}}
 */
export function useProductos() {
  const [productos, setProductos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelado = false
    listarProductos()
      .then((lista) => { if (!cancelado) { setProductos(lista); setCargando(false) } })
      .catch((err) => { if (!cancelado) { setError(err); setCargando(false) } })
    return () => { cancelado = true }
  }, [])

  return { productos, cargando, error }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm test useProductos`
Expected: PASS (all `filtrarYOrdenarProductos` tests + the 2 new ones).

- [ ] **Step 5: Commit**

```bash
git add src/hooks/useProductos.js src/hooks/useProductos.test.js
git commit -m "feat: fetch the product catalog from the backend instead of the static JSON"
```

---

## Task 8: Frontend — Rewire `CatalogoPage` (loading state + real data)

**Files:**
- Modify: `src/pages/CatalogoPage.jsx`
- Modify: `src/pages/CatalogoPage.test.jsx` (full rewrite)

**Interfaces:**
- Consumes: `useProductos()` (Task 7, now returns `{ productos, cargando, error }`).

- [ ] **Step 1: Write the failing test**

Replace the full contents of `src/pages/CatalogoPage.test.jsx`:

```jsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import '@testing-library/jest-dom'
import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import CatalogoPage from './CatalogoPage'

vi.mock('../services/productosService', () => ({
  listarProductos: vi.fn(),
}))
import { listarProductos } from '../services/productosService'

const CATALOGO_PRUEBA = [
  { id: 1, nombre: 'Air Force 1', marca: 'Nike', precio: 115, genero: 'hombre', proposito: 'lifestyle', subcategoria: 'originals', novedad: false, outlet: false, imagen: '' },
  { id: 2, nombre: 'Ultraboost', marca: 'Adidas', precio: 180, genero: 'mujer', proposito: 'running', subcategoria: 'performance', novedad: true, outlet: false, imagen: '' },
  { id: 3, nombre: 'Zoom Freak', marca: 'Nike', precio: 95, genero: 'hombre', proposito: 'basketball', subcategoria: 'performance', novedad: false, outlet: true, imagen: '' },
  { id: 4, nombre: 'Gazelle', marca: 'Adidas', precio: 45, genero: 'mujer', proposito: 'lifestyle', subcategoria: 'originals', novedad: false, outlet: false, imagen: '' },
]

beforeEach(() => {
  listarProductos.mockReset()
  listarProductos.mockResolvedValue(CATALOGO_PRUEBA)
})

function renderCatalogo(initialEntries = ['/catalogo']) {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <CatalogoPage />
    </MemoryRouter>
  )
}

async function contadorActual() {
  const texto = await screen.findByText(/productos$/)
  return Number(texto.textContent.split(' ')[0])
}

describe('CatalogoPage — filtros', () => {
  it('sin filtros muestra los 4 productos del catálogo', async () => {
    renderCatalogo()
    expect(await contadorActual()).toBe(4)
  })

  it('togglear el checkbox de género "Hombre" reduce el conteo (2 productos)', async () => {
    renderCatalogo()
    await contadorActual()
    fireEvent.click(screen.getByLabelText('Hombre'))
    expect(await contadorActual()).toBe(2)
  })

  it('el slider de precio máximo filtra los productos por encima del umbral', async () => {
    renderCatalogo()
    const inicial = await contadorActual()
    fireEvent.change(screen.getByLabelText('Precio máximo'), { target: { value: '50' } })
    expect(await contadorActual()).toBeLessThan(inicial)
  })

  it('con ?q= en la URL inicial, muestra el mensaje de resultados de búsqueda', async () => {
    renderCatalogo(['/catalogo?q=nike'])
    await contadorActual()
    expect(screen.getByText(/Resultados para «nike»/)).toBeInTheDocument()
  })

  it('setea el título del documento según haya o no búsqueda activa', async () => {
    renderCatalogo(['/catalogo?q=nike'])
    await contadorActual()
    expect(document.title).toBe('Resultados para "nike" — Catálogo | Shoes Store')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm test CatalogoPage`
Expected: FAIL — `CatalogoPage` still shows a count immediately (`productos` from `useProductos()` starts as `[]`, so it shows "0 productos" synchronously, and `screen.findByText(/productos$/)` will match that stale "0 productos" text before the mocked promise resolves, then the assertions on 4/2 will fail).

- [ ] **Step 3: Add a loading state to `CatalogoPage.jsx`**

In `src/pages/CatalogoPage.jsx`, change line 33 (`const { productos } = useProductos()`) to:

```jsx
  const { productos, cargando } = useProductos()
```

Then replace the `catalog-toolbar` + `ProductGrid` block (currently lines 175-190):

```jsx
          <div className="catalog-toolbar">
            <span className="catalog-count">{cargando ? 'Cargando…' : `${productosFiltrados.length} productos`}</span>
            <select
              className="sort-select"
              aria-label="Ordenar por"
              value={orden}
              onChange={(e) => actualizarFiltros({ orden: e.target.value })}
            >
              <option value="relevancia">Relevancia</option>
              <option value="precio-asc">Precio: menor a mayor</option>
              <option value="precio-desc">Precio: mayor a menor</option>
              <option value="nombre">Nombre A-Z</option>
              <option value="novedades">Novedades primero</option>
            </select>
          </div>
          {cargando ? (
            <p className="catalog-loading">Cargando productos…</p>
          ) : (
            <ProductGrid productos={productosFiltrados} />
          )}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm test CatalogoPage`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/pages/CatalogoPage.jsx src/pages/CatalogoPage.test.jsx
git commit -m "feat: load the catalog page from the real backend, with a loading state"
```

---

## Task 9: Frontend — Rewire `ProductoDetallePage` (fetch by id, real talla availability)

**Files:**
- Modify: `src/pages/ProductoDetallePage.jsx`
- Create: `src/pages/ProductoDetallePage.test.jsx`

**Interfaces:**
- Consumes: `productosService.obtenerProducto(id)` (Task 6). The resolved product's `tallas` field is now `Array<{ talla: number, disponible: boolean }>` (from the backend's `TallaDisponibleDTO`), not a plain array of numbers.

- [ ] **Step 1: Write the failing test**

Create `src/pages/ProductoDetallePage.test.jsx`:

```jsx
import '@testing-library/jest-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { CartProvider } from '../context/CartContext'
import ProductoDetallePage from './ProductoDetallePage'

vi.mock('../services/productosService', () => ({
  obtenerProducto: vi.fn(),
  listarProductos: vi.fn(() => Promise.resolve([])),
}))
import { obtenerProducto } from '../services/productosService'

const PRODUCTO_PRUEBA = {
  id: 3, nombre: 'Air Force 1', marca: 'Nike', precio: 115, genero: 'hombre',
  proposito: 'lifestyle', subcategoria: 'originals', colorway: 'White/White',
  novedad: false, outlet: false, imagen: 'http://localhost:8081/api/imagenes/producto/3',
  tallas: [{ talla: 9, disponible: true }, { talla: 10, disponible: false }],
}

function renderDetalle(id = '3') {
  return render(
    <MemoryRouter initialEntries={[`/producto/${id}`]}>
      <CartProvider>
        <Routes>
          <Route path="/producto/:id" element={<ProductoDetallePage />} />
        </Routes>
      </CartProvider>
    </MemoryRouter>
  )
}

beforeEach(() => {
  obtenerProducto.mockReset()
})

describe('ProductoDetallePage', () => {
  it('muestra el nombre y marca del producto una vez cargado', async () => {
    obtenerProducto.mockResolvedValue(PRODUCTO_PRUEBA)
    renderDetalle()
    expect(await screen.findByRole('heading', { name: 'Air Force 1' })).toBeInTheDocument()
    expect(screen.getByText('Nike')).toBeInTheDocument()
  })

  it('deshabilita las tallas no disponibles según el detalle del backend', async () => {
    obtenerProducto.mockResolvedValue(PRODUCTO_PRUEBA)
    renderDetalle()
    await screen.findByRole('heading', { name: 'Air Force 1' })
    expect(screen.getByRole('button', { name: 'US 9' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'US 10' })).toBeDisabled()
  })

  it('muestra "Producto no encontrado" si la petición falla', async () => {
    obtenerProducto.mockRejectedValue(new Error('404'))
    renderDetalle('999')
    expect(await screen.findByText('Producto no encontrado')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm test ProductoDetallePage`
Expected: FAIL — `ProductoDetallePage` still reads from `useProductos().find(...)`, ignores the mocked `obtenerProducto`.

- [ ] **Step 3: Rewrite `ProductoDetallePage.jsx`**

Replace the imports (lines 1-9):

```jsx
import { useState, useEffect, useRef } from 'react'
import { useParams, Link } from 'react-router-dom'
import { obtenerProducto } from '../services/productosService'
import { useCart } from '../context/CartContext'
import { imagenFallback } from '../utils/imagenes'
import { useDocumentHead } from '../hooks/useDocumentHead'
import { useJsonLd } from '../hooks/useJsonLd'
import { TASA_COP } from '../utils/pricing'
import Toast from '../components/Toast'
```

Replace the component body from its start (`export default function ProductoDetallePage() {`, line 72) through the end of the "not found" early-return block (line 146) with:

```jsx
export default function ProductoDetallePage() {
  const { id } = useParams()
  const { agregarItem } = useCart()
  const [producto, setProducto] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [errorCarga, setErrorCarga] = useState(false)

  useEffect(() => {
    let cancelado = false
    setCargando(true)
    setErrorCarga(false)
    obtenerProducto(id)
      .then((datos) => { if (!cancelado) { setProducto(datos); setCargando(false) } })
      .catch(() => { if (!cancelado) { setErrorCarga(true); setCargando(false) } })
    return () => { cancelado = true }
  }, [id])

  useDocumentHead({
    title: producto ? `${producto.nombre} — ${producto.marca} | Shoes Store` : 'Producto no encontrado | Shoes Store',
    description: producto
      ? `${producto.nombre} de ${producto.marca}${producto.colorway ? ` — ${producto.colorway}` : ''}. Cómpralo en Shoes Store.`
      : 'No pudimos encontrar el producto que buscas.',
    ogImage: producto ? producto.imagen : undefined,
    canonicalPath: `producto/${id}`,
  })

  useJsonLd(
    producto
      ? {
          '@context': 'https://schema.org',
          '@type': 'Product',
          name: producto.nombre,
          brand: { '@type': 'Brand', name: producto.marca },
          image: producto.imagen,
          offers: {
            '@type': 'Offer',
            priceCurrency: 'COP',
            price: String(Math.round(producto.precio * TASA_COP)),
            availability: 'https://schema.org/InStock',
          },
        }
      : null
  )

  const [imgSrc, setImgSrc] = useState(null)
  const [tallaSeleccionada, setTallaSeleccionada] = useState(null)
  const [cantidad, setCantidad] = useState(1)
  const [favorito, setFavorito] = useState(false)
  const [mensajeToast, setMensajeToast] = useState(null)
  const [zoomActivo, setZoomActivo] = useState(false)
  const [lightboxAbierto, setLightboxAbierto] = useState(false)
  const [itemsAbiertos, setItemsAbiertos] = useState(() => new Set())
  const galleryRef = useRef(null)
  const zoomImgRef = useRef(null)
  const zoomRafRef = useRef(0)

  useEffect(() => {
    setImgSrc(producto ? producto.imagen || imagenFallback(producto) : imagenFallback())
    setTallaSeleccionada(null)
    setCantidad(1)
    setFavorito(false)
    setZoomActivo(false)
    setLightboxAbierto(false)
  }, [producto])

  useEffect(() => () => cancelAnimationFrame(zoomRafRef.current), [])

  if (cargando) {
    return (
      <main className="producto-detalle-page">
        <div className="product-wrapper">
          <p className="catalog-loading">Cargando producto…</p>
        </div>
      </main>
    )
  }

  if (!producto || errorCarga) {
    return (
      <main className="producto-detalle-page">
        <div className="product-wrapper">
          <div className="error-container">
            <h2>Producto no encontrado</h2>
            <p>No pudimos encontrar el producto que buscas o hubo un problema de conexión.</p>
            <Link
              to="/catalogo"
              className="btn-primary-full"
              style={{ width: 'auto', padding: '1rem 2rem', display: 'inline-block', textDecoration: 'none' }}
            >
              Volver al catálogo
            </Link>
          </div>
        </div>
      </main>
    )
  }
```

Everything from `const colorwayParts = []` (originally line 148) through the end of the file stays the same, **except** the size-grid mapping. Replace it (originally lines 280-295):

```jsx
              <div className="size-grid">
                {TALLAS_US.map((talla) => {
                  const entrada = producto.tallas.find((t) => t.talla === talla)
                  const disponible = Boolean(entrada?.disponible)
                  return (
                    <button
                      key={talla}
                      className={`size-btn${tallaSeleccionada === talla ? ' active' : ''}`}
                      disabled={!disponible}
                      onClick={() => setTallaSeleccionada(talla)}
                    >
                      US {talla}
                    </button>
                  )
                })}
              </div>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm test ProductoDetallePage`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/pages/ProductoDetallePage.jsx src/pages/ProductoDetallePage.test.jsx
git commit -m "feat: fetch product detail (with real talla availability) from the backend"
```

---

## Task 10: Frontend — Rewire `CartContext` (real catalog instead of static JSON)

**Files:**
- Modify: `src/context/CartContext.jsx` (full rewrite)
- Modify: `src/context/CartContext.test.jsx` (full rewrite)

**Interfaces:**
- Consumes: `productosService.listarProductos()` (Task 6).
- Produces: same public API as before (`useCart() -> { lineas, totalUnidades, totalPrecio, agregarItem, actualizarCantidad, quitarItem, vaciarCarrito }`) — callers (`Navbar`, `ProductoDetallePage`, `CarritoPage`, `PagoPage`) don't change.

- [ ] **Step 1: Write the failing test**

Replace the full contents of `src/context/CartContext.test.jsx`:

```jsx
import { describe, it, expect, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { CartProvider, useCart } from './CartContext'
import { vi } from 'vitest'

vi.mock('../services/productosService', () => ({
  listarProductos: vi.fn(),
}))
import { listarProductos } from '../services/productosService'

const CLAVE = 'shoesStore_carrito'
const CATALOGO = [
  { id: 1, nombre: 'Air Force 1', precio: 115, tallas: [{ talla: 9, disponible: true }, { talla: 10, disponible: true }] },
]
const p = CATALOGO[0]
const talla = p.tallas[0].talla

function wrapper({ children }) {
  return <CartProvider>{children}</CartProvider>
}

async function renderCartConCatalogo() {
  listarProductos.mockResolvedValue(CATALOGO)
  const utils = renderHook(() => useCart(), { wrapper })
  await waitFor(() => expect(listarProductos).toHaveBeenCalled())
  await act(async () => {})
  return utils
}

beforeEach(() => {
  localStorage.clear()
  listarProductos.mockReset()
})

describe('useCart — operaciones básicas', () => {
  it('inicia vacío', async () => {
    const { result } = await renderCartConCatalogo()
    expect(result.current.lineas).toEqual([])
    expect(result.current.totalPrecio).toBe(0)
  })

  it('agrega un producto y calcula el subtotal desde el catálogo real', async () => {
    const { result } = await renderCartConCatalogo()
    act(() => result.current.agregarItem(p.id, talla, 2))
    expect(result.current.totalUnidades).toBe(2)
    expect(result.current.totalPrecio).toBe(p.precio * 2)
  })

  it('fusiona duplicados de id+talla y acota la cantidad a 10', async () => {
    const { result } = await renderCartConCatalogo()
    act(() => result.current.agregarItem(p.id, talla, 8))
    act(() => result.current.agregarItem(p.id, talla, 8))
    expect(result.current.lineas).toHaveLength(1)
    expect(result.current.lineas[0].cantidad).toBe(10)
  })

  it('quita y vacía', async () => {
    const { result } = await renderCartConCatalogo()
    act(() => result.current.agregarItem(p.id, talla, 1))
    act(() => result.current.quitarItem(p.id, talla))
    expect(result.current.lineas).toEqual([])
  })
})

describe('useCart — endurecimiento contra manipulación del storage', () => {
  it('descarta JSON corrupto sin lanzar', async () => {
    localStorage.setItem(CLAVE, '{{{no-es-json')
    const { result } = await renderCartConCatalogo()
    expect(result.current.lineas).toEqual([])
  })

  it('descarta productos inexistentes y tallas inválidas inyectados', async () => {
    localStorage.setItem(CLAVE, JSON.stringify([
      { id: 'hack-999', talla: 9, cantidad: 1 },
      { id: p.id, talla: 999, cantidad: 1 },
    ]))
    const { result } = await renderCartConCatalogo()
    expect(result.current.lineas).toEqual([])
  })

  it('acota cantidades absurdas inyectadas (anti-manipulación)', async () => {
    localStorage.setItem(CLAVE, JSON.stringify([{ id: p.id, talla, cantidad: 99999 }]))
    const { result } = await renderCartConCatalogo()
    await waitFor(() => expect(result.current.lineas[0]?.cantidad).toBe(10))
  })

  it('el precio nunca se lee del storage: siempre del catálogo', async () => {
    localStorage.setItem(CLAVE, JSON.stringify([{ id: p.id, talla, cantidad: 1, precio: 0.01 }]))
    const { result } = await renderCartConCatalogo()
    await waitFor(() => expect(result.current.totalPrecio).toBe(p.precio))
  })
})

describe('useCart — estabilidad de referencia (memoización)', () => {
  it('agregarItem, quitarItem y vaciarCarrito mantienen la misma identidad entre renders sin cambios', async () => {
    const { result, rerender } = await renderCartConCatalogo()
    const agregarItemInicial = result.current.agregarItem
    const quitarItemInicial = result.current.quitarItem
    const vaciarCarritoInicial = result.current.vaciarCarrito

    rerender()

    expect(result.current.agregarItem).toBe(agregarItemInicial)
    expect(result.current.quitarItem).toBe(quitarItemInicial)
    expect(result.current.vaciarCarrito).toBe(vaciarCarritoInicial)
  })

  it('el value completo mantiene la misma identidad entre renders sin cambios en el carrito', async () => {
    const { result, rerender } = await renderCartConCatalogo()
    const valueInicial = result.current

    rerender()

    expect(result.current).toBe(valueInicial)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm test CartContext`
Expected: FAIL — `CartContext` still imports the static `productos.json` and never calls `listarProductos`.

- [ ] **Step 3: Rewrite `CartContext.jsx`**

Replace its full contents:

```jsx
import { createContext, useContext, useState, useEffect, useCallback, useMemo, useRef } from 'react'
import { listarProductos } from '../services/productosService'

/**
 * Carrito de compras.
 *
 * Diseño con seguridad en mente:
 *  - En localStorage solo se guardan {id, talla, cantidad}: los PRECIOS se
 *    derivan siempre del catálogo del backend. Así, manipular el storage
 *    desde DevTools no puede alterar el total a pagar (anti price-tampering).
 *  - Cada entrada se valida contra el catálogo real (backend) en cuanto
 *    llega: id existente, talla disponible para ese producto y cantidad
 *    acotada 1–10. Entradas corruptas o inyectadas se descartan en silencio.
 *  - JSON.parse va protegido con try/catch: un storage corrupto no puede
 *    tumbar la aplicación (anti DoS por datos malformados).
 *
 * El catálogo se carga una vez de forma asíncrona (misma promesa cacheada
 * que usa useProductos): los items del storage se cargan tal cual hasta que
 * el catálogo llega, y en ese momento se saneán contra los datos reales.
 * Nota: si agregarItem/actualizarCantidad se llaman en la ventana (rara,
 * milisegundos) antes de que el catálogo cargue, ese item concreto se
 * descarta por "producto no encontrado" — trade-off aceptado por simplicidad.
 */

const CLAVE_STORAGE = 'shoesStore_carrito'
const CANTIDAD_MAX = 10

/** Valida y normaliza una lista de items contra el catálogo real dado. */
function sanearItems(crudo, catalogo) {
  if (!Array.isArray(crudo)) return []
  const saneados = []
  for (const item of crudo) {
    if (!item || typeof item !== 'object') continue
    const producto = catalogo.find((p) => p.id === item.id)
    if (!producto) continue
    const talla = Number(item.talla)
    const tallaValida = Array.isArray(producto.tallas)
      ? producto.tallas.some((t) => (typeof t === 'object' ? t.talla === talla : t === talla))
      : true
    if (!tallaValida) continue
    const cantidad = Math.min(CANTIDAD_MAX, Math.max(1, Math.trunc(Number(item.cantidad) || 1)))
    const existente = saneados.find((s) => s.id === producto.id && s.talla === talla)
    if (existente) {
      existente.cantidad = Math.min(CANTIDAD_MAX, existente.cantidad + cantidad)
    } else {
      saneados.push({ id: producto.id, talla, cantidad })
    }
  }
  return saneados
}

function cargarCarritoCrudo() {
  try {
    const crudo = JSON.parse(localStorage.getItem(CLAVE_STORAGE) ?? '[]')
    return Array.isArray(crudo) ? crudo : []
  } catch {
    return []
  }
}

const CartContext = createContext(null)

/** Provee el estado del carrito (persistido en localStorage) a toda la app. */
export function CartProvider({ children }) {
  const [catalogo, setCatalogo] = useState([])
  const [items, setItems] = useState(cargarCarritoCrudo)
  const saneadoInicial = useRef(false)

  useEffect(() => {
    let cancelado = false
    listarProductos().then((lista) => {
      if (!cancelado) setCatalogo(lista)
    })
    return () => { cancelado = true }
  }, [])

  // En cuanto el catálogo real llega la primera vez, sanea lo que había
  // quedado del storage (que hasta entonces se guardó sin validar).
  useEffect(() => {
    if (catalogo.length === 0 || saneadoInicial.current) return
    saneadoInicial.current = true
    setItems((previos) => sanearItems(previos, catalogo))
  }, [catalogo])

  useEffect(() => {
    try {
      localStorage.setItem(CLAVE_STORAGE, JSON.stringify(items))
    } catch {
      /* storage lleno o bloqueado: el carrito sigue funcionando en memoria */
    }
  }, [items])

  const agregarItem = useCallback((id, talla, cantidad = 1) => {
    setItems((previos) => sanearItems([...previos, { id, talla, cantidad }], catalogo))
  }, [catalogo])

  const actualizarCantidad = useCallback((id, talla, cantidad) => {
    setItems((previos) =>
      sanearItems(
        previos.map((item) =>
          item.id === id && item.talla === talla ? { ...item, cantidad } : item
        ),
        catalogo
      )
    )
  }, [catalogo])

  const quitarItem = useCallback((id, talla) => {
    setItems((previos) => previos.filter((item) => !(item.id === id && item.talla === talla)))
  }, [])

  const vaciarCarrito = useCallback(() => {
    setItems([])
  }, [])

  const { lineas, totalUnidades, totalPrecio } = useMemo(() => {
    const lineas = items
      .map((item) => {
        const producto = catalogo.find((p) => p.id === item.id)
        return producto ? { ...item, producto, subtotal: producto.precio * item.cantidad } : null
      })
      .filter(Boolean)
    return {
      lineas,
      totalUnidades: lineas.reduce((acc, l) => acc + l.cantidad, 0),
      totalPrecio: lineas.reduce((acc, l) => acc + l.subtotal, 0),
    }
  }, [items, catalogo])

  const value = useMemo(
    () => ({ lineas, totalUnidades, totalPrecio, agregarItem, actualizarCantidad, quitarItem, vaciarCarrito }),
    [lineas, totalUnidades, totalPrecio, agregarItem, actualizarCantidad, quitarItem, vaciarCarrito]
  )

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

/** Hook de acceso al carrito. */
export function useCart() {
  const ctx = useContext(CartContext)
  if (!ctx) throw new Error('useCart debe usarse dentro de CartProvider')
  return ctx
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm test CartContext`
Expected: PASS (9 tests).

- [ ] **Step 5: Run the full suite (Navbar/HomePage should still pass via Task 5's safety net)**

Run: `pnpm test`
Expected: PASS. `Navbar.test.jsx` and `HomePage.test.jsx` now get `catalogo = []` from the stubbed `fetch` (Task 5) — they don't assert on cart/product content, so this is fine.

- [ ] **Step 6: Commit**

```bash
git add src/context/CartContext.jsx src/context/CartContext.test.jsx
git commit -m "feat: validate and price the cart against the real backend catalog"
```

---

## Task 11: Frontend — `authService.js`

**Files:**
- Create: `src/services/authService.js`
- Test: `src/services/authService.test.js`

**Interfaces:**
- Consumes: `apiClient.post` (Task 4).
- Produces: `login({ nombreUsuario, contrasena }) -> Promise<{ token: string, nombreUsuario: string }>`, `registrar({ nombreUsuario, contrasena, nombreCompleto?, email? }) -> Promise<{ token: string, nombreUsuario: string }>`.

- [ ] **Step 1: Write the failing test**

```js
import { describe, it, expect, vi, afterEach } from 'vitest'
import { registrar, login } from './authService'
import { API_BASE_URL } from './apiClient'

afterEach(() => {
  vi.unstubAllGlobals()
})

function mockFetchJson(datos, status = 200) {
  vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({ ok: status < 400, status, json: () => Promise.resolve(datos) })))
}

describe('authService.login', () => {
  it('envía nombreUsuario y contrasena a /api/auth/login', async () => {
    mockFetchJson({ token: 'abc', nombreUsuario: 'ana' })
    const sesion = await login({ nombreUsuario: 'ana', contrasena: '1234' })
    expect(sesion).toEqual({ token: 'abc', nombreUsuario: 'ana' })
    expect(fetch).toHaveBeenCalledWith(
      `${API_BASE_URL}/api/auth/login`,
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ nombreUsuario: 'ana', contrasena: '1234' }) })
    )
  })

  it('propaga el mensaje de error del backend en credenciales inválidas', async () => {
    mockFetchJson({ mensaje: 'Credenciales inválidas.' }, 401)
    await expect(login({ nombreUsuario: 'ana', contrasena: 'mala' })).rejects.toThrow('Credenciales inválidas.')
  })
})

describe('authService.registrar', () => {
  it('envía los datos de registro a /api/auth/registro', async () => {
    mockFetchJson({ token: 'xyz', nombreUsuario: 'nuevo' }, 201)
    const sesion = await registrar({ nombreUsuario: 'nuevo', contrasena: '1234', nombreCompleto: 'Nuevo Usuario', email: '' })
    expect(sesion).toEqual({ token: 'xyz', nombreUsuario: 'nuevo' })
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm test authService`
Expected: FAIL — `authService.js` does not exist.

- [ ] **Step 3: Write the implementation**

```js
import { apiClient } from './apiClient'

export function registrar({ nombreUsuario, contrasena, nombreCompleto, email }) {
  return apiClient.post('/api/auth/registro', { nombreUsuario, contrasena, nombreCompleto, email })
}

export function login({ nombreUsuario, contrasena }) {
  return apiClient.post('/api/auth/login', { nombreUsuario, contrasena })
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm test authService`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/services/authService.js src/services/authService.test.js
git commit -m "feat: add authService, real login/registro calls to the backend"
```

---

## Task 12: Frontend — Rewire `SessionContext` (real auth, persisted token)

**Files:**
- Modify: `src/context/SessionContext.jsx` (full rewrite)
- Modify: `src/context/SessionContext.test.jsx` (full rewrite)

**Interfaces:**
- Consumes: `authService.login`, `authService.registrar` (Task 11).
- Produces: `useSession() -> { usuario: string|null, token: string|null, iniciarSesion(nombreUsuario, contrasena) -> Promise<{ok: boolean, mensaje?: string}>, registrarUsuario(datos) -> Promise<{ok: boolean, mensaje?: string}>, cerrarSesion() -> void }`. `usuario` stays a plain string (the `nombreUsuario`) — `PerfilPage.jsx` renders it directly into an `<input value>` and must not change. `iniciarSesion`'s return type changes from `boolean` to `{ok, mensaje?}` — `LoginPage` (Task 13) depends on this.

- [ ] **Step 1: Write the failing test**

Replace the full contents of `src/context/SessionContext.test.jsx`:

```jsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { SessionProvider, useSession } from './SessionContext'

vi.mock('../services/authService', () => ({
  login: vi.fn(),
  registrar: vi.fn(),
}))
import { login, registrar } from '../services/authService'

function wrapper({ children }) {
  return <SessionProvider>{children}</SessionProvider>
}

beforeEach(() => {
  localStorage.clear()
  login.mockReset()
  registrar.mockReset()
})

describe('useSession', () => {
  it('inicia sin sesión activa', () => {
    const { result } = renderHook(() => useSession(), { wrapper })
    expect(result.current.usuario).toBeNull()
    expect(result.current.token).toBeNull()
  })

  it('inicia sesión con credenciales válidas y persiste el token', async () => {
    login.mockResolvedValue({ token: 'abc123', nombreUsuario: 'ana' })
    const { result } = renderHook(() => useSession(), { wrapper })

    let respuesta
    await act(async () => {
      respuesta = await result.current.iniciarSesion('ana', 'clave-valida')
    })

    expect(respuesta).toEqual({ ok: true })
    expect(result.current.usuario).toBe('ana')
    expect(result.current.token).toBe('abc123')
    expect(JSON.parse(localStorage.getItem('shoesStore_sesion'))).toEqual({ usuario: 'ana', token: 'abc123' })
  })

  it('no inicia sesión cuando el backend rechaza las credenciales', async () => {
    login.mockRejectedValue(new Error('Credenciales inválidas.'))
    const { result } = renderHook(() => useSession(), { wrapper })

    let respuesta
    await act(async () => {
      respuesta = await result.current.iniciarSesion('ana', 'mala')
    })

    expect(respuesta).toEqual({ ok: false, mensaje: 'Credenciales inválidas.' })
    expect(result.current.usuario).toBeNull()
  })

  it('registrarUsuario inicia sesión igual que login', async () => {
    registrar.mockResolvedValue({ token: 'xyz', nombreUsuario: 'nuevo' })
    const { result } = renderHook(() => useSession(), { wrapper })

    await act(async () => {
      await result.current.registrarUsuario({ nombreUsuario: 'nuevo', contrasena: '1234' })
    })

    expect(result.current.usuario).toBe('nuevo')
    expect(result.current.token).toBe('xyz')
  })

  it('restaura la sesión guardada en localStorage al montar', () => {
    localStorage.setItem('shoesStore_sesion', JSON.stringify({ usuario: 'previo', token: 'tok-previo' }))
    const { result } = renderHook(() => useSession(), { wrapper })
    expect(result.current.usuario).toBe('previo')
    expect(result.current.token).toBe('tok-previo')
  })

  it('cierra sesión y limpia el storage', async () => {
    login.mockResolvedValue({ token: 'abc123', nombreUsuario: 'ana' })
    const { result } = renderHook(() => useSession(), { wrapper })
    await act(async () => { await result.current.iniciarSesion('ana', 'clave-valida') })

    act(() => result.current.cerrarSesion())

    expect(result.current.usuario).toBeNull()
    expect(localStorage.getItem('shoesStore_sesion')).toBeNull()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm test SessionContext`
Expected: FAIL — `SessionContext` still validates the hardcoded `admin`/`shoes2026` mock synchronously.

- [ ] **Step 3: Rewrite `SessionContext.jsx`**

Replace its full contents:

```jsx
import { createContext, useContext, useState } from 'react'
import { login as loginApi, registrar as registrarApi } from '../services/authService'

const CLAVE_SESION = 'shoesStore_sesion'

function leerSesionGuardada() {
  try {
    const datos = JSON.parse(localStorage.getItem(CLAVE_SESION) ?? 'null')
    if (datos && typeof datos.usuario === 'string' && typeof datos.token === 'string') return datos
    return null
  } catch {
    return null
  }
}

function guardarSesion(sesion) {
  try {
    if (sesion) localStorage.setItem(CLAVE_SESION, JSON.stringify(sesion))
    else localStorage.removeItem(CLAVE_SESION)
  } catch {
    /* storage bloqueado: la sesión sigue funcionando solo en memoria */
  }
}

const SessionContext = createContext(null)

/** Provee el estado de sesión real (token del backend) a la app. */
export function SessionProvider({ children }) {
  const [sesion, setSesion] = useState(leerSesionGuardada)

  async function iniciarSesion(nombreUsuario, contrasena) {
    try {
      const { token, nombreUsuario: usuario } = await loginApi({ nombreUsuario, contrasena })
      const nuevaSesion = { usuario, token }
      setSesion(nuevaSesion)
      guardarSesion(nuevaSesion)
      return { ok: true }
    } catch (error) {
      return { ok: false, mensaje: error.message || 'No se pudo iniciar sesión.' }
    }
  }

  async function registrarUsuario(datos) {
    try {
      const { token, nombreUsuario: usuario } = await registrarApi(datos)
      const nuevaSesion = { usuario, token }
      setSesion(nuevaSesion)
      guardarSesion(nuevaSesion)
      return { ok: true }
    } catch (error) {
      return { ok: false, mensaje: error.message || 'No se pudo completar el registro.' }
    }
  }

  function cerrarSesion() {
    setSesion(null)
    guardarSesion(null)
  }

  const value = {
    usuario: sesion?.usuario ?? null,
    token: sesion?.token ?? null,
    iniciarSesion,
    registrarUsuario,
    cerrarSesion,
  }

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

/** Hook de acceso a la sesión actual. */
export function useSession() {
  const ctx = useContext(SessionContext)
  if (!ctx) throw new Error('useSession debe usarse dentro de SessionProvider')
  return ctx
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm test SessionContext`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/context/SessionContext.jsx src/context/SessionContext.test.jsx
git commit -m "feat: authenticate against the real backend and persist the session token"
```

---

## Task 13: Frontend — Rewire `LoginPage`

**Files:**
- Modify: `src/pages/LoginPage.jsx:24-40`
- Create: `src/pages/LoginPage.test.jsx`

**Interfaces:**
- Consumes: `useSession().iniciarSesion(usuario, clave) -> Promise<{ok, mensaje?}>` (Task 12).

- [ ] **Step 1: Write the failing test**

Create `src/pages/LoginPage.test.jsx`:

```jsx
import '@testing-library/jest-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { SessionProvider } from '../context/SessionContext'
import LoginPage from './LoginPage'

vi.mock('../services/authService', () => ({
  login: vi.fn(),
  registrar: vi.fn(),
}))
import { login } from '../services/authService'

function renderLogin() {
  return render(
    <MemoryRouter>
      <SessionProvider>
        <LoginPage />
      </SessionProvider>
    </MemoryRouter>
  )
}

beforeEach(() => {
  localStorage.clear()
  login.mockReset()
})

describe('LoginPage', () => {
  it('muestra error si faltan campos', () => {
    renderLogin()
    fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Por favor completa todos los campos.')
  })

  it('inicia sesión con credenciales válidas', async () => {
    login.mockResolvedValue({ token: 'abc', nombreUsuario: 'ana' })
    renderLogin()
    fireEvent.change(screen.getByLabelText('Nombre de usuario'), { target: { value: 'ana' } })
    fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: 'clave-valida' } })
    fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))
    await waitFor(() => expect(login).toHaveBeenCalledWith({ nombreUsuario: 'ana', contrasena: 'clave-valida' }))
  })

  it('muestra el mensaje de error del backend si las credenciales son inválidas', async () => {
    login.mockRejectedValue(new Error('Usuario o contraseña incorrectos.'))
    renderLogin()
    fireEvent.change(screen.getByLabelText('Nombre de usuario'), { target: { value: 'ana' } })
    fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: 'mala' } })
    fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Usuario o contraseña incorrectos.')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm test LoginPage`
Expected: FAIL — `manejarEnvio` still calls the old synchronous `iniciarSesion(usuario, clave)` and reads its return as a boolean.

- [ ] **Step 3: Update `manejarEnvio` in `LoginPage.jsx`**

Replace lines 24-40:

```jsx
  async function manejarEnvio(e) {
    e.preventDefault()

    if (!usuario.trim() || !clave) {
      mostrarError('Por favor completa todos los campos.')
      return
    }

    const resultado = await iniciarSesion(usuario.trim(), clave)
    if (resultado.ok) {
      navigate('/')
    } else {
      mostrarError(resultado.mensaje || 'Usuario o contraseña incorrectos.')
      setClave('')
      claveRef.current?.focus()
    }
  }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm test LoginPage`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/pages/LoginPage.jsx src/pages/LoginPage.test.jsx
git commit -m "feat: authenticate LoginPage against the real backend"
```

---

## Task 14: Frontend — Rewire `RegistroPage` (add username field, real registration)

**Files:**
- Modify: `src/pages/RegistroPage.jsx` (full rewrite)
- Create: `src/pages/RegistroPage.test.jsx`

**Interfaces:**
- Consumes: `useSession().registrarUsuario({ nombreUsuario, contrasena, nombreCompleto }) -> Promise<{ok, mensaje?}>` (Task 12).

- [ ] **Step 1: Write the failing test**

Create `src/pages/RegistroPage.test.jsx`:

```jsx
import '@testing-library/jest-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { SessionProvider } from '../context/SessionContext'
import RegistroPage from './RegistroPage'

vi.mock('../services/authService', () => ({
  login: vi.fn(),
  registrar: vi.fn(),
}))
import { registrar } from '../services/authService'

function renderRegistro() {
  return render(
    <MemoryRouter>
      <SessionProvider>
        <RegistroPage />
      </SessionProvider>
    </MemoryRouter>
  )
}

beforeEach(() => {
  localStorage.clear()
  registrar.mockReset()
})

describe('RegistroPage', () => {
  it('envía nombreUsuario, contrasena y nombreCompleto al registrarse', async () => {
    registrar.mockResolvedValue({ token: 'nuevo-token', nombreUsuario: 'juand' })
    renderRegistro()
    fireEvent.change(screen.getByLabelText('Nombre de usuario'), { target: { value: 'juand' } })
    fireEvent.change(screen.getByLabelText('Nombre completo'), { target: { value: 'Juan Diaz' } })
    fireEvent.change(screen.getByLabelText('Cédula'), { target: { value: '123456' } })
    fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: 'clave-segura' } })
    fireEvent.click(screen.getByRole('button', { name: 'Registrarse' }))

    await waitFor(() =>
      expect(registrar).toHaveBeenCalledWith({
        nombreUsuario: 'juand',
        contrasena: 'clave-segura',
        nombreCompleto: 'Juan Diaz',
      })
    )
  })

  it('muestra el error del backend si el nombre de usuario ya existe', async () => {
    registrar.mockRejectedValue(new Error('El nombre de usuario ya está en uso.'))
    renderRegistro()
    fireEvent.change(screen.getByLabelText('Nombre de usuario'), { target: { value: 'juand' } })
    fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: 'clave-segura' } })
    fireEvent.click(screen.getByRole('button', { name: 'Registrarse' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('El nombre de usuario ya está en uso.')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm test RegistroPage`
Expected: FAIL — `RegistroPage` has no "Nombre de usuario" field and never calls `registrarUsuario`.

- [ ] **Step 3: Rewrite `RegistroPage.jsx`**

Replace its full contents:

```jsx
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useSession } from '../context/SessionContext'

/**
 * Formulario de registro, conectado a POST /api/auth/registro.
 * Cédula y fecha de nacimiento se conservan en el formulario (fidelidad con
 * registro.html original) pero el backend (Usuario) no las modela hoy, así
 * que no viajan en la petición — solo nombreUsuario/contrasena/nombreCompleto.
 */
export default function RegistroPage() {
  const { registrarUsuario } = useSession()
  const navigate = useNavigate()
  const [nombreUsuario, setNombreUsuario] = useState('')
  const [nombre, setNombre] = useState('')
  const [cedula, setCedula] = useState('')
  const [clave, setClave] = useState('')
  const [nacimiento, setNacimiento] = useState('')
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  async function manejarEnvio(e) {
    e.preventDefault()
    if (enviando) return
    setEnviando(true)
    const resultado = await registrarUsuario({
      nombreUsuario: nombreUsuario.trim(),
      contrasena: clave,
      nombreCompleto: nombre.trim(),
    })
    setEnviando(false)
    if (resultado.ok) {
      navigate('/')
    } else {
      setError(resultado.mensaje || 'No se pudo completar el registro.')
    }
  }

  return (
    <main className="auth-view">
      <div className="cardRegister">
        <nav className="top-nav" aria-label="Navegación superior">
          <Link to="/login" className="back-link">
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline></svg>
            Volver
          </Link>
        </nav>

        <Link to="/" className="brand-tag">SHOES<span className="brand-dot">.</span>STORE</Link>

        <h1 className="text1">Crear una cuenta</h1>
        <h2 className="text2">Únete a la plataforma de sneakers más premium</h2>

        <form onSubmit={manejarEnvio}>
          <div className="form">
            <label htmlFor="username">Nombre de usuario</label>
            <input
              id="username"
              name="username"
              type="text"
              placeholder="Elige un nombre de usuario"
              autoComplete="username"
              value={nombreUsuario}
              onChange={(e) => { setNombreUsuario(e.target.value); setError('') }}
              required
            />
          </div>

          <div className="form">
            <label htmlFor="name">Nombre completo</label>
            <input
              id="name"
              name="name"
              type="text"
              placeholder="Ingresa tu nombre"
              autoComplete="name"
              value={nombre}
              onChange={(e) => { setNombre(e.target.value); setError('') }}
              required
            />
          </div>

          <div className="form">
            <label htmlFor="cedula">Cédula</label>
            <input
              id="cedula"
              name="cedula"
              type="text"
              inputMode="numeric"
              placeholder="Ingresa tu documento"
              autoComplete="off"
              maxLength={10}
              value={cedula}
              onChange={(e) => setCedula(e.target.value.replace(/\D/g, ''))}
              required
            />
          </div>

          <div className="form">
            <label htmlFor="password">Contraseña</label>
            <input
              id="password"
              name="new-password"
              type="password"
              placeholder="Crea una contraseña segura"
              autoComplete="new-password"
              value={clave}
              onChange={(e) => { setClave(e.target.value); setError('') }}
              required
            />
          </div>

          <div className="form">
            <label htmlFor="birthdate">Fecha de nacimiento</label>
            <input
              id="birthdate"
              name="bday"
              type="date"
              autoComplete="bday"
              value={nacimiento}
              onChange={(e) => setNacimiento(e.target.value)}
              required
            />
          </div>

          <div className={`login-error${error ? ' visible' : ''}`} role="alert" aria-live="polite">{error}</div>

          <button type="submit" className="btn2" disabled={enviando}>Registrarse</button>
        </form>

        <div className="auth-link">
          <Link to="/login">¿Ya tienes una cuenta? Inicia sesión</Link>
        </div>

        <p className="footer">Asegúrate de ingresar datos reales para tu proceso de envío.</p>
      </div>
    </main>
  )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm test RegistroPage`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/pages/RegistroPage.jsx src/pages/RegistroPage.test.jsx
git commit -m "feat: register real users through RegistroPage"
```

---

## Task 15: Frontend — `pedidosService.js`

**Files:**
- Create: `src/services/pedidosService.js`
- Test: `src/services/pedidosService.test.js`

**Interfaces:**
- Consumes: `apiClient.post` (Task 4).
- Produces: `crearPedido(datos, token) -> Promise<object>` where `datos = { metodoPago, banco?, envioCop, items: [{ productoId, talla, cantidad, precioUnitario }] }` and the resolved object includes `numeroOrden` (backend's `Pedido.numeroOrden`).

- [ ] **Step 1: Write the failing test**

```js
import { describe, it, expect, vi, afterEach } from 'vitest'
import { crearPedido } from './pedidosService'
import { API_BASE_URL } from './apiClient'

afterEach(() => {
  vi.unstubAllGlobals()
})

it('envía el pedido con el token en Authorization', async () => {
  vi.stubGlobal('fetch', vi.fn(() =>
    Promise.resolve({ ok: true, status: 201, json: () => Promise.resolve({ id: 10, numeroOrden: 'SS-000010' }) })
  ))
  const datos = { metodoPago: 'contraentrega', envioCop: 0, items: [{ productoId: 1, talla: 9, cantidad: 1, precioUnitario: 115 }] }
  const pedido = await crearPedido(datos, 'mi-token')
  expect(pedido).toEqual({ id: 10, numeroOrden: 'SS-000010' })
  expect(fetch).toHaveBeenCalledWith(
    `${API_BASE_URL}/api/pedidos`,
    expect.objectContaining({
      method: 'POST',
      body: JSON.stringify(datos),
      headers: expect.objectContaining({ Authorization: 'Bearer mi-token' }),
    })
  )
})

it('propaga el mensaje del backend cuando el stock es insuficiente', async () => {
  vi.stubGlobal('fetch', vi.fn(() =>
    Promise.resolve({ ok: false, status: 409, json: () => Promise.resolve({ mensaje: 'Stock insuficiente.' }) })
  ))
  await expect(crearPedido({ items: [] }, 'tok')).rejects.toThrow('Stock insuficiente.')
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm test pedidosService`
Expected: FAIL — `pedidosService.js` does not exist.

- [ ] **Step 3: Write the implementation**

```js
import { apiClient } from './apiClient'

/** Crea un pedido real a partir del carrito. Requiere una sesión activa (token). */
export function crearPedido(datos, token) {
  return apiClient.post('/api/pedidos', datos, { token })
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm test pedidosService`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/services/pedidosService.js src/services/pedidosService.test.js
git commit -m "feat: add pedidosService, real order creation against the backend"
```

---

## Task 16: Frontend — Rewire `PagoPage` (require login, create a real order)

**Files:**
- Modify: `src/pages/PagoPage.jsx`
- Modify: `src/pages/PagoPage.test.jsx` (full rewrite)

**Interfaces:**
- Consumes: `useSession() -> { usuario, token }` (Task 12), `pedidosService.crearPedido` (Task 15).

- [ ] **Step 1: Write the failing test**

Replace the full contents of `src/pages/PagoPage.test.jsx`:

```jsx
import '@testing-library/jest-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { SessionProvider } from '../context/SessionContext'
import { CartProvider } from '../context/CartContext'
import PagoPage from './PagoPage'

vi.mock('../services/productosService', () => ({
  listarProductos: vi.fn(),
}))
vi.mock('../services/pedidosService', () => ({
  crearPedido: vi.fn(),
}))
import { listarProductos } from '../services/productosService'
import { crearPedido } from '../services/pedidosService'

const CLAVE_CARRITO = 'shoesStore_carrito'
const CLAVE_SESION = 'shoesStore_sesion'
const CATALOGO = [{ id: 1, nombre: 'Air Force 1', precio: 115, tallas: [{ talla: 9, disponible: true }] }]
const p = CATALOGO[0]
const talla = p.tallas[0].talla

function renderPagoPage() {
  localStorage.setItem(CLAVE_SESION, JSON.stringify({ usuario: 'ana', token: 'tok-123' }))
  localStorage.setItem(CLAVE_CARRITO, JSON.stringify([{ id: p.id, talla, cantidad: 1 }]))
  return render(
    <MemoryRouter>
      <SessionProvider>
        <CartProvider>
          <PagoPage />
        </CartProvider>
      </SessionProvider>
    </MemoryRouter>
  )
}

function llenarFormularioValido() {
  fireEvent.change(screen.getByLabelText('Número de tarjeta'), { target: { value: '4111111111111111' } })
  fireEvent.change(screen.getByLabelText('Titular'), { target: { value: 'Juan Perez' } })
  fireEvent.change(screen.getByLabelText('Vence (MM/AA)'), { target: { value: '12/28' } })
  fireEvent.change(screen.getByLabelText('CVV'), { target: { value: '123' } })
}

beforeEach(() => {
  localStorage.clear()
  listarProductos.mockReset()
  listarProductos.mockResolvedValue(CATALOGO)
  crearPedido.mockReset()
})

describe('PagoPage — validación de formulario', () => {
  it('muestra error si el número de tarjeta es inválido', async () => {
    renderPagoPage()
    await screen.findByRole('button', { name: /Pagar/ })
    llenarFormularioValido()
    fireEvent.change(screen.getByLabelText('Número de tarjeta'), { target: { value: '123' } })
    fireEvent.click(screen.getByRole('button', { name: /Pagar/ }))
    expect(screen.getByRole('alert')).toHaveTextContent('Número de tarjeta inválido')
  })

  it('muestra error si el CVV es inválido', async () => {
    renderPagoPage()
    await screen.findByRole('button', { name: /Pagar/ })
    llenarFormularioValido()
    fireEvent.change(screen.getByLabelText('CVV'), { target: { value: '12' } })
    fireEvent.click(screen.getByRole('button', { name: /Pagar/ }))
    expect(screen.getByRole('alert')).toHaveTextContent('CVV inválido')
  })

  it('muestra error si el vencimiento no tiene formato MM/AA', async () => {
    renderPagoPage()
    await screen.findByRole('button', { name: /Pagar/ })
    llenarFormularioValido()
    fireEvent.change(screen.getByLabelText('Vence (MM/AA)'), { target: { value: '13/99' } })
    fireEvent.click(screen.getByRole('button', { name: /Pagar/ }))
    expect(screen.getByRole('alert')).toHaveTextContent('Vencimiento en formato MM/AA')
  })

  it('reactiva el botón de pago tras un intento inválido (el guard no lo deja bloqueado)', async () => {
    renderPagoPage()
    await screen.findByRole('button', { name: /Pagar/ })
    llenarFormularioValido()
    fireEvent.change(screen.getByLabelText('Número de tarjeta'), { target: { value: '123' } })
    const boton = screen.getByRole('button', { name: /Pagar/ })
    fireEvent.click(boton)
    expect(screen.getByRole('alert')).toHaveTextContent('Número de tarjeta inválido')
    expect(boton).not.toBeDisabled()
  })

  it('confirma el pedido con datos válidos, llama al backend y vacía el carrito', async () => {
    crearPedido.mockResolvedValue({ id: 1, numeroOrden: 'SS-000001' })
    renderPagoPage()
    await screen.findByRole('button', { name: /Pagar/ })
    llenarFormularioValido()
    fireEvent.click(screen.getByRole('button', { name: /Pagar/ }))
    expect(await screen.findByText('¡Pedido confirmado!')).toBeInTheDocument()
    expect(screen.getByText(/SS-000001/)).toBeInTheDocument()
    expect(localStorage.getItem(CLAVE_CARRITO)).toBe(JSON.stringify([]))
  })

  it('muestra el error del backend si el pago falla (ej. stock insuficiente)', async () => {
    crearPedido.mockRejectedValue(new Error('Stock insuficiente.'))
    renderPagoPage()
    await screen.findByRole('button', { name: /Pagar/ })
    llenarFormularioValido()
    fireEvent.click(screen.getByRole('button', { name: /Pagar/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Stock insuficiente.')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm test PagoPage`
Expected: FAIL — `PagoPage` doesn't require a session and `pagar()` still generates a client-side fake order number instead of calling `crearPedido`.

- [ ] **Step 3: Rewrite `PagoPage.jsx`**

Replace the imports (lines 1-4):

```jsx
import { useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { useCart } from '../context/CartContext'
import { useSession } from '../context/SessionContext'
import { crearPedido } from '../services/pedidosService'
import { TASA_COP, calcularEnvioCOP } from '../utils/pricing'
```

Replace the component from its start (`export default function PagoPage() {`, line 24) through the end of the `pagar` function (line 72) with:

```jsx
export default function PagoPage() {
  const { lineas, totalPrecio, vaciarCarrito } = useCart()
  const { usuario, token } = useSession()

  const [metodo, setMetodo] = useState('tarjeta')
  const [numero, setNumero] = useState('')
  const [titular, setTitular] = useState('')
  const [vence, setVence] = useState('')
  const [cvv, setCvv] = useState('')
  const [banco, setBanco] = useState('')
  const [error, setError] = useState('')
  const [pedidoConfirmado, setPedidoConfirmado] = useState(null)
  const [enviando, setEnviando] = useState(false)

  const totalCOP = totalPrecio * TASA_COP
  const envioCOP = calcularEnvioCOP(totalCOP, lineas.length)

  if (!usuario) {
    return <Navigate to="/login" replace />
  }

  if (lineas.length === 0 && !pedidoConfirmado) {
    return <Navigate to="/carrito" replace />
  }

  function validar() {
    if (metodo === 'tarjeta') {
      const digitos = numero.replace(/\s/g, '')
      if (!/^\d{13,19}$/.test(digitos)) return 'Número de tarjeta inválido (13–19 dígitos).'
      if (titular.trim().length < 3) return 'Ingresa el nombre del titular.'
      if (!/^(0[1-9]|1[0-2])\/\d{2}$/.test(vence)) return 'Vencimiento en formato MM/AA.'
      if (!/^\d{3,4}$/.test(cvv)) return 'CVV inválido (3–4 dígitos).'
    }
    if (metodo === 'pse' && !banco) return 'Selecciona tu banco.'
    return ''
  }

  async function pagar(e) {
    e.preventDefault()
    if (enviando) return
    setEnviando(true)
    const mensajeError = validar()
    if (mensajeError) {
      setError(mensajeError)
      setEnviando(false)
      return
    }
    try {
      const pedido = await crearPedido(
        {
          metodoPago: metodo,
          banco: metodo === 'pse' ? banco : undefined,
          envioCop: envioCOP,
          items: lineas.map((linea) => ({
            productoId: linea.id,
            talla: linea.talla,
            cantidad: linea.cantidad,
            precioUnitario: linea.producto.precio,
          })),
        },
        token
      )
      // Simulación: los datos de tarjeta se descartan aquí mismo — nunca se persisten.
      setNumero(''); setTitular(''); setVence(''); setCvv('')
      setPedidoConfirmado(pedido.numeroOrden)
      vaciarCarrito()
    } catch (err) {
      setError(err.message || 'No se pudo procesar el pedido. Intenta de nuevo.')
    } finally {
      setEnviando(false)
    }
  }
```

Everything from the `if (pedidoConfirmado) {` block (originally line 74) to the end of the file stays the same.

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm test PagoPage`
Expected: PASS (6 tests).

- [ ] **Step 5: Run the entire frontend test suite**

Run: `pnpm test`
Expected: all tests green.

- [ ] **Step 6: Commit**

```bash
git add src/pages/PagoPage.jsx src/pages/PagoPage.test.jsx
git commit -m "feat: require login and create a real order when checking out"
```

---

## Task 17: Manual end-to-end verification + evidence write-up

This task has no automated test — it's the manual verification the GA8-220501096-AA1-EV01 guide asks for, plus the write-up you'll convert to the submitted PDF.

**Files:**
- Create: `INTEGRACION.md` at the frontend repo root (`D:\juandiplay\cursito html\sena\shoes'sStore 2.0\INTEGRACION.md`)

- [ ] **Step 1: Start all three services, in order**

```bash
# Terminal 1 — inventario (puerto 8080)
cd "D:\juandiplay\shoes-store-inventory-2.0\zapateria-backend" && mvn spring-boot:run

# Terminal 2 — tienda (puerto 8081)
cd "D:\juandiplay\cursito html\sena\shoesstore-tienda-api" && mvn spring-boot:run

# Terminal 3 — frontend (puerto 5173)
cd "D:\juandiplay\cursito html\sena\shoes'sStore 2.0" && pnpm dev
```

- [ ] **Step 2: Walk the golden path in the browser and confirm each piece works**

Open `http://localhost:5173` and verify, in order:
1. Home/Catálogo shows real products with the self-generated placeholder image (open devtools Network tab — confirm the image request goes to `localhost:8081/api/imagenes/producto/...`, never to `sneakerjagers.com`).
2. `/registro` creates a new user (check `shoesstore_tienda.usuarios` in MySQL, or watch the network response) and logs you in automatically.
3. `/login` with those same credentials works; logging out and back in works too.
4. Product detail page shows real talla availability (tallas seeded with stock — see `data-seed.sql` comments — should be enabled; the rest disabled).
5. Add to cart, go to `/carrito` → `/pago`, submit a valid order (any payment method) — confirms with a real `numeroOrden`, and a row appears in `shoesstore_tienda.pedidos`.
6. Try to reach `/pago` while logged out (clear the session in devtools) — confirms it redirects to `/login`.

If any step fails, fix it before moving on — this is the actual integration test the evidence is graded on.

- [ ] **Step 3: Write `INTEGRACION.md`**

```markdown
# Integración frontend-backend — GA8-220501096-AA1-EV01

## Servicios y cómo levantarlos

| Servicio | Puerto | Comando |
|---|---|---|
| `zapateria-backend` (inventario) | 8080 | `mvn spring-boot:run` desde `shoes-store-inventory-2.0/zapateria-backend` |
| `shoesstore-tienda-api` (catálogo, auth, pedidos, imágenes) | 8081 | `mvn spring-boot:run` desde `shoesstore-tienda-api` |
| `shoes-store` (frontend React) | 5173 | `pnpm dev` desde `shoes'sStore 2.0` |

Orden de arranque: inventario → tienda → frontend (la tienda depende del inventario en cada consulta de stock; el frontend depende de la tienda para todo).

## Módulos integrados

- **Imágenes propias**: `ImagenController`/`ImagenService` (backend) generan un SVG placeholder por producto en el momento — sin depender de ningún CDN de terceros. Antes, `productos.json` apuntaba a `static.sneakerjagers.com` (contenido con derechos de autor de las marcas); ahora todo producto sirve su imagen desde `/api/imagenes/producto/{id}`, bajo nuestro propio dominio.
- **Catálogo**: `productosService` (frontend) reemplaza el `productos.json` local por `GET /api/productos` y `GET /api/productos/{id}`, incluyendo disponibilidad real de tallas (consultada al servicio de inventario).
- **Autenticación**: `authService` + `SessionContext` reemplazan las credenciales mock (`admin`/`shoes2026`) por `POST /api/auth/registro` y `POST /api/auth/login` reales, con el token persistido en `localStorage`.
- **Pedidos**: `pedidosService` reemplaza el número de orden generado en el cliente por `POST /api/pedidos` real, que valida stock contra el inventario y persiste el pedido.

## Capas y patrones aplicados

- Backend en capas: `Controller` → `Service` → `Repository`/`Client`, DTOs dedicados por caso de uso (`ProductoResumenDTO`, `ProductoDetalleDTO`, `SesionDTO`, ...).
- Frontend: capa `services/` (`apiClient` → `productosService`/`authService`/`pedidosService`) como único punto de contacto con la red, consumida por hooks (`useProductos`) y contexts (`CartContext`, `SessionContext`) — ningún componente de UI llama a `fetch` directamente.
- Manejo de errores centralizado en ambos lados: `ManejadorErrores` (backend, `@RestControllerAdvice`) y `ApiError` (frontend, un solo lugar donde se traduce `{mensaje}` del backend a un `Error` de JS).

## Pruebas

- Backend: `mvn test` (JUnit 5 + Mockito) — cubre `ImagenService`, `ImagenController`, `ProductoService`.
- Frontend: `pnpm test` (Vitest + Testing Library) — cubre `apiClient`, `productosService`, `authService`, `pedidosService`, `useProductos`, `CartContext`, `SessionContext`, y las páginas `CatalogoPage`, `ProductoDetallePage`, `LoginPage`, `RegistroPage`, `PagoPage`.

## Capturas / evidencia para el PDF

*(pendiente — adjuntar aquí capturas de: catálogo con imágenes propias cargando desde `localhost:8081`, registro/login exitoso, detalle de producto con tallas reales, pedido confirmado con `numeroOrden`, y la fila correspondiente en la tabla `pedidos` de MySQL)*
```

- [ ] **Step 4: Commit**

```bash
git add INTEGRACION.md
git commit -m "docs: document the frontend-backend integration and how to run it"
```

- [ ] **Step 5: Manual — assemble the PDF for GA8-220501096-AA1-EV01**

This is on you, not code: take the screenshots called out in step 2, drop them into `INTEGRACION.md`'s last section (or a separate doc), export to PDF, and submit per the guide's "Lineamientos generales para la entrega" (product: source code + docs + URL; format: PDF). Follow the `GA7-220501096-AA5-EV04` folder as a layout reference (`capturas/`, `video/` if needed, a top-level write-up, `enlace_repositorio.txt`).

---

## Self-Review Notes

- **Spec coverage**: Section A (own image module) → Tasks 1-2. Section B (frontend↔backend integration: productos/imágenes/auth/pedidos) → Tasks 3, 6-16. Section C (loading/error states) → Tasks 8, 9, 16 (each rewired page gets its own loading/error branch; `Toast` is reused as-is, no changes needed to it). Section D (tests) → every task pairs code with a test. Section E (evidence package) → Task 17. "Fuera de alcance" items (image upload, inventario changes) are correctly not covered anywhere above.
- **Known trade-off carried over from the design doc**: `PerfilPage` is intentionally left untouched beyond what `SessionContext`'s unchanged `usuario` string already gives it — no order history UI, since the spec listed that as optional ("si expone historial") and it isn't needed for the flows the guide grades.
