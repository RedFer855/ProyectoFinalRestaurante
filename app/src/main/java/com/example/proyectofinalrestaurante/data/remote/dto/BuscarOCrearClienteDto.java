package com.example.proyectofinalrestaurante.data.remote.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * Cuerpo de {@code POST rest/v1/rpc/buscar_o_crear_cliente}. Argumentos del RPC (Plan Fase
 * 2d, §2.4), con los nombres exactos: {@code p_nombre}, {@code p_apellido},
 * {@code p_identidad}, {@code p_telefono}.
 */
public final class BuscarOCrearClienteDto {

    @SerializedName("p_nombre")
    private final String pNombre;

    @SerializedName("p_apellido")
    private final String pApellido;

    @SerializedName("p_identidad")
    @Nullable
    private final String pIdentidad;

    @SerializedName("p_telefono")
    @Nullable
    private final String pTelefono;

    public BuscarOCrearClienteDto(String pNombre, String pApellido,
                                  @Nullable String pIdentidad, @Nullable String pTelefono) {
        this.pNombre = pNombre;
        this.pApellido = pApellido;
        this.pIdentidad = pIdentidad;
        this.pTelefono = pTelefono;
    }
}
