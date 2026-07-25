# Backend de la tienda y vínculo con el inventario (GA7-220501096-AA5-EV03)

## Contexto

El ecosistema **Shoes Store** ya tiene dos piezas construidas en evidencias previas, cada una en su propio repositorio, y hasta ahora sin relación entre sí:

- **`shoes'sStore 2.0`** (repo `shoes-store`): el storefront público en React (AA4-EV03). Todo mock — login hardcodeado (`admin`/`shoes2026`), catálogo estático en `productos.json`, carrito en `localStorage`, checkout simulado.
- **`zapateria-backend` / `zapateria-frontend`** (repo `shoes-store-inventory-2.0`, evidencia AA3): sistema de gestión de inventario real, con Spring Boot + JPA + MySQL (`shoesstore`). CRUD completo de `Producto` (una fila = una variante talla+color con su stock), `Categoria` y `Proveedor`. Ya entregado y evidenciado (AA3-EV02, con capturas y video).
- **`auth-service`** (evidencias AA5-EV01/EV02): microservicio standalone de registro/login, en memoria, sin base de datos ni relación con los otros dos.

Esta evidencia (AA5-EV03, "Diseño y desarrollo de servicios web – proyecto") pide diseñar y codificar las APIs necesarias para el software del proyecto formativo. En vez de construir un cuarto sistema desconectado, se aprovecha para **ligar el ecosistema**: la tienda pública debe consumir al sistema de inventario real, en lugar de duplicar su propio catálogo aislado.

## Decisión de arquitectura

Se descartaron dos alternativas:

- Backend único fusionando ambos modelos de datos: obligaba a migrar el esquema y los datos de `zapateria-backend`, que ya está evidenciado y aprobado (AA3-EV02) — riesgo innecesario sobre trabajo ya entregado.
- Dos bases de datos compartiendo tablas por convención, sin llamada real entre servicios: no demuestra integración real, que es justamente el objetivo de la competencia de servicios web.

Se adopta: **dos backends independientes, donde el de la tienda consulta al de inventario por HTTP.** Es el patrón real de una tienda que se abastece de su propio ERP/sistema de inventario.

### Renombrado del ecosistema (paso previo, sobre el repo de inventario)

Para que la relación entre sistemas se lea en los nombres:

| Antes | Después |
|---|---|
| Carpeta `zapateria-backend` | `shoesstore-inventario-api` |
| Carpeta `zapateria-frontend` | `shoesstore-inventario-admin` |
| Paquete Java `com.zapateria` | `com.shoesstore.inventario` |
| Repo GitHub `shoes-store-inventory-2.0` | `shoesstore-inventario-api` (GitHub redirige la URL vieja automáticamente) |

Este rename se ejecuta como refactor mecánico (mover archivos, cambiar `package`, actualizar `pom.xml`, `nb-configuration.xml`, `nbactions.xml`) sin tocar el modelo de datos ni el comportamiento — se vuelve a probar el CRUD manualmente tras el cambio para confirmar que sigue funcionando, sin regrabar el video ya entregado en AA3-EV02.

### Proyecto nuevo: `shoesstore-tienda-api`

Repositorio y carpeta nuevos, hermanos de `shoes'sStore 2.0`. Spring Boot (Java 17, Maven), paquete `com.shoesstore.tienda`, base de datos MySQL propia `shoesstore_tienda` (separada de `shoesstore` — cada servicio es dueño de su propia base, se comunican solo por HTTP, nunca por SQL cruzado).

```
com.shoesstore.tienda
├── auth/        (registro, login, sesión)
├── productos/   (catálogo público — enriquecido, ligado a inventario)
├── pedidos/     (checkout, historial)
├── inventario/  (cliente HTTP hacia shoesstore-inventario-api)
└── common/      (manejador de errores global, filtro de token)
```

Corre en `localhost:8081` (el de inventario ya usa `8080`). CORS habilitado para `localhost:5173` (Vite del storefront).

## Modelo de datos (`shoesstore_tienda`)

