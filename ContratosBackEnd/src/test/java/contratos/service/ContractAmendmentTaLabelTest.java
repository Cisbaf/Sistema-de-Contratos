package contratos.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** TA-10.3/10.4 — regra do "TA N": o maior entre o número no fim do texto atual e a quantidade de aditivos, mais 1. */
class ContractAmendmentTaLabelTest {

    @ParameterizedTest(name = "texto \"{0}\" com {1} aditivo(s) -> {2}")
    @CsvSource(value = {
            "NULL,           0, TA 1",        // contrato sem nada
            "'',             0, TA 1",        // campo vazio
            "'TA 03',        0, TA 4",        // legado com zero à esquerda
            "'TA 1',         0, TA 2",
            "'TA 1',         3, TA 4",        // a contagem de aditivos manda quando é maior
            "'TA 7',         2, TA 8",        // o número digitado manda quando é maior
            "'Aditivo 7',    0, TA 8",        // qualquer texto que termine em número
            "'Aditivo sem número', 0, TA 1",  // ilegível: cai para a contagem
            "'Aditivo sem número', 2, TA 3",
            "'TA 999999',    0, TA 1000000",  // maior valor aceito: o rótulo continua com 10 caracteres
            "'TA 12345678',  0, TA 1"         // número grande demais é ignorado em vez de virar lixo
    }, nullValues = "NULL")
    void proximoRotulo(String textoAtual, long aditivosJaRegistrados, String esperado) {
        assertThat(ContractAmendmentService.nextTaLabel(textoAtual, aditivosJaRegistrados)).isEqualTo(esperado);
    }

    @Test
    void espacoNoFimNaoAtrapalha() {
        assertThat(ContractAmendmentService.nextTaLabel("TA 5  ", 0)).isEqualTo("TA 6");
    }

    @Test
    void rotuloMaximoCabeNaColunaDeDezCaracteres() {
        assertThat(ContractAmendmentService.nextTaLabel("TA 999999", 0)).hasSizeLessThanOrEqualTo(10);
    }
}
