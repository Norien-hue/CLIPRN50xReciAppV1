package com.reciapp.api.repository;

import com.reciapp.api.entity.FotoExtra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FotoExtraRepository extends JpaRepository<FotoExtra, Long> {

    List<FotoExtra> findByProductoTipoAndProductoNumeroBarrasOrderByOrdenAsc(
        String productoTipo, Long productoNumeroBarras);

    void deleteByProductoTipoAndProductoNumeroBarras(
        String productoTipo, Long productoNumeroBarras);
}
