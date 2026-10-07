package contratos.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** LC-10 passo 1 — a regra da faixa de lançamento, sem Spring nem banco. */
class FaixaLancamentoTest {

    // 06/10/2026 a 16/02/2027: out, nov, dez, jan, fev = 5 meses
    private final FaixaLancamento faixa = FaixaLancamento.de(LocalDate.of(2026, 10, 6), LocalDate.of(2027, 2, 16));

    @Test
    void totalDeParcelasContaOMesDeInicioEOMesDeTermino() {
        assertThat(faixa.totalParcelas()).isEqualTo(5);
        assertThat(FaixaLancamento.de(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 3, 1)).totalParcelas()).isEqualTo(1);
        assertThat(FaixaLancamento.de(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1)).totalParcelas()).isEqualTo(2);
    }

    @Test
    void competenciaNosLimitesEstaDentroEUmMesAntesOuDepoisEstaFora() {
        assertThat(faixa.competenciaDentro(LocalDate.of(2026, 10, 1))).isTrue();
        assertThat(faixa.competenciaDentro(LocalDate.of(2026, 10, 31))).isTrue();
        assertThat(faixa.competenciaDentro(LocalDate.of(2027, 2, 1))).isTrue();
        assertThat(faixa.competenciaDentro(LocalDate.of(2027, 2, 28))).isTrue();
        assertThat(faixa.competenciaDentro(LocalDate.of(2026, 9, 30))).isFalse();
        assertThat(faixa.competenciaDentro(LocalDate.of(2027, 3, 1))).isFalse();
        assertThat(faixa.competenciaDentro(LocalDate.of(2007, 1, 1))).isFalse();
    }

    @Test
    void parcelaVaziaOuNulaContaComoDentro() {
        assertThat(faixa.parcelaDentro(null)).isTrue();
        assertThat(faixa.parcelaDentro("")).isTrue();
        assertThat(faixa.parcelaDentro("   ")).isTrue();
    }

    @Test
    void parcelaDeUmAteOTotalEstaDentro() {
        assertThat(faixa.parcelaDentro("1")).isTrue();
        assertThat(faixa.parcelaDentro("5")).isTrue();
        assertThat(faixa.parcelaDentro(" 3 ")).isTrue();
    }

    @Test
    void parcelaZeroAcimaDoTotalOuNegativaEstaFora() {
        assertThat(faixa.parcelaDentro("0")).isFalse();
        assertThat(faixa.parcelaDentro("6")).isFalse();
        assertThat(faixa.parcelaDentro("-1")).isFalse();
        assertThat(faixa.parcelaDentro("99999999999999999999")).isFalse();
    }

    @Test
    void textoQueNaoEUmNumeroDeLancamentoAntigoContaComoFora() {
        assertThat(faixa.parcelaDentro("1/12")).isFalse();
        assertThat(faixa.parcelaDentro("única")).isFalse();
        assertThat(faixa.parcelaDentro("abc")).isFalse();
    }

    @Test
    void textosDeMesAnoSaoOsDoMensageiroDeErro() {
        assertThat(faixa.inicioFmt()).isEqualTo("10/2026");
        assertThat(faixa.fimFmt()).isEqualTo("02/2027");
    }
}
