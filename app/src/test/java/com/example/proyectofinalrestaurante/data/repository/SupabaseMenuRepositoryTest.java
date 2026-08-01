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
import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Categoria;
import com.example.proyectofinalrestaurante.domain.model.ImagenPlatillo;
import com.example.proyectofinalrestaurante.domain.model.NuevoPlatillo;
import com.example.proyectofinalrestaurante.domain.model.Platillo;
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
 * Tests de {@link SupabaseMenuRepository} (Plan Fase 2a, E7).
 *
 * <p>El caso importante es la compensación: si la foto se subió pero el insert falla,
 * el archivo tiene que borrarse. Sin eso, cada error deja basura permanente en el bucket.</p>
 */
public class SupabaseMenuRepositoryTest {

    private static final String JSON_PLATILLO =
            "{\"id_platillo\":1,\"nombre\":\"Baleada sencilla\",\"descripcion\":\"Con frijoles\","
                    + "\"precio\":35.00,\"id_categoria\":1,\"nombre_categoria\":\"Entradas\","
                    + "\"ruta_imagen\":null,\"id_estado\":1}";

    private final Gson gson = new Gson();

    private SupabaseMenuRepository repositorioCon(FakeMenuApi api, FakeStorageApi storage) {
        return new SupabaseMenuRepository(api, storage, () -> "token-válido");
    }

    private static NuevoPlatillo nuevoPlatillo() {
        return new NuevoPlatillo("Baleada sencilla", "Con frijoles", 35.0, 1);
    }

    private static ImagenPlatillo imagen() {
        return new ImagenPlatillo(new byte[]{1, 2, 3}, ImagenPlatillo.MIME_JPEG);
    }

    private static Response<Void> errorConMensaje(int codigo, String mensaje) {
        return Response.error(codigo, ResponseBody.create(MediaType.get("application/json"),
                "{\"message\":\"" + mensaje + "\"}"));
    }

    // ------------------------------------------------------------------ lectura

    @Test
    public void listarPlatillos_sinSesion_devuelveFalloSinLlamarALaRed() {
        FakeMenuApi api = new FakeMenuApi();
        SupabaseMenuRepository repositorio =
                new SupabaseMenuRepository(api, new FakeStorageApi(), () -> null);

        Result<List<Platillo>> resultado = repositorio.listarPlatillos();

        assertFalse(resultado.isSuccess());
        assertEquals("Tu sesión venció. Volvé a iniciar sesión.", resultado.getError());
        assertEquals(0, api.llamadasListarPlatillos);
    }

    @Test
    public void listarPlatillos_exitoso_mapeaLosDtoADominio() {
        FakeMenuApi api = new FakeMenuApi();
        PlatilloDto[] dtos = gson.fromJson("[" + JSON_PLATILLO + "]", PlatilloDto[].class);
        api.respuestaListarPlatillos = FakeCall.deRespuesta(Response.success(List.of(dtos)));

        Result<List<Platillo>> resultado = repositorioCon(api, new FakeStorageApi()).listarPlatillos();

        assertTrue(resultado.isSuccess());
        assertEquals(1, resultado.getValue().size());
        Platillo platillo = resultado.getValue().get(0);
        assertEquals("Baleada sencilla", platillo.getNombre());
        assertEquals("Entradas", platillo.getNombreCategoria());
        assertEquals(35.0, platillo.getPrecio(), 0.001);
        assertTrue(platillo.isActivo());
        assertFalse(platillo.tieneImagen());
    }

    @Test
    public void listarPlatillos_platilloInactivo_seMapeaComoInactivo() {
        FakeMenuApi api = new FakeMenuApi();
        // El estado se deriva de id_estado, no de la columna `activo` de la vista.
        PlatilloDto[] dtos = gson.fromJson(
                "[{\"id_platillo\":2,\"nombre\":\"Sopa\",\"precio\":90.0,\"id_categoria\":2,\"id_estado\":2}]",
                PlatilloDto[].class);
        api.respuestaListarPlatillos = FakeCall.deRespuesta(Response.success(List.of(dtos)));

        Result<List<Platillo>> resultado = repositorioCon(api, new FakeStorageApi()).listarPlatillos();

        assertTrue(resultado.isSuccess());
        assertFalse(resultado.getValue().get(0).isActivo());
    }

