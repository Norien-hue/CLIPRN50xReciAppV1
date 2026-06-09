-- ═══════════════════════════════════════════════════════════════
-- regenerar_bd.sql
-- Regenera la base de datos reciInventario_db desde cero
-- (esquema + datos de los productos que usa el sistema actual)
--
-- Uso en el servidor:
--   sudo mysql < regenerar_bd.sql
-- ═══════════════════════════════════════════════════════════════

DROP DATABASE IF EXISTS `reciInventario_db`;
CREATE DATABASE `reciInventario_db`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `reciInventario_db`;

-- ═══════════════════════════════════════════════════════════════
-- 1. TABLA Usuarios
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE `Usuarios` (
  `Id_Usuario` INT NOT NULL AUTO_INCREMENT,
  `Nombre` VARCHAR(50) NOT NULL,
  `Hash_Contraseña` VARCHAR(100) NOT NULL,
  `Permisos` VARCHAR(15) DEFAULT 'cliente',
  `Emisiones_Reducidas` FLOAT DEFAULT 0,
  `TAP` INT DEFAULT NULL,
  PRIMARY KEY (`Id_Usuario`),
  UNIQUE KEY `uk_nombre` (`Nombre`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Usuarios del sistema (los hashes son bcrypt de ejemplo,
-- en producción se generan con BCryptPasswordEncoder)
INSERT INTO `Usuarios` (`Nombre`, `Hash_Contraseña`, `Permisos`, `Emisiones_Reducidas`, `TAP`) VALUES
  ('adminb',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'administrador', 0, NULL),
  ('terminal',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'administrador', 0, NULL),
  ('profesor',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'administrador', 0, 123456),
  ('alumno1',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cliente',      0, 224969),
  ('alumno2',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'cliente',      0, 824330);

-- Nota: los hashes de arriba son placeholders.
-- En producción se generan con:
--   BCryptPasswordEncoder().encode("clase1234")
-- Contraseñas por defecto de los usuarios creados:
--   adminb   → clase1234
--   terminal → 1234
--   profesor → 1234
--   alumno1  → 1234
--   alumno2  → 1234

-- ═══════════════════════════════════════════════════════════════
-- 2. TABLA Productos
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE `Productos` (
  `Tipo` VARCHAR(10) NOT NULL,
  `Numero_barras` BIGINT NOT NULL,
  `Nombre` VARCHAR(50) DEFAULT NULL,
  `Emisiones_Reducibles` FLOAT DEFAULT NULL,
  `Material` VARCHAR(15) DEFAULT NULL,
  `Imagen` LONGTEXT DEFAULT NULL,
  PRIMARY KEY (`Tipo`, `Numero_barras`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Productos actuales del sistema (usados por la app + CLIP)
INSERT INTO `Productos` (`Tipo`, `Numero_barras`, `Nombre`, `Emisiones_Reducibles`, `Material`, `Imagen`) VALUES
  ('EAN13', 8410031961241, 'Botella agua 1L Bezoya',       1.2, 'PET',      NULL),
  ('EAN13', 8410031961258, 'Botella agua 250ml Bezoya',    0.6, 'PET',      NULL),
  ('EAN13', 8410100222224, 'Brick leche 1L Pascual',       1.7, 'Brick',    NULL),
  ('EAN13', 8410314021012, 'Botella cerveza 33cl Mahou',   1.5, 'Vidrio',   NULL),
  ('EAN13', 8410376101246, 'Lata Coca-Cola 33cl',          0.8, 'Aluminio', NULL),
  ('EAN13', 8410654012345, 'Bote champu 400ml Pantene',    2.8, 'PET',      NULL),
  ('EAN13', 8410596004108, 'Lata Fanta Naranja 33cl',      0.8, 'Aluminio', NULL),
  ('EAN13', 8410123456789, 'Caja de carton',               0.5, 'Carton',   NULL);

-- ═══════════════════════════════════════════════════════════════
-- 3. TABLA Fotos_Extra
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE `Fotos_Extra` (
  `Id` BIGINT NOT NULL AUTO_INCREMENT,
  `Producto_Tipo` VARCHAR(10) NOT NULL,
  `Producto_Numero_Barras` BIGINT NOT NULL,
  `Orden` INT NOT NULL DEFAULT 0,
  `Imagen` LONGTEXT DEFAULT NULL,
  PRIMARY KEY (`Id`),
  CONSTRAINT `fk_foto_extra_producto`
    FOREIGN KEY (`Producto_Tipo`, `Producto_Numero_Barras`)
    REFERENCES `Productos` (`Tipo`, `Numero_barras`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ═══════════════════════════════════════════════════════════════
-- 4. TABLA Recicla (historial de reciclajes)
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE `Recicla` (
  `Id_Usuario` INT NOT NULL,
  `Tipo` VARCHAR(10) NOT NULL,
  `Numero_barras` BIGINT NOT NULL,
  `Fecha` DATE NOT NULL,
  `Hora` TIME NOT NULL,
  PRIMARY KEY (`Id_Usuario`, `Tipo`, `Numero_barras`, `Fecha`, `Hora`),
  CONSTRAINT `fk_recicla_usuario`
    FOREIGN KEY (`Id_Usuario`) REFERENCES `Usuarios` (`Id_Usuario`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_recicla_producto`
    FOREIGN KEY (`Tipo`, `Numero_barras`) REFERENCES `Productos` (`Tipo`, `Numero_barras`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
