package com.example.proyectofinalrestaurante.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.data.FakeCall;
import com.example.proyectofinalrestaurante.data.remote.SupabaseMenuApi;
import com.example.proyectofinalrestaurante.data.remote.SupabaseStorageApi;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarCategoriaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarPlatilloDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CategoriaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearCategoriaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearPlatilloDto;
import com.example.proyectofinalrestaurante.data.remote.dto.PlatilloDto;
import com.example.proyectofinalrestaurante.domain.model.ImagenPlatillo;
import com.google.gson.Gson;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.Buffer;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Tests de {@link MenuRemoto} (Plan Fase 2b, E5/E6): la cara de red que usa el
 * sincronizador para drenar el outbox y bajar el delta. Es el sucesor directo de
 * {@code SupabaseMenuRepositoryTest} de 2a: conserva la cobertura de la compensación
 * (si la foto se subió pero el paso siguiente falla, el archivo se borra) y adapta las
 * lecturas al contrato nuevo, que devuelve DTOs y no mapea a dominio — ese mapeo vive en
 * los mappers y se cubre en {@code SincronizadorMenuTest}.
 */
public class MenuRemotoTest {

    private static final String JSON_PLATILLO =
            "{\"id_platillo\":1,\"nombre\":\"Baleada sencilla\",\"descripcion\":\"Con frijoles\","
                    + "\"precio\":35.00,\"id_categoria\":1,\"nombre_categoria\":\"Entradas\","
                    + "\"ruta_imagen\":null,\"id_estado\":1}";

    private final Gson gson = new Gson();

    private static MenuRemoto remotoCon(FakeMenuApi api, FakeStorageApi storage) {
        return new MenuRemoto(api, storage, () -> "token-válido");
    }

    private static MenuRemoto remotoSinSesion(FakeMenuApi api) {
        return new MenuRemoto(api, new FakeStorageApi(), () -> null);
    }

    private static ImagenPlatillo imagen() {
        return new ImagenPlatillo(new byte[]{1, 2, 3}, ImagenPlatillo.MIME_JPEG);
    }

    private static Response<Void> errorConMensaje(int codigo, String mensaje) {
        return Response.error(codigo, ResponseBody.create(MediaType.get("application/json"),
                "{\"message\":\"" + mensaje + "\"}"));
    }

    // ------------------------------------------------------------------ lecturas

    @Test
    public void listarPlatillos_sinSesion_devuelveFalloSinLlamarALaRed() {
        FakeMenuApi api = new FakeMenuApi();

        ResultadoRed<List<PlatilloDto>> resultado = remotoSinSesion(api).listarPlatillos();

        assertFalse(resultado.isExitoso());
        assertEquals("Tu sesión venció. Volvé a iniciar sesión.", resultado.getMensaje());
        assertEquals(401, resultado.getCodigoHttp());
        assertEquals(0, api.llamadasListarPlatillos);
    }

    @Test
    public void listarPlatillos_exitoso_devuelveLosDtoTalCual() {
        FakeMenuApi api = new FakeMenuApi();
        PlatilloDto[] dtos = gson.fromJson("[" + JSON_PLATILLO + "]", PlatilloDto[].class);
        api.respuestaListarPlatillos = FakeCall.deRespuesta(Response.success(List.of(dtos)));

        ResultadoRed<List<PlatilloDto>> resultado =
                remotoCon(api, new FakeStorageApi()).listarPlatillos();

        assertTrue(resultado.isExitoso());
        assertEquals(1, resultado.getValor().size());
        // El remoto no mapea: entrega lo que vino. El mapeo a dominio lo hace el mapper
        // cuando el sincronizador baja el delta (SincronizadorMenuTest).
        assertEquals("Baleada sencilla", resultado.getValor().get(0).getNombre());
        assertEquals(1, resultado.getValor().get(0).getIdCategoria());
        assertEquals(1, resultado.getValor().get(0).getIdEstado());
    }

    @Test
    public void listarPlatillos_platilloInactivo_conservaElIdEstado() {
        FakeMenuApi api = new FakeMenuApi();
        PlatilloDto[] dtos = gson.fromJson(
                "[{\"id_platillo\":2,\"nombre\":\"Sopa\",\"precio\":90.0,\"id_categoria\":2,\"id_estado\":2}]",
                PlatilloDto[].class);
        api.respuestaListarPlatillos = FakeCall.deRespuesta(Response.success(List.of(dtos)));

        ResultadoRed<List<PlatilloDto>> resultado =
                remotoCon(api, new FakeStorageApi()).listarPlatillos();

        assertTrue(resultado.isExitoso());
        assertEquals(2, resultado.getValor().get(0).getIdEstado());
    }

