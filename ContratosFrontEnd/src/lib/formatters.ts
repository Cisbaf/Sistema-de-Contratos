// Documento do prestador: CPF (11 dígitos) ou CNPJ (14 caracteres, numérico ou alfanumérico da Receita Federal).
// A máscara e a validação são decididas pelo tamanho do que foi digitado. Espelha o backend (`DocumentoFiscal`).

/** Só letras e números, em maiúsculas, no máximo 14 (o tamanho de um CNPJ). */
export function normalizeDocumento(value: string) {
    return value.replace(/[^0-9A-Za-z]/g, "").toUpperCase().slice(0, 14);
}

/** Até 11 caracteres só com números: máscara de CPF. Mais que isso, ou com letra: máscara de CNPJ. */
export function formatDocumento(value: string) {
    const n = normalizeDocumento(value);
    const part = (from: number, to: number, prefix: string) => (n.length > from ? prefix + n.slice(from, to) : "");

    if (n.length <= 11 && !/[A-Z]/.test(n)) {
        return n.slice(0, 3) + part(3, 6, ".") + part(6, 9, ".") + part(9, 11, "-");
    }
    return n.slice(0, 2) + part(2, 5, ".") + part(5, 8, ".") + part(8, 12, "/") + part(12, 14, "-");
}

/** "CPF" para 11 caracteres, "CNPJ" para o resto. */
export function rotuloDocumento(value: string) {
    return normalizeDocumento(value).length === 11 ? "CPF" : "CNPJ";
}

export function isValidDocumento(value: string) {
    const n = normalizeDocumento(value);
    if (/^(.)\1+$/.test(n)) return false;
    if (n.length === 11) return /^\d{11}$/.test(n) && isValidCpf(n);
    if (n.length === 14) return /^[0-9A-Z]{12}\d{2}$/.test(n) && isValidCnpj(n);
    return false;
}

function isValidCpf(cpf: string) {
    const digit = (length: number) => {
        let sum = 0;
        for (let index = 0; index < length; index++) {
            sum += Number(cpf[index]) * (length + 1 - index);
        }
        const remainder = (sum * 10) % 11;
        return remainder === 10 ? 0 : remainder;
    };
    return digit(9) === Number(cpf[9]) && digit(10) === Number(cpf[10]);
}

// Valor de cada caractere = código ASCII - 48 (dá o mesmo resultado dos CNPJs numéricos antigos).
function isValidCnpj(cnpj: string) {
    const digit = (length: number) => {
        let sum = 0;
        let weight = length - 7;
        for (let index = 0; index < length; index++) {
            sum += (cnpj.charCodeAt(index) - 48) * weight;
            weight = weight === 2 ? 9 : weight - 1;
        }
        const remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    };
    return digit(12) === Number(cnpj[12]) && digit(13) === Number(cnpj[13]);
}
