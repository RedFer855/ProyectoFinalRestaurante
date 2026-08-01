package com.example.proyectofinalrestaurante.domain.repository;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Categoria;
import com.example.proyectofinalrestaurante.domain.model.ImagenPlatillo;
import com.example.proyectofinalrestaurante.domain.model.NuevoPlatillo;
import com.example.proyectofinalrestaurante.domain.model.Platillo;

import java.util.List;

/**
 * Contrato del módulo Menú (Domain Layer). {@code data} lo implementa; es la única cara
 * que {@code ui} ve del menú.
 *
 * <p>Todo va directo por PostgREST y lo autoriza la RLS: a diferencia de Empleados, acá
 * no hace falta ninguna Edge Function porque no se crean cuentas de acceso.</p>
 *
 * <p>Las fotos viven en el bucket {@code platillos} de Storage, que es un sistema
 * <b>distinto</b> de la base. Las operaciones que tocan los dos (crear con foto, cambiar
 * la foto, quitarla) se orquestan del lado de {@code data} para que {@code ui} no tenga
 * que saber que hay dos sistemas que pueden desincronizarse.</p>
 */
public interface MenuRepository {

    Result<List<Platillo>> listarPlatillos();

    Result<List<Categoria>> listarCategorias();

    /**
     * Crea el platillo y, si viene imagen, la sube primero.
     *
     * <p>Si el insert falla después de haber subido la foto, la implementación borra el
     * archivo recién subido: sin esa compensación, cada error dejaría basura permanente
     * en el bucket.</p>
     */
    Result<Platillo> crearPlatillo(NuevoPlatillo nuevo, @Nullable ImagenPlatillo imagen);

    /**
     * Guarda los datos del platillo y, si viene {@code imagenNueva}, la reemplaza.
     *
     * <p>La foto nueva se sube en una ruta nueva y la vieja se borra al final. Así, si algo
     * se cae en el medio, sobra un archivo (barato, invisible) en vez de faltar la foto de
     * un platillo que sí existe (visible para el usuario).</p>
     */
    Result<Void> actualizarPlatillo(Platillo platillo, @Nullable ImagenPlatillo imagenNueva);

    /** Deja el platillo sin foto: limpia la ruta en la fila y borra el archivo. */
    Result<Void> quitarImagen(Platillo platillo);

    /** Activa o desactiva. Un platillo nunca se borra: rompería el historial de pedidos. */
    Result<Void> cambiarEstadoPlatillo(int idPlatillo, boolean activo);

    Result<Categoria> crearCategoria(String descripcion);

    Result<Void> renombrarCategoria(int idCategoria, String descripcion);

    Result<Void> cambiarEstadoCategoria(int idCategoria, boolean activo);

    /** Solo funciona si la categoría no tiene platillos; si los tiene, el servidor la rechaza. */
    Result<Void> borrarCategoria(int idCategoria);
}
