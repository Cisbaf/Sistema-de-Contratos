// Espelham as regras do backend (ContractAttachmentService + application.properties).
// O backend continua sendo a garantia; aqui é só para avisar antes de enviar.
export const MAX_FILES = 5;
export const MAX_PER_CONTRACT = 10;
export const MAX_FILE_BYTES = 30 * 1024 * 1024;
export const MAX_TOTAL_BYTES = 90 * 1024 * 1024;
export const ALLOWED_EXTENSIONS = [".pdf", ".doc", ".docx"];

export function tamanho(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** Regras de um arquivo isolado (extensão, vazio, tamanho). Devolve a mensagem de erro ou null. */
export function validarArquivo(file: File): string | null {
  const name = file.name.toLowerCase();
  if (!ALLOWED_EXTENSIONS.some(ext => name.endsWith(ext))) {
    return `"${file.name}": apenas arquivos PDF, DOC e DOCX são permitidos.`;
  }
  if (file.size === 0) return `"${file.name}" está vazio.`;
  if (file.size > MAX_FILE_BYTES) return `"${file.name}" passa de ${tamanho(MAX_FILE_BYTES)}.`;
  return null;
}

export function validarArquivos(files: File[], jaAnexados: number): string | null {
  if (files.length > MAX_FILES) return `Selecione no máximo ${MAX_FILES} arquivos por vez.`;
  if (jaAnexados + files.length > MAX_PER_CONTRACT) {
    const vagas = Math.max(0, MAX_PER_CONTRACT - jaAnexados);
    return `Limite de ${MAX_PER_CONTRACT} anexos por contrato. Este contrato já tem ${jaAnexados} e comporta mais ${vagas}.`;
  }
  for (const file of files) {
    const problem = validarArquivo(file);
    if (problem) return problem;
  }
  if (files.reduce((sum, file) => sum + file.size, 0) > MAX_TOTAL_BYTES) {
    return `O total dos arquivos passa de ${tamanho(MAX_TOTAL_BYTES)}.`;
  }
  return null;
}
