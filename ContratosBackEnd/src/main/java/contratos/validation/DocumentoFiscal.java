package contratos.validation;

import java.util.Locale;

/**
 * CPF (11 dígitos) ou CNPJ (14 caracteres) do prestador, guardado só com letras e números, sem máscara.
 * O CNPJ aceita o formato alfanumérico da Receita Federal (12 letras/números + 2 dígitos verificadores): o valor de
 * cada caractere no cálculo é o código ASCII menos 48, o que dá o mesmo resultado dos CNPJs numéricos antigos.
 */
public final class DocumentoFiscal {
    private static final int[] PESOS_CNPJ_1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_CNPJ_2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private DocumentoFiscal() {
    }

    /** Tira máscara e espaços e deixa em maiúsculas: "12.abc.345/01de-35" vira "12ABC34501DE35". */
    public static String normalizar(String valor) {
        if (valor == null) return null;
        return valor.replaceAll("[^0-9A-Za-z]", "").toUpperCase(Locale.ROOT);
    }

    /** Aceita com ou sem máscara. 11 caracteres valem como CPF e 14 como CNPJ; qualquer outro tamanho é inválido. */
    public static boolean valido(String valor) {
        String n = normalizar(valor);
        if (n == null) return false;
        return switch (n.length()) {
            case 11 -> cpfValido(n);
            case 14 -> cnpjValido(n);
            default -> false;
        };
    }

    /** "CPF" para 11 caracteres, "CNPJ" para o resto. Recebe o valor já normalizado. */
    public static String rotulo(String normalizado) {
        return normalizado != null && normalizado.length() == 11 ? "CPF" : "CNPJ";
    }

    static boolean cpfValido(String n) {
        if (!n.matches("\\d{11}") || todosIguais(n)) return false;
        return digitoCpf(n, 9) == n.charAt(9) - '0' && digitoCpf(n, 10) == n.charAt(10) - '0';
    }

    static boolean cnpjValido(String n) {
        if (!n.matches("[0-9A-Z]{12}\\d{2}") || todosIguais(n)) return false;
        return digitoCnpj(n, PESOS_CNPJ_1) == n.charAt(12) - '0' && digitoCnpj(n, PESOS_CNPJ_2) == n.charAt(13) - '0';
    }

    private static int digitoCpf(String n, int quantos) {
        int soma = 0;
        for (int i = 0; i < quantos; i++) {
            soma += (n.charAt(i) - '0') * (quantos + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    private static int digitoCnpj(String n, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (n.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static boolean todosIguais(String n) {
        return n.chars().allMatch(c -> c == n.charAt(0));
    }
}
