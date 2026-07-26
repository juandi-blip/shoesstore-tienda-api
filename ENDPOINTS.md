# Endpoints — shoesstore-tienda-api (puerto 8081)

Requiere `shoesstore-inventario-api` corriendo en el puerto 8080 para resolver
disponibilidad de tallas y para crear pedidos (valida y descuenta stock ahí).

## Auth (`/api/auth`)

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| POST | /api/auth/registro | público | Crea un usuario, devuelve token de sesión |
| POST | /api/auth/login | público | Valida credenciales, devuelve token de sesión |
| GET | /api/auth/perfil | token | Datos del usuario autenticado |
| POST | /api/auth/logout | token | Invalida el token actual |

## Productos (`/api/productos`)

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| GET | /api/productos?genero=&marca=&proposito= | público | Lista el catálogo, con filtros opcionales |
| GET | /api/productos/{id} | público | Detalle con disponibilidad de tallas resuelta contra el inventario |
| POST | /api/productos | token | Crea un producto del catálogo |
| PUT | /api/productos/{id} | token | Actualiza un producto |
| DELETE | /api/productos/{id} | token | Elimina un producto |

## Pedidos (`/api/pedidos`)

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| POST | /api/pedidos | token | Crea un pedido; valida y descuenta stock en shoesstore-inventario-api |
| GET | /api/pedidos | token | Historial de pedidos del usuario autenticado |
| GET | /api/pedidos/{id} | token | Detalle de un pedido propio (401 si no es tuyo) |

## Notas de seguridad verificadas en vivo

- El precio de cada línea de pedido se calcula siempre desde el producto real
  en el servidor — el `precioUnitario` que mande el cliente en el body se
  ignora.
- `GET /api/pedidos/{id}` rechaza con 401 si el pedido no pertenece al usuario
  del token.
- Las respuestas de pedidos nunca incluyen el hash de la contraseña del
  usuario ni generan referencias circulares en el JSON.