    @Test
    public void listarPlatillos_sinConexion_devuelveFalloDeRed() {
        FakeMenuApi api = new FakeMenuApi();
        api.respuestaListarPlatillos = FakeCall.deFallo(new IOException("timeout"));

        Result<List<Platillo>> resultado = repositorioCon(api, new FakeStorageApi()).listarPlatillos();

        assertFalse(resultado.isSuccess());
        assertEquals("Sin conexión al servidor. Intentá de nuevo.", resultado.getError());
    }

    @Test
    public void listarCategorias_exitoso_traeLosContadores() {
        FakeMenuApi api = new FakeMenuApi();
        CategoriaDto[] dtos = gson.fromJson(
                "[{\"id_categoria\":1,\"descripcion\":\"Entradas\",\"id_estado\":1,"
                        + "\"cantidad_platillos\":3,\"cantidad_platillos_activos\":2}]",
                CategoriaDto[].class);
        api.respuestaListarCategorias = FakeCall.deRespuesta(Response.success(List.of(dtos)));

        Result<List<Categoria>> resultado = repositorioCon(api, new FakeStorageApi()).listarCategorias();

        assertTrue(resultado.isSuccess());
        assertEquals(3, resultado.getValue().get(0).getCantidadPlatillos());
        assertEquals(2, resultado.getValue().get(0).getCantidadPlatillosActivos());
    }

    // ------------------------------------------------------------------ crear

    @Test
    public void crearPlatillo_sinImagen_devuelveElPlatilloCreado() {
        FakeMenuApi api = new FakeMenuApi();
        FakeStorageApi storage = new FakeStorageApi();
        PlatilloDto[] creado = gson.fromJson("[" + JSON_PLATILLO + "]", PlatilloDto[].class);
        api.respuestaCrearPlatillo = FakeCall.deRespuesta(Response.success(List.of(creado)));

        Result<Platillo> resultado = repositorioCon(api, storage).crearPlatillo(nuevoPlatillo(), null);

        assertTrue(resultado.isSuccess());
        assertEquals(1, resultado.getValue().getIdPlatillo());
        assertTrue(storage.rutasSubidas.isEmpty());
    }

    @Test
    public void crearPlatillo_conImagen_subePrimeroYGuardaLaRuta() {
        FakeMenuApi api = new FakeMenuApi();
        FakeStorageApi storage = new FakeStorageApi();
        PlatilloDto[] creado = gson.fromJson("[" + JSON_PLATILLO + "]", PlatilloDto[].class);
        api.respuestaCrearPlatillo = FakeCall.deRespuesta(Response.success(List.of(creado)));

        Result<Platillo> resultado = repositorioCon(api, storage).crearPlatillo(nuevoPlatillo(), imagen());

        assertTrue(resultado.isSuccess());
        assertEquals(1, storage.rutasSubidas.size());
        assertTrue(storage.rutasSubidas.get(0).endsWith(".jpg"));
        assertEquals(ImagenPlatillo.MIME_JPEG, storage.tipoSubido);
        assertTrue(storage.rutasBorradas.isEmpty());
    }

    @Test
    public void crearPlatillo_siFallaLaSubida_noInsertaNada() {
        FakeMenuApi api = new FakeMenuApi();
        FakeStorageApi storage = new FakeStorageApi();
        storage.respuestaSubir = FakeCall.deRespuesta(errorConMensaje(413, "Payload too large"));

        Result<Platillo> resultado = repositorioCon(api, storage).crearPlatillo(nuevoPlatillo(), imagen());

        assertFalse(resultado.isSuccess());
        assertEquals("No se pudo subir la foto. Intentá de nuevo.", resultado.getError());
        assertEquals(0, api.llamadasCrearPlatillo);
    }

    @Test
    public void crearPlatillo_imagenSubidaPeroInsertRechazado_borraElObjetoDelBucket() {
        FakeMenuApi api = new FakeMenuApi();
        FakeStorageApi storage = new FakeStorageApi();
        api.respuestaCrearPlatillo = FakeCall.deRespuesta(Response.error(409,
                ResponseBody.create(MediaType.get("application/json"),
                        "{\"message\":\"duplicate key value violates unique constraint\\\"uq_platillo_nombre\\\"\"}")));

        Result<Platillo> resultado = repositorioCon(api, storage).crearPlatillo(nuevoPlatillo(), imagen());

        assertFalse(resultado.isSuccess());
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

        Result<Platillo> resultado = repositorioCon(api, storage).crearPlatillo(nuevoPlatillo(), gif);

        assertFalse(resultado.isSuccess());
        assertTrue(storage.rutasSubidas.isEmpty());
        assertEquals(0, api.llamadasCrearPlatillo);
    }