- **usuarios**: id, nombre_usuario (único), contrasena_hash (BCrypt), nombre_completo, email, fecha_registro
- **sesiones**: token (PK, UUID), usuario_id (FK), fecha_creacion, fecha_expiracion
- **productos**: id, nombre, marca, precio, genero, proposito, subcategoria, colorway, novedad, outlet, imagen — los atributos descriptivos que el storefront necesita y que el inventario no modela
- **producto_tallas**: id, producto_id (FK), talla, **id_producto_inventario** (Long) — referencia a la fila SKU real en `shoesstore-inventario-api` (`producto.ID_PRODUCTO`). Este campo es el vínculo entre los dos sistemas: cada combinación producto+talla del catálogo público apunta a una variante específica del inventario.
- **pedidos**: id, usuario_id (FK), numero_orden, metodo_pago, banco (nullable), total_cop, envio_cop, estado, fecha
- **pedido_items**: id, pedido_id (FK), producto_id (FK), talla, cantidad, precio_unitario

## Integración con el inventario

`shoesstore-tienda-api` nunca guarda ni cachea el stock — lo consulta en vivo, para que nunca quede desactualizado:

- **Al ver disponibilidad de tallas** (`GET /api/productos/{id}`): por cada `producto_tallas`, se llama a `GET /api/productos/{idProductoInventario}` en `shoesstore-inventario-api` y se anota si `stock > 0`.
- **Al crear un pedido** (`POST /api/pedidos`): primero se recorren TODAS las líneas del carrito haciendo `GET /api/productos/{idProductoInventario}` para confirmar que cada una tiene stock suficiente. Si alguna no alcanza, el pedido se rechaza completo con `409 Conflict` sin haber llamado a ningún `PUT` (nada se descuenta). Solo si todas las líneas pasan la validación, se recorren de nuevo haciendo `PUT /api/productos/{idProductoInventario}` con el stock descontado, línea por línea. No es una transacción distribuida real (si el servidor cayera a mitad de los `PUT` quedaría un descuento parcial), pero para el alcance de esta evidencia es suficiente: valida-todo-antes-de-descontar-nada evita el caso común de vender de más.
- Estas llamadas se hacen con `RestClient` (cliente HTTP de Spring), configurando la URL base de `shoesstore-inventario-api` en `application.properties`.

Esto es lo que se demuestra en el video/Postman de esta evidencia: crear un pedido en la tienda y ver el stock bajar en el panel de inventario.

## Endpoints

**Auth** (`/api/auth`)
- `POST /registro` — crea usuario, devuelve token de sesión
- `POST /login` — valida credenciales, devuelve token de sesión
- `GET /perfil` — datos del usuario autenticado (requiere token)
- `POST /logout` — invalida el token

**Productos** (`/api/productos`) — reemplaza `productos.json` estático
- `GET /` — lista con filtros por query params (`genero`, `marca`, `proposito`, `precioMin/Max`)
- `GET /{id}` — detalle, con disponibilidad de tallas resuelta contra el inventario
- `POST /`, `PUT /{id}`, `DELETE /{id}` — administración del catálogo público (requiere token)

**Pedidos** (`/api/pedidos`) — reemplaza el mock de `PagoPage.jsx`
- `POST /` — crea el pedido, valida y descuenta stock contra `shoesstore-inventario-api` (requiere token)
- `GET /` — historial de pedidos del usuario autenticado
- `GET /{id}` — detalle de un pedido

## Seguridad y errores

Token opaco: al hacer login se genera un UUID guardado en `sesiones` junto al usuario. Un filtro intercepta `Authorization: Bearer <token>` en las rutas protegidas, lo busca en `sesiones` y responde `401` si no existe o expiró. Contraseñas con hash BCrypt (mejora sobre `auth-service`, que las guarda en texto plano). Un `@ControllerAdvice` centraliza las respuestas de error, mismo patrón que `ManejadorErrores.java` del `auth-service` actual.

## Semilla de datos

Para la demo se cargan ~10 productos representativos de `productos.json` (los ya usados por el storefront) tanto en `shoesstore_tienda` (tabla `productos`/`producto_tallas`) como en `shoesstore` (tabla `producto` del inventario, una fila por talla con su stock), con los `id_producto_inventario` cruzados correctamente entre ambas bases. Esto permite mostrar el vínculo funcionando de punta a punta sin tener que migrar el catálogo completo.

## Entrega de la evidencia

Postman collection con todos los endpoints de `shoesstore-tienda-api` (incluyendo el flujo de pedido que dispara la llamada a inventario), documento de pruebas con capturas, video de testing mostrando el descuento de stock cruzado, y archivo `ENDPOINTS.md`. El repositorio de `shoesstore-inventario-api` (renombrado) se entrega como referencia del sistema con el que se integra, aunque su evidencia formal ya fue AA3.
