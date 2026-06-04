package com.reciapp.api.dto;

import com.reciapp.api.entity.FotoExtra;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FotoExtraDto {
    private Long id;
    private String productoTipo;
    private String productoNumeroBarras;
    private Integer orden;
    private String imagen;

    public static FotoExtraDto from(FotoExtra fe) {
        return new FotoExtraDto(
            fe.getId(),
            fe.getProductoTipo(),
            String.valueOf(fe.getProductoNumeroBarras()),
            fe.getOrden(),
            fe.getImagen()
        );
    }
}