    @Test
    public void crearPlatillo_errorDelServidor_devuelveElMensajeDePostgrest() {
        FakeMenuApi api = new FakeMenuApi();
        api.respuestaCrearPlatillo = FakeCall.deRespuesta(Response.error(400,
                ResponseBody.create(MediaType.get("application/json"),
                        "{\"message\":\"new row violates check constraint \\\"ck_platillo_precio_positivo\\\"\"}")));

        Result<Platillo> resultado =
                repositorioCon(api, new FakeStorageApi()).crearPlatillo(nuevoPlatillo(), null);

        assertFalse(resultado.isSuccess());
        assertTrue(resultado.getError().contains("ck_platillo_precio_positivo"));
    }

    // ------------------------------------------------------------------ actualizar

    @Test
    public void actualizarPlatillo_conFotoNueva_borraLaViejaDespuesDeGuardar() {
        FakeMenuApi api = new FakeMenuApi();
        FakeStorageApi storage = new FakeStorageApi();
        Platillo conFoto = new Platillo(1, "Baleada", "Con frijoles", 35.0, 1, "Entradas",
                "vieja.jpg", true);

        Result<Void> resultado = repositorioCon(api, storage).actualizarPlatillo(conFoto, imagen());

        assertTrue(resultado.isSuccess());
        assertEquals(1, storage.rutasSubidas.size());
        assertEquals(1, storage.rutasBorradas.size());
        assertEquals("vieja.jpg", storage.rutasBorradas.get(0));
        // La ruta nueva nunca reusa la vieja: si no, Glide seguiría sirviendo la foto vieja.
        assertFalse(storage.rutasSubidas.get(0).equals("vieja.jpg"));
    }

    @Test
    public void actualizarPlatillo_siFallaElPatch_borraLaFotoNuevaYNoLaVieja() {
        FakeMenuApi api = new FakeMenuApi();
        FakeStorageApi storage = new FakeStorageApi();
        api.respuestaActualizarPlatillo = FakeCall.deRespuesta(
                errorConMensaje(403, "No autorizado"));
        Platillo conFoto = new Platillo(1, "Baleada", null, 35.0, 1, "Entradas", "vieja.jpg", true);

        Result<Void> resultado = repositorioCon(api, storage).actualizarPlatillo(conFoto, imagen());

        assertFalse(resultado.isSuccess());
        assertEquals(1, storage.rutasBorradas.size());
        assertEquals(storage.rutasSubidas.get(0), storage.rutasBorradas.get(0));
    }

    @Test
    public void quitarImagen_mandaElNullExplicitoYBorraElArchivo() throws IOException {
        FakeMenuApi api = new FakeMenuApi();
        FakeStorageApi storage = new FakeStorageApi();
        Platillo conFoto = new Platillo(1, "Baleada", null, 35.0, 1, "Entradas", "foto.jpg", true);

        Result<Void> resultado = repositorioCon(api, storage).quitarImagen(conFoto);

        assertTrue(resultado.isSuccess());
        // Gson omite los nulos: el null tiene que viajar en un cuerpo JSON literal.
        assertEquals("{\"ruta_imagen\":null}", api.cuerpoCrudoEnviado);
        assertEquals(1, storage.rutasBorradas.size());
        assertEquals("foto.jpg", storage.rutasBorradas.get(0));
    }

    @Test
    public void cambiarEstadoPlatillo_desactivar_noBorraNadaDelBucket() {
        FakeMenuApi api = new FakeMenuApi();
        FakeStorageApi storage = new FakeStorageApi();

        Result<Void> resultado = repositorioCon(api, storage).cambiarEstadoPlatillo(1, false);

        assertTrue(resultado.isSuccess());
        assertEquals("eq.1", api.ultimoFiltroPlatillo);
        assertTrue(storage.rutasBorradas.isEmpty());
    }

    @Test
    public void cambiarEstadoPlatillo_sinFiltroJamas_elFiltroViajaSiempre() {
        FakeMenuApi api = new FakeMenuApi();

        repositorioCon(api, new FakeStorageApi()).cambiarEstadoPlatillo(7, true);

        // Un PATCH sin filtro actualizaría todas las filas de la tabla.
        assertEquals("eq.7", api.ultimoFiltroPlatillo);
    }

    // ------------------------------------------------------------------ categorías

