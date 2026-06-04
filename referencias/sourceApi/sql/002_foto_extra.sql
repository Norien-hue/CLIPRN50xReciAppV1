-- Migracion: tabla de fotos extra para productos
-- Permite multiples imagenes por producto para mejorar los embeddings de CLIP
-- Ejecutar en la base de datos reciInventario_db

USE reciInventario_db;

CREATE TABLE IF NOT EXISTS `Fotos_Extra` (
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