    @Test
    public void listarPlatillos_sinConexion_devuelveFalloDeRed() {
        FakeMenuApi api = new FakeMenuApi();
        api.respuestaListarPlatillos = FakeCall.deFallo(new IOException("timeout"));

        ResultadoRed<List<PlatilloDto>> resultado =
                remotoCon(api, new FakeStorageApi()).listarPlatillos();

        assertFalse(resultado.isExitoso());
        assertEquals("Sin conexión al servidor. Intentá de nuevo.", resultado.getMensaje());
    }

    @Test
    public void listarCategorias_exitoso_traeLasFilas() {
        FakeMenuApi api = new FakeMenuApi();
        CategoriaDto[] dtos = gson.fromJson(
                "[{\"id_categoria\":1,\"descripcion\":\"Entradas\",\"id_estado\":1,"
                        + "\"cantidad_platillos\":3,\"cantidad_platillos_activos\":2}]",
                CategoriaDto[].class);
        api.respuestaListarCategorias = FakeCall.deRespuesta(Response.success(List.of(dtos)));

        ResultadoRed<List<CategoriaDto>> resultado =
                remotoCon(api, new FakeStorageApi()).listarCategorias();

        assertTrue(resultado.isExitoso());
        assertEquals(3, resultado.getValor().get(0).getCantidadPlatillos());
        assertEquals(2, resultado.getValor().get(0).getCantidadPlatillosActivos());
    }

    @Test
    public void listarPlatillosDesde_sinMarca_noPoneFiltro() {
        FakeMenuApi api = new FakeMenuApi();
        api.respuestaListarPlatillosDesde = FakeCall.deRespuesta(Response.success(List.of()));

        ResultadoRed<List<PlatilloDto>> resultado =
                remotoCon(api, new FakeStorageApi()).listarPlatillosDesde(null, 0);

        assertTrue(resultado.isExitoso());
        // Sin marca, la primera bajada pide la tabla entera: sin query actualizado_en.
        assertEquals(null, api.ultimoFiltroDesdePlatillos);
    }

    @Test
    public void listarPlatillosDesde_conMarca_armaElFiltroMayorQue() {
        FakeMenuApi api = new FakeMenuApi();
        api.respuestaListarPlatillosDesde = FakeCall.deRespuesta(Response.success(List.of()));

        remotoCon(api, new FakeStorageApi())
                .listarPlatillosDesde("2026-01-01T10:00:00+00:00", 0);

        assertEquals("gt.2026-01-01T10:00:00+00:00", api.ultimoFiltroDesdePlatillos);
    }

    @Test
    public void listarPlatillosDesde_propagaElOffsetYOrdenaConDesempate() {
        FakeMenuApi api = new FakeMenuApi();
        api.respuestaListarPlatillosDesde = FakeCall.deRespuesta(Response.success(List.of()));

        remotoCon(api, new FakeStorageApi()).listarPlatillosDesde(null, 50);

        assertEquals(50, api.ultimoOffsetPlatillos);
        // Sin el id como desempate, dos pedidos con el mismo offset pueden devolver filas
        // distintas y alguna se pierde entre páginas.
        assertEquals("actualizado_en.asc,id_platillo.asc", api.ultimoOrdenPlatillos);
    }

    // ------------------------------------------------------------------ crear

    @Test
    public void crearPlatillo_sinImagen_devuelveLaFilaCreada() {
        FakeMenuApi api = new FakeMenuApi();
        FakeStorageApi storage = new FakeStorageApi();
        PlatilloDto[] creado = gson.fromJson("[" + JSON_PLATILLO + "]", PlatilloDto[].class);
        api.respuestaCrearPlatillo = FakeCall.deRespuesta(Response.success(List.of(creado)));

        ResultadoRed<PlatilloDto> resultado = remotoCon(api, storage)
                .crearPlatillo("Baleada sencilla", "Con frijoles", 35.0, 1, null);

        assertTrue(resultado.isExitoso());
        assertEquals("Baleada sencilla", resultado.getValor().getNombre());
        assertTrue(storage.rutasSubidas.isEmpty());
    }

