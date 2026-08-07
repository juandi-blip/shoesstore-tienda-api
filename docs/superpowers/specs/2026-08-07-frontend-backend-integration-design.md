# Integración frontend-backend + módulo propio de imágenes

Evidencia SENA: GA8-220501096-AA1-EV01 (Desarrollar software a partir de la integración de sus módulos componentes).

## Contexto

El ecosistema Shoes'sStore tiene tres servicios independientes hoy:

- `zapateria-backend` (`shoes-store-inventory-2.0/zapateria-backend`, puerto 8080): inventario real — productos por variante talla/color con stock, categorías, proveedores.
- `shoesstore-tienda-api` (puerto 8081): catálogo público, auth y pedidos. Ya consume `zapateria-backend` vía `InventarioClient`. CORS ya habilitado para `http://localhost:5173`.
- `shoes'sStore 2.0` (frontend React + Vite, puerto 5173): storefront público. **Hoy no llama a ningún backend** — lee `src/data/productos.json` en local, y ese JSON referencia imágenes de producto hotlinkeadas a `static.sneakerjagers.com` (CDN de un tercero, contenido con derechos de autor de las marcas).

Ese hotlinking es un riesgo real si el proyecto se despliega a un hosting público: la fuente de las imágenes no es nuestra y puede resultar en un cierre/DMCA. El frontend ya tiene un generador de placeholder SVG (`src/utils/imagenes.js` → `imagenFallback`) usado hoy solo como fallback cuando el link externo muere.

## Objetivo

Integrar el frontend con `shoesstore-tienda-api` de punta a punta (productos, imágenes, auth, pedidos), eliminando la dependencia de imágenes de terceros mediante un módulo de imágenes propio en el backend.

## A. Módulo propio de imágenes (backend)

Nuevo paquete `com.shoesstore.tienda.imagenes` en `shoesstore-tienda-api`:

- `ImagenController`: `GET /api/imagenes/producto/{id}` — resuelve el `Producto` por id, genera un SVG determinista (mismo estilo visual que el `imagenFallback` actual del frontend: fondo oscuro, marca + nombre del producto) y lo devuelve con `Content-Type: image/svg+xml`.
- `ImagenService`: lógica pura de generación del SVG (sin IO, sin dependencias externas — testeable con datos simples de marca/nombre).
- No requiere entidad, repositorio ni almacenamiento: es 100% computado a partir de datos que el `Producto` ya tiene.
- El campo `imagen` (URL externa) del modelo `Producto` deja de usarse como fuente real de imagen. `ProductoDetalleDTO` (y el DTO de listado que exponga `ProductoController`) exponen en su lugar la ruta relativa al nuevo endpoint propio (p. ej. `/api/imagenes/producto/{id}`), para que el frontend nunca necesite tocar una URL externa.

## B. Integración frontend → backend

Nuevo `src/services/` en `shoes'sStore 2.0`:

- `apiClient.js`: wrapper mínimo sobre `fetch`, base URL desde `import.meta.env.VITE_API_URL` (default `http://localhost:8081`), maneja JSON y errores HTTP de forma centralizada.
- `productosService.js`: `listarProductos()` → `GET /api/productos`, `obtenerProducto(id)` → `GET /api/productos/{id}`. Reemplaza el import directo de `productos.json` en `CatalogoPage` y `ProductoDetallePage`. La URL de imagen de cada producto pasa a ser la del endpoint propio del punto A (con `imagenFallback` como respaldo solo si esa llamada fallara).
- `authService.js`: `registrar(datos)` → `POST /api/auth/registro`, `login(credenciales)` → `POST /api/auth/login`. Conectado a `LoginPage`, `RegistroPage` y `SessionContext` (que pasa a guardar el token real de sesión en vez de estado mock/local).
- `pedidosService.js`: `crearPedido(payload)` → `POST /api/pedidos` desde `PagoPage` (checkout real del carrito), y si `PedidoController` expone historial, se usa en `PerfilPage`.
- Todas las llamadas a endpoints protegidos añaden el header `Authorization` con el token de sesión (el backend ya valida esto vía `TokenAuthFilter`).

## C. Manejo de errores y estados de carga

Cada página que pasa a depender de red obtiene estado explícito de carga/error (spinner o mensaje, reutilizando el componente `Toast` existente). Los errores del backend (`RespuestaDTO` de error, `InventarioNoDisponibleException`, `StockInsuficienteException`) se traducen a mensajes legibles para el usuario en vez de mostrarse crudos.

## D. Pruebas

- Backend: `ImagenServiceTest` (unitaria, sin Spring context) cubriendo generación del SVG con distintos valores de marca/nombre, incluidos nulos/vacíos.
- Frontend: actualizar los tests existentes que importan `productos.json` directamente (`CatalogoPage.test.jsx`, `HomePage.test.jsx`, `PagoPage.test.jsx`, etc.) para mockear los nuevos `services/*` en vez de leer el JSON; agregar tests propios de cada servicio (mock de `fetch`).

## E. Paquete de evidencia (entrega GA8-220501096-AA1-EV01)

Documento PDF con:
- Instrucciones para levantar los 3 servicios juntos (orden: `zapateria-backend` :8080 → `shoesstore-tienda-api` :8081 → frontend :5173).
- Capturas de la integración funcionando (catálogo con imágenes propias, login/registro, checkout creando un pedido real).
- Explicación de módulos, capas y patrones aplicados, siguiendo el checklist de "Elementos a tener en cuenta" de la guía de aprendizaje 8.
- Enlace al repositorio.

## Fuera de alcance

- Subida de imágenes reales por el usuario/admin (se descartó por ahora: el generador de placeholders server-side ya resuelve el riesgo legal sin necesitar fotos propias).
- Cambios al servicio de inventario (`zapateria-backend`): se consume tal cual existe hoy.
