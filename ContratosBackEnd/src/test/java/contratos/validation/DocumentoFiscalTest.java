package contratos.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** CPF e CNPJ (numérico e alfanumérico) do prestador, sem Spring nem banco. */
class DocumentoFiscalTest {

    // ------------------------------------------------------------------ CPF

    @Test
    void cpfValidoComESemMascara() {
        assertThat(DocumentoFiscal.valido("529.982.247-25")).isTrue();
        assertThat(DocumentoFiscal.valido("52998224725")).isTrue();
        assertThat(DocumentoFiscal.valido("111.444.777-35")).isTrue();
        assertThat(DocumentoFiscal.valido("123.456.789-09")).isTrue();
    }

    @Test
    void cpfComDigitoVerificadorErradoEhInvalido() {
        assertThat(DocumentoFiscal.valido("529.982.247-24")).isFalse();
        assertThat(DocumentoFiscal.valido("529.982.247-35")).isFalse();
        assertThat(DocumentoFiscal.valido("123.456.789-00")).isFalse();
    }

    @Test
    void cpfComTodosOsDigitosIguaisEhInvalido() {
        for (int d = 0; d <= 9; d++) {
            assertThat(DocumentoFiscal.valido(String.valueOf(d).repeat(11))).as("CPF " + d + "x11").isFalse();
        }
    }

    @Test
    void cpfComLetraEhInvalido() {
        assertThat(DocumentoFiscal.valido("5299822472A")).isFalse();
    }

    // ------------------------------------------------------------------ CNPJ

    @Test
    void cnpjNumericoValidoComESemMascara() {
        assertThat(DocumentoFiscal.valido("11.222.333/0001-81")).isTrue();
        assertThat(DocumentoFiscal.valido("11222333000181")).isTrue();
        assertThat(DocumentoFiscal.valido("11.444.777/0001-61")).isTrue();
    }

    /** Resto 1 na soma dos pesos deve virar dígito 0 (resto 0 ou 1 -> 0; resto >= 2 -> 11 - resto). */
    @Test
    void cnpjCujoRestoDoDigitoVerificadorEhUmTemDigitoZero() {
        // 1º DV: soma % 11 == 1 -> dígito 0 (2º DV = 3)
        assertThat(DocumentoFiscal.valido("01122233300603")).isTrue();
        assertThat(DocumentoFiscal.valido("01.122.233/3006-03")).isTrue();
        // 2º DV: soma % 11 == 1 -> dígito 0
        assertThat(DocumentoFiscal.valido("01122233300000")).isTrue();
        // 2º DV com resto 0 -> dígito 0
        assertThat(DocumentoFiscal.valido("01122233300190")).isTrue();
        // outro dígito no lugar do 0 continua inválido
        assertThat(DocumentoFiscal.valido("01122233300613")).isFalse();
        assertThat(DocumentoFiscal.valido("01122233300001")).isFalse();
    }

    @Test
    void cnpjNumericoComDigitoVerificadorErradoEhInvalido() {
        assertThat(DocumentoFiscal.valido("11.222.333/0001-82")).isFalse();
        assertThat(DocumentoFiscal.valido("11.222.333/0001-91")).isFalse();
    }

    @Test
    void cnpjAlfanumericoDoExemploOficialDaReceitaEhValido() {
        assertThat(DocumentoFiscal.valido("12.ABC.345/01DE-35")).isTrue();
        assertThat(DocumentoFiscal.valido("12ABC34501DE35")).isTrue();
        assertThat(DocumentoFiscal.valido("12.abc.345/01de-35")).isTrue();
    }

    @Test
    void cnpjAlfanumericoComDigitoVerificadorErradoOuLetraNoDigitoEhInvalido() {
        assertThat(DocumentoFiscal.valido("12.ABC.345/01DE-36")).isFalse();
        assertThat(DocumentoFiscal.valido("12.ABC.345/01DE-3A")).isFalse();
        assertThat(DocumentoFiscal.valido("12.ABC.345/01DX-35")).isFalse();
    }

    @Test
    void cnpjComTodosOsCaracteresIguaisEhInvalido() {
        assertThat(DocumentoFiscal.valido("00.000.000/0000-00")).isFalse();
        assertThat(DocumentoFiscal.valido("11111111111111")).isFalse();
    }

    // ------------------------------------------------------------------ tamanho e entradas estranhas

    @Test
    void tamanhoQueNaoEOdeCpfNemODeCnpjEhInvalido() {
        assertThat(DocumentoFiscal.valido("1234567890")).isFalse();      // 10
        assertThat(DocumentoFiscal.valido("123456789012")).isFalse();    // 12
        assertThat(DocumentoFiscal.valido("1122233300018")).isFalse();   // 13
        assertThat(DocumentoFiscal.valido("112223330001811")).isFalse(); // 15
        assertThat(DocumentoFiscal.valido("")).isFalse();
        assertThat(DocumentoFiscal.valido("abc")).isFalse();
        assertThat(DocumentoFiscal.valido(null)).isFalse();
    }

    // ------------------------------------------------------------------ normalização e rótulo

    @Test
    void normalizarTiraMascaraEEspacosEDeixaEmMaiusculas() {
        assertThat(DocumentoFiscal.normalizar(" 529.982.247-25 ")).isEqualTo("52998224725");
        assertThat(DocumentoFiscal.normalizar("12.abc.345/01de-35")).isEqualTo("12ABC34501DE35");
        assertThat(DocumentoFiscal.normalizar(null)).isNull();
    }

    @Test
    void rotuloEhCpfPara11CaracteresECnpjParaOResto() {
        assertThat(DocumentoFiscal.rotulo("52998224725")).isEqualTo("CPF");
        assertThat(DocumentoFiscal.rotulo("11222333000181")).isEqualTo("CNPJ");
        assertThat(DocumentoFiscal.rotulo("12ABC34501DE35")).isEqualTo("CNPJ");
        assertThat(DocumentoFiscal.rotulo(null)).isEqualTo("CNPJ");
    }
}