    @Test
    public void crearPlatillo_conImagen_subePrimeroYGuardaLaRuta() {
        FakeMenuApi api = new FakeMenuApi();
        FakeStorageApi storage = new FakeStorageApi();
        PlatilloDto[] creado = gson.fromJson("[" + JSON_PLATILLO + "]", PlatilloDto[].class);
        api.respuestaCrearPlatillo = FakeCall.deRespuesta(Response.success(List.of(creado)));

        ResultadoRed<PlatilloDto> resultado = remotoCon(api, storage)
                .crearPlatillo("Baleada sencilla", "Con frijoles", 35.0, 1, imagen());

        assertTrue(resultado.isExitoso());
        assertEquals(1, storage.rutasSubidas.size());
        assertTrue(storage.rutasSubidas.get(0).endsWith(".jpg"));
        assertEquals(ImagenPlatillo.MIME_JPEG, storage.tipoSubido);
        // El POST lleva la ruta del archivo recién subido: sin eso el servidor no
        // tendría cómo saber qué foto va con la fila.
        String cuerpo = gson.toJson(api.ultimoCrearPlatillo);
        assertTrue(cuerpo.contains(storage.rutasSubidas.get(0)));
        assertTrue(storage.rutasBorradas.isEmpty());
    }

    @Test
    public void crearPlatillo_siFallaLaSubida_noInsertaNada() {
        FakeMenuApi api = new FakeMenuApi();
        FakeStorageApi storage = new FakeStorageApi();
        storage.respuestaSubir = FakeCall.deRespuesta(errorConMensaje(413, "Payload too large"));

        ResultadoRed<PlatilloDto> resultado = remotoCon(api, storage)
                .crearPlatillo("Baleada sencilla", "Con frijoles", 35.0, 1, imagen());

        assertFalse(resultado.isExitoso());
        assertEquals("No se pudo subir la foto. Intentá de nuevo.", resultado.getMensaje());
        assertEquals(0, api.llamadasCrearPlatillo);
    }

    @Test
    public void crearPlatillo_imagenSubidaPeroInsertRechazado_borraElObjetoDelBucket() {
        FakeMenuApi api = new FakeMenuApi();
        FakeStorageApi storage = new FakeStorageApi();
        api.respuestaCrearPlatillo = FakeCall.deRespuesta(Response.error(409,
                ResponseBody.create(MediaType.get("application/json"),
                        "{\"message\":\"duplicate key value violates unique constraint\\\"uq_platillo_nombre\\\"\"}")));

        ResultadoRed<PlatilloDto> resultado = remotoCon(api, storage)
                .crearPlatillo("Baleada sencilla", "Con frijoles", 35.0, 1, imagen());

        assertFalse(resultado.isExitoso());
        // Lo que importa: el archivo que se subió es exactamente el que se borra.
        assertEquals(1, storage.rutasSubidas.size());
        assertEquals(1, storage.rutasBorradas.size());
        assertEquals(storage.rutasSubidas.get(0), storage.rutasBorradas.get(0));
    }

    @Test
    public void crearPlatillo_imagenInvalida_niSubeNiInserta() {
        FakeMenuApi api = new FakeMenuApi();
        FakeStorageApi storage = new FakeStorageApi();
        ImagenPlatillo gif = new ImagenPlatillo(new byte[]{1, 2, 3}, "image/gif");

        ResultadoRed<PlatilloDto> resultado = remotoCon(api, storage)
                .crearPlatillo("Baleada sencilla", "Con frijoles", 35.0, 1, gif);

        assertFalse(resultado.isExitoso());
        assertTrue(storage.rutasSubidas.isEmpty());
        assertEquals(0, api.llamadasCrearPlatillo);
    }

    @Test
    public void crearPlatillo_errorDelServidor_devuelveElMensajeDePostgrest() {
        FakeMenuApi api = new FakeMenuApi();
        api.respuestaCrearPlatillo = FakeCall.deRespuesta(Response.error(400,
                ResponseBody.create(MediaType.get("application/json"),
                        "{\"message\":\"new row violates check constraint \\\"ck_platillo_precio_positivo\\\"\"}")));

        ResultadoRed<PlatilloDto> resultado = remotoCon(api, new FakeStorageApi())
                .crearPlatillo("Baleada sencilla", "Con frijoles", -5.0, 1, null);

        assertFalse(resultado.isExitoso());
        assertTrue(resultado.getMensaje().contains("ck_platillo_precio_positivo"));
    }