    @Test
    public void crearCategoria_exitoso_devuelveLaCategoriaSinPlatillos() {
        FakeMenuApi api = new FakeMenuApi();
        CategoriaDto[] creada = gson.fromJson(
                "[{\"id_categoria\":5,\"descripcion\":\"Postres\",\"id_estado\":1,"
                        + "\"cantidad_platillos\":0,\"cantidad_platillos_activos\":0}]",
                CategoriaDto[].class);
        api.respuestaCrearCategoria = FakeCall.deRespuesta(Response.success(List.of(creada)));

        Result<Categoria> resultado = repositorioCon(api, new FakeStorageApi()).crearCategoria("Postres");

        assertTrue(resultado.isSuccess());
        assertEquals(5, resultado.getValue().getIdCategoria());
        assertEquals(0, resultado.getValue().getCantidadPlatillos());
    }

    @Test
    public void borrarCategoria_conPlatillos_devuelveElMensajeDelTrigger() {
        FakeMenuApi api = new FakeMenuApi();
        api.respuestaBorrarCategoria = FakeCall.deRespuesta(errorConMensaje(400,
                "No se puede borrar una categoría que todavía tiene platillos."));

        Result<Void> resultado = repositorioCon(api, new FakeStorageApi()).borrarCategoria(1);

        assertFalse(resultado.isSuccess());
        // El mensaje del trigger lo escribimos nosotros en lenguaje humano: sí se muestra.
        assertEquals("No se puede borrar una categoría que todavía tiene platillos.",
                resultado.getError());
    }

    @Test
    public void borrarCategoria_vacia_funcionaYUsaElFiltro() {
        FakeMenuApi api = new FakeMenuApi();

        Result<Void> resultado = repositorioCon(api, new FakeStorageApi()).borrarCategoria(9);

        assertTrue(resultado.isSuccess());
        assertEquals("eq.9", api.ultimoFiltroCategoria);
    }

    @Test
    public void renombrarCategoria_sinConexion_devuelveFalloDeRed() {
        FakeMenuApi api = new FakeMenuApi();
        api.respuestaActualizarCategoria = FakeCall.deFallo(new IOException("timeout"));

        Result<Void> resultado =
                repositorioCon(api, new FakeStorageApi()).renombrarCategoria(1, "Entraditas");

        assertFalse(resultado.isSuccess());
        assertEquals("Sin conexión al servidor. Intentá de nuevo.", resultado.getError());
    }

    // ------------------------------------------------------------------ fakes

    /** Fake mínimo: solo implementa lo que {@link SupabaseMenuRepository} usa. */
    private static final class FakeMenuApi implements SupabaseMenuApi {

        Call<List<PlatilloDto>> respuestaListarPlatillos;
        Call<List<CategoriaDto>> respuestaListarCategorias;
        Call<List<PlatilloDto>> respuestaCrearPlatillo;
        Call<List<CategoriaDto>> respuestaCrearCategoria;
        Call<Void> respuestaActualizarPlatillo = FakeCall.deRespuesta(Response.success(null));
        Call<Void> respuestaActualizarCategoria = FakeCall.deRespuesta(Response.success(null));
        Call<Void> respuestaBorrarCategoria = FakeCall.deRespuesta(Response.success(null));

        int llamadasListarPlatillos;
        int llamadasCrearPlatillo;
        String ultimoFiltroPlatillo;
        String ultimoFiltroCategoria;
        String cuerpoCrudoEnviado;

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
        public Call<List<PlatilloDto>> crearPlatillo(String bearerToken, CrearPlatilloDto cuerpo) {
            llamadasCrearPlatillo++;
            return respuestaCrearPlatillo;
        }

        @Override
        public Call<Void> actualizarPlatillo(String bearerToken, String idPlatilloIgualA,
                                             ActualizarPlatilloDto cuerpo) {
            ultimoFiltroPlatillo = idPlatilloIgualA;
            return respuestaActualizarPlatillo;
        }

        @Override
        public Call<Void> actualizarPlatilloConCuerpoCrudo(String bearerToken, String idPlatilloIgualA,
                                                           RequestBody cuerpoJson) {
            ultimoFiltroPlatillo = idPlatilloIgualA;
            cuerpoCrudoEnviado = leer(cuerpoJson);
            return respuestaActualizarPlatillo;
        }

        @Override
        public Call<List<CategoriaDto>> crearCategoria(String bearerToken, CrearCategoriaDto cuerpo) {
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
