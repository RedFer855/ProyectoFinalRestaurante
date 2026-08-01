package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.domain.ReglasMenu;
import com.example.proyectofinalrestaurante.domain.model.Categoria;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.ImagenPlatillo;
import com.example.proyectofinalrestaurante.domain.model.Platillo;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Tests de {@link ReglasMenu} — el espejo en el cliente de las reglas del servidor
 * (Plan Fase 2a, E7).
 */
public class ReglasMenuTest {

    private static Categoria categoria(int id, String descripcion, int cantidadPlatillos) {
        return new Categoria(id, id, descripcion, true, cantidadPlatillos, cantidadPlatillos,
                EstadoSync.SINCRONIZADO);
    }

    private static Platillo platillo(int id, String nombre) {
        return new Platillo(id, id, nombre, null, 35.0, 1, "Entradas", null, true,
                EstadoSync.SINCRONIZADO);
    }

    // ------------------------------------------------------------------ borrado

    @Test
    public void categoriaVacia_sePuedeBorrar() {
        assertTrue(ReglasMenu.puedeBorrarse(categoria(9, "Sin uso", 0)));
    }

    @Test
    public void categoriaConPlatillos_noSePuedeBorrar() {
        assertFalse(ReglasMenu.puedeBorrarse(categoria(1, "Entradas", 3)));
    }

    @Test
    public void categoriaSoloConPlatillosDesactivados_tampocoSePuedeBorrar() {
        // El trigger cuenta filas, no activos: una categoría con un platillo desactivado
        // sigue teniendo un platillo.
        Categoria conDesactivados = new Categoria(1, 1, "Entradas", true, 2, 0,
                EstadoSync.SINCRONIZADO);
        assertFalse(ReglasMenu.puedeBorrarse(conDesactivados));
    }

    @Test
    public void categoriaNula_noSePuedeBorrar() {
        assertFalse(ReglasMenu.puedeBorrarse(null));
    }

    // ------------------------------------------------------------------ unicidad

    @Test
    public void normalizarNombre_bajaYRecortaIgualQueElIndiceUnico() {
        assertEquals("baleada sencilla", ReglasMenu.normalizarNombre("  Baleada SENCILLA "));
        assertEquals("", ReglasMenu.normalizarNombre(null));
    }

    @Test
    public void nombreDuplicadoConOtrasMayusculasYEspacios_seDetecta() {
        List<Platillo> platillos = Arrays.asList(platillo(1, "Baleada sencilla"), platillo(2, "Tres leches"));

        assertTrue(ReglasMenu.existeOtroPlatilloLlamado(platillos, "  baleada SENCILLA ", 0));
    }

    @Test
    public void elPropioPlatilloNoCuentaComoDuplicado() {
        List<Platillo> platillos = Arrays.asList(platillo(1, "Baleada sencilla"));

        // Editar el precio sin tocar el nombre no debe reportarse como duplicado.
        assertFalse(ReglasMenu.existeOtroPlatilloLlamado(platillos, "Baleada sencilla", 1));
    }

    @Test
    public void categoriaDuplicada_seDetectaIgnorandoMayusculas() {
        List<Categoria> categorias = Arrays.asList(categoria(1, "Entradas", 3), categoria(2, "Bebidas", 1));

        assertTrue(ReglasMenu.existeOtraCategoriaLlamada(categorias, "entradas", 0));
        assertFalse(ReglasMenu.existeOtraCategoriaLlamada(categorias, "Entradas", 1));
        assertFalse(ReglasMenu.existeOtraCategoriaLlamada(categorias, "Postres", 0));
    }

    @Test
    public void listaNula_noRevienta() {
        assertFalse(ReglasMenu.existeOtroPlatilloLlamado(null, "Baleada", 0));
        assertFalse(ReglasMenu.existeOtraCategoriaLlamada(null, "Entradas", 0));
    }

    // ------------------------------------------------------------------ imágenes

    @Test
    public void imagenChicaYDeTipoPermitido_puedeSubirse() {
        assertTrue(ReglasMenu.puedeSubirse(
                new ImagenPlatillo(new byte[]{1, 2, 3}, ImagenPlatillo.MIME_JPEG)));
    }

    @Test
    public void imagenMasGrandeQueElLimiteDelBucket_noPuedeSubirse() {
        byte[] pasada = new byte[ReglasMenu.TAMANIO_MAXIMO_IMAGEN_BYTES + 1];
        assertFalse(ReglasMenu.cabeEnElBucket(new ImagenPlatillo(pasada, ImagenPlatillo.MIME_JPEG)));
    }

    @Test
    public void imagenExactamenteEnElLimite_siPuedeSubirse() {
        byte[] justa = new byte[ReglasMenu.TAMANIO_MAXIMO_IMAGEN_BYTES];
        assertTrue(ReglasMenu.cabeEnElBucket(new ImagenPlatillo(justa, ImagenPlatillo.MIME_JPEG)));
    }

    @Test
    public void imagenVacia_noPuedeSubirse() {
        assertFalse(ReglasMenu.cabeEnElBucket(new ImagenPlatillo(new byte[0], ImagenPlatillo.MIME_JPEG)));
        assertFalse(ReglasMenu.cabeEnElBucket(null));
    }

    @Test
    public void tipoNoPermitido_noPuedeSubirse() {
        // El bucket solo acepta jpeg, png y webp: un GIF falla con 400 del servidor.
        assertFalse(ReglasMenu.tipoDeImagenPermitido("image/gif"));
        assertFalse(ReglasMenu.puedeSubirse(new ImagenPlatillo(new byte[]{1}, "image/gif")));
        assertTrue(ReglasMenu.tipoDeImagenPermitido(ImagenPlatillo.MIME_PNG));
        assertTrue(ReglasMenu.tipoDeImagenPermitido(ImagenPlatillo.MIME_WEBP));
    }

    @Test
    public void extensionSaleDelMimeYNoDelArchivoElegido() {
        assertEquals("jpg", new ImagenPlatillo(new byte[]{1}, ImagenPlatillo.MIME_JPEG).extensionDeArchivo());
        assertEquals("png", new ImagenPlatillo(new byte[]{1}, ImagenPlatillo.MIME_PNG).extensionDeArchivo());
        assertEquals("webp", new ImagenPlatillo(new byte[]{1}, ImagenPlatillo.MIME_WEBP).extensionDeArchivo());
    }

    @Test
    public void losBytesSeCopian_laImagenEsInmutable() {
        byte[] originales = {1, 2, 3};
        ImagenPlatillo imagen = new ImagenPlatillo(originales, ImagenPlatillo.MIME_JPEG);

        originales[0] = 99;

        assertEquals(1, imagen.getBytes()[0]);
    }
}