    // ------------------------------------------------------------------ actualizar

    @Test
    public void actualizarPlatillo_conFotoNueva_borraLaViejaDespuesDeGuardar() {
        FakeMenuApi api = new FakeMenuApi();
        FakeStorageApi storage = new FakeStorageApi();

        ResultadoRed<String> resultado = remotoCon(api, storage)
                .actualizarPlatillo(1, "Baleada", "Con frijoles", 35.0, 1, true, imagen(),
                        "vieja.jpg");

        assertTrue(resultado.isExitoso());
        assertEquals(1, storage.rutasSubidas.size());
        assertEquals(1, storage.rutasBorradas.size());
        assertEquals("vieja.jpg", storage.rutasBorradas.get(0));
        // La ruta nueva nunca reusa la vieja: si no, Glide seguiría sirviendo la foto vieja.
        assertFalse(storage.rutasSubidas.get(0).equals("vieja.jpg"));
        // Y el sincronizador la guarda en la fila local para mostrarla sin otra bajada.
        assertEquals(storage.rutasSubidas.get(0), resultado.getValor());
    }

    @Test
    public void actualizarPlatillo_siFallaElPatch_borraLaFotoNuevaYNoLaVieja() {
        FakeMenuApi api = new FakeMenuApi();
        FakeStorageApi storage = new FakeStorageApi();
        api.respuestaActualizarPlatillo = FakeCall.deRespuesta(
                errorConMensaje(403, "No autorizado"));

        ResultadoRed<String> resultado = remotoCon(api, storage)
                .actualizarPlatillo(1, "Baleada", null, 35.0, 1, true, imagen(), "vieja.jpg");

        assertFalse(resultado.isExitoso());
        assertEquals(1, storage.rutasBorradas.size());
        assertEquals(storage.rutasSubidas.get(0), storage.rutasBorradas.get(0));
        // La vieja no se toca: la fila sigue apuntando a ella.
        assertFalse(storage.rutasBorradas.contains("vieja.jpg"));
    }

    @Test
    public void actualizarPlatillo_sinImagenNiRutaVieja_noTocaElBucket() {
        FakeMenuApi api = new FakeMenuApi();
        FakeStorageApi storage = new FakeStorageApi();

        ResultadoRed<String> resultado = remotoCon(api, storage)
                .actualizarPlatillo(1, "Baleada", "Con frijoles", 35.0, 1, true, null, null);

        assertTrue(resultado.isExitoso());
        assertEquals(null, resultado.getValor());
        assertTrue(storage.rutasSubidas.isEmpty());
        assertTrue(storage.rutasBorradas.isEmpty());
    }

    @Test
    public void quitarImagen_mandaElNullExplicitoYBorraElArchivo() {
        FakeMenuApi api = new FakeMenuApi();
        FakeStorageApi storage = new FakeStorageApi();

        ResultadoRed<Void> resultado = remotoCon(api, storage).quitarImagen(1, "foto.jpg");

        assertTrue(resultado.isExitoso());
        // Gson omite los nulos: el null tiene que viajar en un cuerpo JSON literal.
        assertEquals("{\"ruta_imagen\":null}", api.cuerpoCrudoEnviado);
        assertEquals(1, storage.rutasBorradas.size());
        assertEquals("foto.jpg", storage.rutasBorradas.get(0));
    }

    @Test
    public void cambiarEstadoPlatillo_desactivar_noBorraNadaDelBucket() {
        FakeMenuApi api = new FakeMenuApi();
        FakeStorageApi storage = new FakeStorageApi();

        ResultadoRed<Void> resultado = remotoCon(api, storage).cambiarEstadoPlatillo(1, false);

        assertTrue(resultado.isExitoso());
        assertEquals("eq.1", api.ultimoFiltroPlatillo);
        assertTrue(storage.rutasBorradas.isEmpty());
    }

    @Test
    public void cambiarEstadoPlatillo_sinFiltroJamas_elFiltroViajaSiempre() {
        FakeMenuApi api = new FakeMenuApi();

        remotoCon(api, new FakeStorageApi()).cambiarEstadoPlatillo(7, true);

        // Un PATCH sin filtro actualizaría todas las filas de la tabla.
        assertEquals("eq.7", api.ultimoFiltroPlatillo);
    }

