-- Semilla de datos que liga el catálogo público (shoesstore_tienda) con el
-- inventario real (shoesstore, shoesstore-inventario-api).
-- No se ejecuta automáticamente: documenta los pasos reales usados para la evidencia.

-- =====================================================================
-- PASO 1: filas de inventario (shoesstore-inventario-api, BD `shoesstore`)
-- Se crearon vía POST http://localhost:8080/api/productos (una fila = una
-- variante talla+color con su propio stock). IDs generados: 4-13.
-- =====================================================================
-- categoria=1 (Tenis Deportivos), proveedor=1 (Nike Colombia S.A.)
-- id 4:  Air Force 1 07, talla 9,  stock 8
-- id 5:  Air Force 1 07, talla 10, stock 0
-- id 6:  Dunk Low Retro, talla 9,  stock 5
-- id 7:  Dunk Low Retro, talla 10, stock 0
-- categoria=1, proveedor=2 (Adidas)
-- id 8:  Superstar, talla 9,  stock 6
-- id 9:  Superstar, talla 10, stock 0
-- categoria=1, proveedor=3 (Puma)
-- id 10: Suede Classic, talla 9,  stock 4
-- id 11: Suede Classic, talla 10, stock 0
-- categoria=1, proveedor=2 (Adidas)
-- id 12: Gazelle, talla 9,  stock 3
-- id 13: Gazelle, talla 10, stock 0

-- =====================================================================
-- PASO 2: productos del catálogo público (shoesstore-tienda-api, BD
-- `shoesstore_tienda`). Se crearon vía POST http://localhost:8081/api/productos
-- (requiere token de un usuario registrado). IDs generados: 1-5.
-- =====================================================================
-- id 1: Air Force 1 07 (Nike, $115, hombre, lifestyle, originals, White/White)
-- id 2: Dunk Low Retro (Nike, $110, hombre, lifestyle, originals, White/Black)
-- id 3: Superstar (Adidas, $100, hombre, lifestyle, originals, Cloud White/Core Black)
-- id 4: Suede Classic (Puma, $70, unisex, casual, classics, Black/White)
-- id 5: Gazelle (Adidas, $95, hombre, lifestyle, originals, Core Black, novedad=true)

-- =====================================================================
-- PASO 3: vínculo producto_tallas <-> inventario. Insertado directamente
-- por SQL (no hay endpoint REST para esta tabla interna).
-- =====================================================================
INSERT INTO producto_tallas (producto_id, talla, id_producto_inventario) VALUES
  (1, 9.0, 4),
  (1, 10.0, 5),
  (2, 9.0, 6),
  (2, 10.0, 7),
  (3, 9.0, 8),
  (3, 10.0, 9),
  (4, 9.0, 10),
  (4, 10.0, 11),
  (5, 9.0, 12),
  (5, 10.0, 13);

-- =====================================================================
-- Verificación (Paso 6 del plan): GET /api/productos/{id} contra
-- shoesstore-tienda-api resolvió correctamente disponible=true para la
-- talla con stock y disponible=false para la talla sin stock, en los 5
-- productos. Confirmado también el flujo completo de punta a punta:
--   - POST /api/pedidos con precioUnitario manipulado en el body (1) fue
--     ignorado; el pedido se creó con el precio real del producto (115).
--   - El stock de shoesstore-inventario-api bajó de 8 a 7 tras el pedido.
--   - GET /api/pedidos/{id} de otro usuario devolvió 401 (verificación IDOR).
-- =====================================================================
