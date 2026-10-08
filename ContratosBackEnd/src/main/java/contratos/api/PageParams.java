package contratos.api;

public class PageParams {

    private static final int MAX_PAGE_SIZE = 100;

    static void validate(int page, int size){
        if (page < 0){
            throw new IllegalArgumentException("O número da página não pode ser negativo");
        }
        if(size < 1){
            throw new IllegalArgumentException("O tamanho da página deve ser pelo menos 1");
        }
        if (size > MAX_PAGE_SIZE){
            throw new IllegalArgumentException("O tamanho máximo da página é " + MAX_PAGE_SIZE);
        }
    }
}
