package com.reciapp.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "Fotos_Extra")
@Getter
@Setter
public class FotoExtra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "Producto_Tipo", length = 10)
    private String productoTipo;

    @Column(name = "Producto_Numero_Barras")
    private Long productoNumeroBarras;

    @Column(name = "Orden")
    private Integer orden;

    @Lob
    @Column(name = "Imagen", columnDefinition = "LONGTEXT")
    private String imagen;
}
