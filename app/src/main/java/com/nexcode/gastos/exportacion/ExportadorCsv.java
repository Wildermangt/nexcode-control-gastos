package com.nexcode.gastos.exportacion;

import com.nexcode.gastos.domain.model.Category;
import com.nexcode.gastos.domain.model.Transaction;

import java.util.List;
import java.util.Map;

/**
 * Convierte los movimientos del usuario en un archivo CSV.
 *
 * Escrito en Java a proposito. Es un formateador puro —entra una lista, sale
 * una cadena—, sin estado, sin corrutinas y sin nada de Compose, asi que se
 * puede leer entero de una sentada y probar sin levantar la aplicacion.
 *
 * La clase es final y el constructor privado: no hay nada que heredar ni que
 * instanciar, solo dos funciones sobre los datos que se le pasan.
 */
public final class ExportadorCsv {

    /** Excel en configuracion regional espanola espera punto y coma. */
    private static final char SEPARADOR = ';';

    private static final String SALTO = "\r\n";

    private static final String CABECERA =
            "Fecha;Tipo;Titulo;Categoria;Monto;Nota";

    private ExportadorCsv() {
        throw new AssertionError("Clase de utilidades: no se instancia");
    }

    /**
     * Construye el CSV completo.
     *
     * @param movimientos  los movimientos a exportar; si es nulo o vacio se
     *                     devuelve solo la cabecera, que sigue siendo un CSV
     *                     valido y evita que la app tenga que tratar ese caso.
     * @param categorias   indice de categorias por identificador, para escribir
     *                     el nombre y no el numero.
     */
    public static String exportar(List<Transaction> movimientos,
                                  Map<Long, Category> categorias) {
        StringBuilder csv = new StringBuilder();
        csv.append(CABECERA).append(SALTO);

        if (movimientos == null || movimientos.isEmpty()) {
            return csv.toString();
        }

        for (Transaction movimiento : movimientos) {
            if (movimiento == null) {
                continue;
            }
            csv.append(fila(movimiento, categorias)).append(SALTO);
        }
        return csv.toString();
    }

    /** Una linea del archivo, con todos sus campos ya escapados. */
    private static String fila(Transaction movimiento, Map<Long, Category> categorias) {
        StringBuilder fila = new StringBuilder();

        fila.append(escapar(movimiento.getDay().toString())).append(SEPARADOR);
        fila.append(escapar(movimiento.getType().getCode())).append(SEPARADOR);
        fila.append(escapar(movimiento.getTitle())).append(SEPARADOR);
        fila.append(escapar(nombreDeCategoria(movimiento, categorias))).append(SEPARADOR);
        fila.append(escapar(movimiento.montoConSigno())).append(SEPARADOR);
        fila.append(escapar(movimiento.getNote()));

        return fila.toString();
    }

    /**
     * Nombre de la categoria del movimiento.
     *
     * La categoria puede faltar de dos formas distintas: que el movimiento no
     * tenga ninguna, o que tuviera una que despues se borro y ya no esta en el
     * indice. Las dos se resuelven igual de cara al archivo.
     */
    private static String nombreDeCategoria(Transaction movimiento,
                                            Map<Long, Category> categorias) {
        Long id = movimiento.getCategoryId();
        if (id == null || categorias == null) {
            return "Sin categoria";
        }
        Category categoria = categorias.get(id);
        return categoria == null ? "Sin categoria" : categoria.getName();
    }

    /**
     * Escapa un campo segun el RFC 4180.
     *
     * Es la parte que de verdad importa del formato: un titulo con un punto y
     * coma —"Mercado; frutas"— parte la fila en dos columnas y desplaza todo
     * lo que venga detras. La regla es entrecomillar cuando el campo contiene
     * el separador, comillas o un salto de linea, y duplicar las comillas de
     * dentro.
     */
    static String escapar(String campo) {
        if (campo == null || campo.isEmpty()) {
            return "";
        }

        boolean necesitaComillas = campo.indexOf(SEPARADOR) >= 0
                || campo.indexOf('"') >= 0
                || campo.indexOf('\n') >= 0
                || campo.indexOf('\r') >= 0;

        if (!necesitaComillas) {
            return campo;
        }

        StringBuilder escapado = new StringBuilder(campo.length() + 2);
        escapado.append('"');
        for (int i = 0; i < campo.length(); i++) {
            char caracter = campo.charAt(i);
            if (caracter == '"') {
                escapado.append('"');   // una comilla dentro se escribe doble
            }
            escapado.append(caracter);
        }
        escapado.append('"');
        return escapado.toString();
    }

    /** Nombre sugerido del archivo, con la fecha para no pisar exportaciones. */
    public static String nombreDeArchivo(String fechaIso) {
        String fecha = (fechaIso == null || fechaIso.isEmpty()) ? "sin-fecha" : fechaIso;
        return "nexcode-movimientos-" + fecha + ".csv";
    }
}
