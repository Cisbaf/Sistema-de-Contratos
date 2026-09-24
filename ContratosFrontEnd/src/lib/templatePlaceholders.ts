import type { DocumentTemplateType } from "@/types";

const SAMPLE_VALUES: Record<string, string> = {
  numero_contrato: "0042/2026",
  objeto_contrato: "Prestação de serviços de manutenção predial",
  numero_processo: "SEI 2026.0001.0042",
  empresa: "Fornecedora Exemplo LTDA",
  cnpj: "12.345.678/0001-90",
  data_termino: "15/03/2027",
  nomes_fiscais: "Maria Souza e João Pereira",
  nome_fiscal: "Maria Souza",
  data_inicio: "15/03/2026",
  mes_referencia: "SETEMBRO 2026",
  numero_nota_fiscal: "174",
  assinaturas_fiscais: "**Maria Souza**  \n*Departamento de Engenharia*\n\n**João Pereira**  \n*Setor de Compras*",
  observacoes: "Contrato cumprido dentro do prazo, sem pendências registradas.",
};

export const TEMPLATE_TYPE_LABELS: Record<DocumentTemplateType, string> = {
  INTEREST_EMAIL: "E-mail de interesse",
  TECHNICAL_OPINION: "Parecer técnico",
  SUPPLIER_RENEWAL_EMAIL: "Máscara para o fornecedor",
  PAYMENT_CHECKLIST: "Ateste dos fiscais (checklist)",
};

// Espelha o catálogo de `allowedVariables` do DocumentTemplateService no backend.
// Se o catálogo mudar lá, atualizar aqui também.
export const TEMPLATE_TYPE_VARIABLES: Record<DocumentTemplateType, string[]> = {
  INTEREST_EMAIL: [
    "numero_contrato",
    "objeto_contrato",
    "numero_processo",
    "empresa",
    "cnpj",
    "data_termino",
    "nomes_fiscais",
  ],
  TECHNICAL_OPINION: [
    "numero_contrato",
    "objeto_contrato",
    "numero_processo",
    "empresa",
    "cnpj",
    "data_termino",
    "nomes_fiscais",
    "observacoes",
  ],
  SUPPLIER_RENEWAL_EMAIL: [
    "numero_contrato",
    "empresa",
    "cnpj",
    "objeto_contrato",
    "data_termino",
    "numero_processo",
    "nome_fiscal",
  ],
  PAYMENT_CHECKLIST: [
    "numero_contrato",
    "data_inicio",
    "data_termino",
    "mes_referencia",
    "numero_nota_fiscal",
    "assinaturas_fiscais",
  ],
};

export function fillPlaceholdersWithSampleData(content: string): string {
  return content.replace(/\{\{((?:\\?\w)+)}}/g, (match, rawVariable: string) => {
    const variable = rawVariable.replace(/\\/g, "");
    return SAMPLE_VALUES[variable] ?? match;
  });
}

// Mantém a prévia consistente com a geração de documentos no backend. O
// editor visual escapa caracteres de Markdown para preservá-los como texto,
// mas, nesta tela, o conteúdo deve ser exibido já formatado.
export function prepareTemplatePreview(content: string): string {
  return fillPlaceholdersWithSampleData(content)
    .replace(/\\([\\`*_{}\[\]()#+\-.!>])/g, "$1");
}
