package com.nexcode.gastos.exportacion;

/**
 * Saneamiento del texto libre que escribe el usuario.
 *
 * Cubre un hueco real: el dominio comprueba que el titulo de un movimiento no
 * este en blanco, pero no que sea razonable. Un titulo de dos mil caracteres
 * pegado desde el portapapeles pasa la validacion de negocio, se guarda, y
 * despues rompe la lista y el archivo exportado.
 *
 * Va en Java y junto al exportador porque es el mismo tipo de pieza: reglas
 * sobre cadenas, sin estado y sin dependencias.
 */
public final class ValidadorEntrada {

    /** Suficiente para "Almuerzo con Andrea en la universidad". */
    public static final int MAX_TITULO = 80;

    /** La nota admite mas, pero no un ensayo. */
    public static final int MAX_NOTA = 280;

    /** Con una sola letra la busqueda devolveria practicamente todo. */
    public static final int MIN_BUSQUEDA = 2;

    public static final int MAX_BUSQUEDA = 40;

    private ValidadorEntrada() {
        throw new AssertionError("Clase de utilidades: no se instancia");
    }

    /**
     * Deja el texto en una sola linea, sin espacios de sobra y recortado.
     *
     * Los saltos de linea y los tabuladores se sustituyen por un espacio en
     * vez de eliminarse: quien pega dos lineas espera ver las dos palabras
     * separadas, no pegadas.
     *
     * @return el texto normalizado, o null si no queda nada util.
     */
    public static String normalizar(String texto, int maximo) {
        if (texto == null) {
            return null;
        }

        StringBuilder limpio = new StringBuilder(texto.length());
        boolean espacioPendiente = false;

        for (int i = 0; i < texto.length(); i++) {
            char caracter = texto.charAt(i);

            if (Character.isWhitespace(caracter)) {
                // El espacio se aplaza: asi varios seguidos cuentan como uno y
                // no queda ninguno al final de la cadena.
                espacioPendiente = limpio.length() > 0;
                continue;
            }

            // Los caracteres de control no se ven pero rompen el CSV.
            if (Character.isISOControl(caracter)) {
                continue;
            }

            if (espacioPendiente) {
                limpio.append(' ');
                espacioPendiente = false;
            }
            limpio.append(caracter);

            if (limpio.length() >= maximo) {
                break;
            }
        }

        return limpio.length() == 0 ? null : limpio.toString();
    }

    /** Normaliza un titulo de movimiento. */
    public static String titulo(String texto) {
        return normalizar(texto, MAX_TITULO);
    }

    /** Normaliza una nota de movimiento. */
    public static String nota(String texto) {
        return normalizar(texto, MAX_NOTA);
    }

    /**
     * Prepara el texto de una busqueda.
     *
     * @return el texto listo para consultar, o null si es demasiado corto para
     *         que la busqueda signifique algo.
     */
    public static String consulta(String texto) {
        String limpio = normalizar(texto, MAX_BUSQUEDA);
        if (limpio == null || limpio.length() < MIN_BUSQUEDA) {
            return null;
        }
        return limpio;
    }

    /** Cierto cuando el texto daria una busqueda con sentido. */
    public static boolean esBusquedaValida(String texto) {
        return consulta(texto) != null;
    }
}
