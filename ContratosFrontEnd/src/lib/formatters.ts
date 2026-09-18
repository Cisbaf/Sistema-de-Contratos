export function formatCnpj(value: string) {

    const cleaned = value.replace(/\D/g, '').slice(0, 14);

    return cleaned
        .replace(/^(\d{2})(\d)/, "$1.$2")
        .replace(/^(\d{2})\.(\d{3})(\d)/, "$1.$2.$3")
        .replace(/\.(\d{3})(\d)/, ".$1/$2")
        .replace(/(\d{4})(\d{1,2})$/, "$1-$2");

}

export function isValidCnpj(value: string) {
    const cnpj = value.replace(/\D/g, "");

    if (cnpj.length !== 14 || /^(\d)\1{13}$/.test(cnpj)) return false;

    const digit = (length: number) => {
        let sum = 0;
        let weight = length - 7;

        for (let index = 0; index < length; index++) {
            sum += Number(cnpj[index]) * weight;
            weight = weight === 2 ? 9 : weight - 1;
        }

        const remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    };

    return digit(12) === Number(cnpj[12]) && digit(13) === Number(cnpj[13]);
}
