package contratos.exception;

/**
 * Falha ao ler o arquivo de um anexo no disco (arquivo sumido, corrompido ou pasta inacessível). Vira 500 com
 * mensagem genérica: o detalhe e o caminho ficam só no log do servidor.
 */
public class AttachmentStorageException extends RuntimeException {

    public AttachmentStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
