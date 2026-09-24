/**
 * Copia o conteúdo já renderizado de um elemento COM formatação (negrito, listas, tabelas...).
 * Gmail e o editor do SEI não entendem Markdown: se colassem o texto cru, apareceriam `**` e `#`.
 * Ambos aceitam colar texto formatado, então copiamos o HTML renderizado (mais uma versão em texto simples).
 */
export async function copyRenderedContent(element: HTMLElement): Promise<void> {
  const html = element.innerHTML;
  const plain = element.innerText;

  // navigator.clipboard só existe em contexto seguro (https ou localhost); pelo IP da rede interna (http) não existe.
  try {
    if (navigator.clipboard && typeof ClipboardItem !== "undefined") {
      await navigator.clipboard.write([
        new ClipboardItem({
          "text/html": new Blob([html], { type: "text/html" }),
          "text/plain": new Blob([plain], { type: "text/plain" }),
        }),
      ]);
      return;
    }
  } catch {
    // segue para o plano B
  }

  // Plano B: seleciona o conteúdo e usa o copiar do navegador (mesmo efeito de selecionar e apertar Ctrl+C).
  const selection = window.getSelection();
  const range = document.createRange();
  range.selectNodeContents(element);
  selection?.removeAllRanges();
  selection?.addRange(range);
  const ok = document.execCommand("copy");
  selection?.removeAllRanges();
  if (!ok) throw new Error("Não foi possível copiar");
}