    @Test
    public void cambiarEstadoPlatillo_soloPAtcheaElEstado() {
        FakeMenuApi api = new FakeMenuApi();

        remotoCon(api, new FakeStorageApi()).cambiarEstadoPlatillo(1, false);

        String cuerpo = new Gson().toJson(api.ultimoActualizarPlatillo);
        assertTrue(cuerpo.contains("\"id_estado\":2"));
        assertFalse(cuerpo.contains("nombre"));
    }

    // ------------------------------------------------------------------ categorías

    @Test
    public void crearCategoria_exitoso_devuelveLaCategoriaCreada() {
        FakeMenuApi api = new FakeMenuApi();
        CategoriaDto[] creada = gson.fromJson(
                "[{\"id_categoria\":5,\"descripcion\":\"Postres\",\"id_estado\":1,"
                        + "\"cantidad_platillos\":0,\"cantidad_platillos_activos\":0}]",
                CategoriaDto[].class);
        api.respuestaCrearCategoria = FakeCall.deRespuesta(Response.success(List.of(creada)));

        ResultadoRed<CategoriaDto> resultado =
                remotoCon(api, new FakeStorageApi()).crearCategoria("Postres");

        assertTrue(resultado.isExitoso());
        assertEquals(5, resultado.getValor().getIdCategoria());
        assertEquals(0, resultado.getValor().getCantidadPlatillos());
    }

    @Test
    public void crearCategoria_sinSesion_devuelveFalloSinLlamarALaRed() {
        FakeMenuApi api = new FakeMenuApi();

        ResultadoRed<CategoriaDto> resultado = remotoSinSesion(api).crearCategoria("Postres");

        assertFalse(resultado.isExitoso());
        assertEquals(401, resultado.getCodigoHttp());
    }

    @Test
    public void borrarCategoria_conPlatillos_devuelveElMensajeDelTrigger() {
        FakeMenuApi api = new FakeMenuApi();
        api.respuestaBorrarCategoria = FakeCall.deRespuesta(errorConMensaje(400,
                "No se puede borrar una categoría que todavía tiene platillos."));

        ResultadoRed<Void> resultado = remotoCon(api, new FakeStorageApi()).borrarCategoria(1);

        assertFalse(resultado.isExitoso());
        // El mensaje del trigger lo escribimos nosotros en lenguaje humano: sí se muestra.
        assertEquals("No se puede borrar una categoría que todavía tiene platillos.",
                resultado.getMensaje());
    }

    @Test
    public void borrarCategoria_vacia_funcionaYUsaElFiltro() {
        FakeMenuApi api = new FakeMenuApi();

        ResultadoRed<Void> resultado = remotoCon(api, new FakeStorageApi()).borrarCategoria(9);

        assertTrue(resultado.isExitoso());
        assertEquals("eq.9", api.ultimoFiltroCategoria);
    }

    @Test
    public void renombrarCategoria_sinConexion_devuelveFalloDeRed() {
        FakeMenuApi api = new FakeMenuApi();
        api.respuestaActualizarCategoria = FakeCall.deFallo(new IOException("timeout"));

        ResultadoRed<Void> resultado =
                remotoCon(api, new FakeStorageApi()).renombrarCategoria(1, "Entraditas");

        assertFalse(resultado.isExitoso());
        assertEquals("Sin conexión al servidor. Intentá de nuevo.", resultado.getMensaje());
    }

    // ------------------------------------------------------------------ fakes

    /** Fake mínimo: solo implementa lo que {@link MenuRemoto} usa. */
    private static final class FakeMenuApi implements SupabaseMenuApi {

        Call<List<PlatilloDto>> respuestaListarPlatillos;
        Call<List<CategoriaDto>> respuestaListarCategorias;
        Call<List<PlatilloDto>> respuestaListarPlatillosDesde;
        Call<List<CategoriaDto>> respuestaListarCategoriasDesde;
        Call<List<PlatilloDto>> respuestaCrearPlatillo;
        Call<List<CategoriaDto>> respuestaCrearCategoria;
        Call<Void> respuestaActualizarPlatillo = FakeCall.deRespuesta(Response.success(null));
        Call<Void> respuestaActualizarCategoria = FakeCall.deRespuesta(Response.success(null));
        Call<Void> respuestaBorrarCategoria = FakeCall.deRespuesta(Response.success(null));

