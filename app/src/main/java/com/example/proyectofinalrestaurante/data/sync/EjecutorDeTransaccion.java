package com.example.proyectofinalrestaurante.data.sync;

/**
 * Corre un bloque de escrituras dentro de una sola transacción de la base local.
 *
 * <p>Existe para que los sincronizadores puedan agrupar la aplicación de una página del
 * delta sin recibir la {@code AppDatabase} entera. Reciben DAOs sueltos a propósito —así
 * los tests les pasan fakes en memoria—, y pedirles la base rompería esa costura.</p>
 *
 * <p>Por qué importa agrupar (medido en la Sesión 2026-08-04): aplicar 50 filas una por una
 * son 50 transacciones SQLite, cada una con su {@code fsync}, y además 50 invalidaciones de
 * tabla. Cada invalidación hace que Room vuelva a correr las consultas observadas, se
 * remapee la lista completa y el {@code RecyclerView} se repinte. Con una transacción por
 * página, Room difiere las notificaciones hasta el commit: una sola re-emisión en vez de 50.
 * En gama baja esa diferencia es la que se ve como "los items cargaron de a poco".</p>
 */
public interface EjecutorDeTransaccion {

    /**
     * Implementación directa para tests: corre el bloque sin transacción real.
     *
     * <p>Deja los tests que no miran transacciones exactamente igual que antes.</p>
     */
    EjecutorDeTransaccion DIRECTO = Runnable::run;

    /** Ejecuta {@code bloque} en una transacción; hace commit al salir sin excepción. */
    void enTransaccion(Runnable bloque);
}