        int llamadasListarPlatillos;
        int llamadasCrearPlatillo;
        String ultimoFiltroPlatillo;
        String ultimoFiltroCategoria;
        String ultimoFiltroDesdePlatillos;
        String ultimoOrdenPlatillos;
        int ultimoOffsetPlatillos;
        String cuerpoCrudoEnviado;
        ActualizarPlatilloDto ultimoActualizarPlatillo;
        CrearPlatilloDto ultimoCrearPlatillo;

        @Override
        public Call<List<PlatilloDto>> listarPlatillos(String bearerToken) {
            llamadasListarPlatillos++;
            return respuestaListarPlatillos;
        }

        @Override
        public Call<List<CategoriaDto>> listarCategorias(String bearerToken) {
            return respuestaListarCategorias;
        }

        @Override
        public Call<List<PlatilloDto>> listarPlatillosDesde(String bearerToken, String select,
                                                            String actualizadoEnMayorQue,
                                                            String orden, int limite,
                                                            int desplazamiento) {
            ultimoFiltroDesdePlatillos = actualizadoEnMayorQue;
            ultimoOrdenPlatillos = orden;
            ultimoOffsetPlatillos = desplazamiento;
            return respuestaListarPlatillosDesde;
        }

        @Override
        public Call<List<CategoriaDto>> listarCategoriasDesde(String bearerToken, String select,
                                                              String actualizadoEnMayorQue,
                                                              String orden, int limite,
                                                              int desplazamiento) {
            return respuestaListarCategoriasDesde;
        }

        @Override
        public Call<List<PlatilloDto>> crearPlatillo(String bearerToken, CrearPlatilloDto cuerpo) {
            llamadasCrearPlatillo++;
            ultimoCrearPlatillo = cuerpo;
            return respuestaCrearPlatillo;
        }

        @Override
        public Call<Void> actualizarPlatillo(String bearerToken, String idPlatilloIgualA,
                                             ActualizarPlatilloDto cuerpo) {
            ultimoFiltroPlatillo = idPlatilloIgualA;
            ultimoActualizarPlatillo = cuerpo;
            return respuestaActualizarPlatillo;
        }

        @Override
        public Call<Void> actualizarPlatilloConCuerpoCrudo(String bearerToken,
                                                           String idPlatilloIgualA,
                                                           RequestBody cuerpoJson) {
            ultimoFiltroPlatillo = idPlatilloIgualA;
            cuerpoCrudoEnviado = leer(cuerpoJson);
            return respuestaActualizarPlatillo;
        }

        @Override
        public Call<List<CategoriaDto>> crearCategoria(String bearerToken,
                                                       CrearCategoriaDto cuerpo) {
            return respuestaCrearCategoria;
        }

        @Override
        public Call<Void> actualizarCategoria(String bearerToken, String idCategoriaIgualA,
                                              ActualizarCategoriaDto cuerpo) {
            ultimoFiltroCategoria = idCategoriaIgualA;
            return respuestaActualizarCategoria;
        }

        @Override
        public Call<Void> borrarCategoria(String bearerToken, String idCategoriaIgualA) {
            ultimoFiltroCategoria = idCategoriaIgualA;
            return respuestaBorrarCategoria;
        }

        private static String leer(RequestBody cuerpo) {
            try (Buffer buffer = new Buffer()) {
                cuerpo.writeTo(buffer);
                return buffer.readString(StandardCharsets.UTF_8);
            } catch (IOException ex) {
                return null;
            }
        }
    }

    /** Fake del bucket: anota qué se subió y qué se borró, que es lo que se verifica. */
    private static final class FakeStorageApi implements SupabaseStorageApi {

        final List<String> rutasSubidas = new ArrayList<>();
        final List<String> rutasBorradas = new ArrayList<>();
        String tipoSubido;

        Call<Void> respuestaSubir = FakeCall.deRespuesta(Response.success(null));
        Call<Void> respuestaBorrar = FakeCall.deRespuesta(Response.success(null));

        @Override
        public Call<Void> subir(String bearerToken, String ruta, RequestBody imagen) {
            if (imagen.contentType() != null) {
                tipoSubido = imagen.contentType().toString();
            }
            // Solo se anota si la subida efectivamente va a responder OK: una subida
            // fallida no deja archivo que borrar.
            rutasSubidas.add(ruta);
            return respuestaSubir;
        }

        @Override
        public Call<Void> borrar(String bearerToken, String ruta) {
            rutasBorradas.add(ruta);
            return respuestaBorrar;
        }
    }
}
