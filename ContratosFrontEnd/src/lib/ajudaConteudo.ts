// Conteúdo da página /dashboard/ajuda. Versão curta do MANUAL_USUARIO.md.
// Para mudar um texto, edite aqui: cada seção é um trecho em Markdown (negrito, listas e tabelas funcionam).
// Evite crases (`) dentro dos textos, porque eles ficam entre crases do TypeScript.

export type AjudaImagem = { src: string; legenda: string };
export type AjudaSecao = { id: string; titulo: string; texto: string; imagem?: AjudaImagem };
export type AjudaAba = { id: string; titulo: string; resumo: string; secoes: AjudaSecao[] };

export const ajudaAbas: AjudaAba[] = [
  {
    id: "comecar",
    titulo: "Primeiros passos",
    resumo: "Entrar no sistema, entender a tela de contratos e os status.",
    secoes: [
      {
        id: "entrar",
        titulo: "Entrar e sair",
        texto: `**Entrar:** abra o endereço do sistema (a TI informa), preencha **E-mail** e **Senha** e clique em **Entrar**.

**Esqueceu a senha?** Peça ao Controle Interno ou ao Administrador. Eles definem uma nova na tela Usuários.

**Sair:** clique no ícone **Sair**, ao lado do seu nome, no canto da tela.`,
      },
      {
        id: "perfis",
        titulo: "O que cada perfil vê",
        texto: `- **Fiscal:** vê só a tela **Contratos**, e só os contratos em que é fiscal.
- **Controle Interno e Administrador:** veem todos os contratos e um menu lateral com Contratos, Templates, Usuários, Setores, Notificações, Parâmetros, Auditoria e Documentos.

Todos os perfis têm esta página de **Ajuda**.`,
      },
      {
        id: "tela-contratos",
        titulo: "A tela de contratos",
        texto: `No topo ficam os cartões **Total de contratos**, **Contratos vigentes** e **Valor mensal**. Quando há contratos terminando nos próximos 60 dias, aparece um aviso.

Use **Buscar contrato, empresa, processo ou fiscal** para filtrar a lista e **Linhas por página** (10, 25 ou 50) para paginar.`,
        imagem: { src: "/ajuda/02-lista-contratos.png", legenda: "Lista de contratos, com a coluna de status (dados de teste)" },
      },
      {
        id: "status",
        titulo: "Os status do contrato",
        texto: `| Status | O que significa |
|---|---|
| **Em vigência** | Contrato normal, longe do fim. Nada a fazer. |
| **Aguardando e-mail de interesse** | Faltam 6 meses ou menos. Os fiscais geram e enviam o e-mail de interesse. |
| **E-mail enviado** | Todos os fiscais confirmaram o envio. Agora cada fiscal envia o seu parecer. |
| **Renovação aberta no SEI** | Todos os fiscais enviaram o parecer. O Controle Interno registra o Termo Aditivo quando ele for assinado. |
| **Vencido** (vermelho) | A data de fim passou e o aditivo não foi registrado. Avise o Controle Interno. |

O status muda sozinho por prazo (todo dia, à 01:00) e quando os fiscais completam cada etapa.

**O caminho de uma renovação:** Em vigência, depois Aguardando e-mail de interesse, depois E-mail enviado, depois Renovação aberta no SEI e, com o Termo Aditivo registrado, de volta a Em vigência com a nova data.`,
      },
      {
        id: "painel",
        titulo: "O painel do contrato",
        texto: `Clique em **Ver detalhes e ações** na linha do contrato. O painel mostra os dados e os botões de ação. Os botões que não servem para o status atual ficam em cinza, com a explicação.

Ações: **Gerar e-mail de interesse**, **Gerar parecer**, **Máscara externa ao prestador**, **Registrar Termo Aditivo** (só Controle Interno e Administrador), **Documentos gerados**, **Financeiro**, **Anexos**, **Notificações** e **Histórico do contrato**.`,
        imagem: { src: "/ajuda/03-detalhes-contrato.png", legenda: "Painel de detalhes e ações" },
      },
    ],
  },
  {
    id: "fiscal",
    titulo: "Fiscal",
    resumo: "As tarefas do fiscal, na ordem em que acontecem.",
    secoes: [
      {
        id: "email-interesse",
        titulo: "Enviar o e-mail de interesse",
        texto: `**Quando:** status **Aguardando e-mail de interesse**. O sistema **não envia** o e-mail: ele monta o texto, você envia pelo seu programa de e-mail e confirma aqui.

1. No painel, clique em **Gerar e-mail de interesse**. A janela mostra o texto e o Cc sugerido (os fiscais). Quem já confirmou aparece com etiqueta **verde**.
2. Clique em **Copiar texto**.
3. Cole no seu e-mail e envie.
4. Volte e clique em **Marcar como enviado** (só habilita depois de copiar).

**Cada fiscal** do contrato confirma o seu. O status só passa para E-mail enviado quando **todos** confirmarem.`,
        imagem: { src: "/ajuda/42b-email-interesse-fiscal-copiado.png", legenda: "E-mail de interesse, depois de copiar o texto" },
      },
      {
        id: "parecer",
        titulo: "Enviar o parecer",
        texto: `**Quando:** status **E-mail enviado**.

1. Clique em **Gerar parecer**. Em **Progresso dos fiscais** você vê quem já enviou.
2. Escreva a sua opinião no campo. A prévia se atualiza sozinha.
3. Clique em **Copiar template inteiro** e depois em **Enviar parecer** (só habilita depois de copiar e com a opinião preenchida).

Cada fiscal envia a **sua** opinião. Quando o **último** envia, o sistema gera o parecer técnico final em PDF e o contrato passa para Renovação aberta no SEI.`,
        imagem: { src: "/ajuda/43-parecer-fiscal.png", legenda: "Parecer técnico, na visão do fiscal" },
      },
      {
        id: "mascara",
        titulo: "Texto para o prestador",
        texto: `Disponível em qualquer status, exceto Em vigência. Clique em **Máscara externa ao prestador**, depois em **Copiar texto**, e envie ao prestador pelo seu e-mail. O sistema **não envia** e-mail ao prestador.`,
      },
      {
        id: "financeiro",
        titulo: "Lançar notas fiscais",
        texto: `1. No painel, clique em **Financeiro**. Os cartões mostram valor global, valor mensal, vigência e saldo do contrato.
2. Clique em **Novo lançamento** e preencha **Nº do processo**, **Nota fiscal** (não pode repetir no contrato), **Parcela** (de 1 até o número de meses da vigência), **Competência** (MM/AA ou MM/AAAA, dentro da vigência), **Valor da nota (R$)** e, se quiser, **Observações**. Clique em **Salvar**.
3. Na coluna **Ações** há **Editar** e **Excluir**. Tudo que for editado ou excluído fica no **Histórico**.

Um lançamento com a etiqueta **Fora da vigência** ficou fora das datas depois que a vigência foi alterada. Ele só pode ser editado depois de corrigido.`,
        imagem: { src: "/ajuda/07-financeiro.png", legenda: "Painel financeiro do contrato" },
      },
      {
        id: "ateste",
        titulo: "Gerar o ateste",
        texto: `No financeiro, clique no **primeiro ícone** da linha da nota (Gerar checklist, o ateste). Abre o **Ateste dos fiscais** com o **seu nome e o seu setor**.

Use **Copiar texto** (para colar no SEI ou no e-mail) ou **Baixar PDF**. Ao copiar, o ateste fica registrado em Documentos gerados.`,
        imagem: { src: "/ajuda/09-ateste.png", legenda: "Ateste dos fiscais" },
      },
      {
        id: "consultar",
        titulo: "Consultar o contrato",
        texto: `No painel do contrato:

- **Documentos gerados:** e-mail de interesse, parecer técnico e ateste, por versão. Use **Baixar PDF**.
- **Anexos:** lista de arquivos, com **Baixar**. O fiscal só consulta; anexar e remover é com o Controle Interno e o Administrador.
- **Notificações:** avisos automáticos de vencimento, com o status **Enviado**, **Falhou** (passe o mouse para ver o motivo) ou **Simulado** (envio real desligado no servidor).
- **Histórico do contrato:** linha do tempo com mudanças de status, documentos e anexos, sempre com quem fez e quando.`,
      },
    ],
  },
  {
    id: "gestao",
    titulo: "Controle Interno e Administrador",
    resumo: "Cadastros, Termo Aditivo, anexos, usuários, modelos e telas de consulta. Estes perfis também usam tudo da aba Fiscal, em qualquer contrato.",
    secoes: [
      {
        id: "acompanhar",
        titulo: "Acompanhar as renovações",
        texto: `Veja os status na lista de contratos. Em **Gerar e-mail de interesse** e **Gerar parecer** você acompanha quem já confirmou ou enviou (**Progresso dos fiscais**), mas não escreve nem confirma pelos fiscais.`,
      },
      {
        id: "cadastrar",
        titulo: "Cadastrar e editar contratos",
        texto: `1. Clique em **Novo contrato** (ou no ícone **Editar** da linha).
2. Preencha **Contrato**, **Número do processo**, **Objeto do contrato**, **Empresa**, **CNPJ/CPF**, **Nº Processo SEI**, **Início** e **Fim da vigência**, **Valor global**, **Valor mensal**, **Limite de prorrogação (meses)** (opcional), **Termo aditivo** (Sem TA para contrato novo), **Fonte de recurso** (opcional) e **Fiscais responsáveis** (pelo menos um; só aparecem usuários com perfil Fiscal).
3. Clique em **Salvar contrato**.

**Cuidado com as datas:** com a renovação em andamento (E-mail enviado ou Renovação aberta no SEI), só o **Administrador** altera as datas. Mudar o **fim da vigência** **cancela a renovação**: apaga as confirmações e os pareceres (documentos e anexos continuam). O sistema pede confirmação em **Salvar mesmo assim**. Se a nova vigência deixar notas fiscais de fora, a janela **Confirmar alterações** lista quais.`,
        imagem: { src: "/ajuda/16-novo-contrato.png", legenda: "Formulário de novo contrato" },
      },
      {
        id: "aditivo",
        titulo: "Registrar o Termo Aditivo",
        texto: `**Quando:** status **Renovação aberta no SEI** e aditivo assinado. Clique em **Registrar Termo Aditivo**. São três passos:

1. **Nova data:** informe a nova data de fim da vigência, posterior à atual e dentro do limite de prorrogação, se houver.
2. **Documento:** escolha o arquivo (PDF, DOC ou DOCX, até 30 MB).
3. **Confirmação:** confira os dados e clique em **Confirmar Termo Aditivo**.

**Não dá para desfazer:** o contrato volta para Em vigência com a nova data, o número do TA aumenta e as confirmações e os pareceres do ciclo anterior são zerados. Se a nova data estiver a menos de 6 meses de hoje, o contrato volta para Aguardando e-mail de interesse na atualização das 01:00. É o esperado.`,
        imagem: { src: "/ajuda/17-aditivo-3-confirmacao.png", legenda: "Termo Aditivo, passo de confirmação" },
      },
      {
        id: "anexos",
        titulo: "Gerenciar anexos",
        texto: `Em **Anexos**: **Adicionar arquivos** (até 5 por vez e 10 por contrato; PDF, DOC ou DOCX, até 30 MB cada), **Baixar** e **Remover**. Ao remover, o conteúdo é apagado de vez; fica registrado só que o arquivo existiu.

O anexo **Termo Aditivo** não pode ser excluído, só trocado pelo ícone **Substituir documento**. Data, status e número do TA não mudam, e a troca vai para a auditoria.`,
        imagem: { src: "/ajuda/12-anexos.png", legenda: "Anexos de um contrato" },
      },
      {
        id: "usuarios",
        titulo: "Usuários e setores",
        texto: `- **Usuários:** **Adicionar usuário** pede nome completo, e-mail, celular (opcional), setor, senha (mínimo 6 caracteres) e perfil. Para editar, use o lápis; **Nova senha (opcional)** troca a senha. O Controle Interno não cria nem altera Administradores.
- **Setores:** **Novo setor** e lápis para renomear.
- **Excluir** usuário ou setor é **só do Administrador**. Não dá para excluir quem é fiscal de um contrato, o próprio usuário, o único Administrador, nem um setor que ainda tenha usuários.`,
      },
      {
        id: "templates",
        titulo: "Editar os modelos dos documentos",
        texto: `Em **Templates**, escolha a aba (E-mail de interesse, Parecer técnico, Máscara para o fornecedor ou Ateste dos fiscais), edite o texto e clique em **Salvar**.

Para inserir dados do contrato use variáveis entre chaves duplas, como {{numero_contrato}}: clique numa variável da lista para copiá-la. A prévia usa dados de exemplo. A alteração vale para os documentos gerados dali em diante.`,
        imagem: { src: "/ajuda/23-templates.png", legenda: "Tela de templates" },
      },
      {
        id: "consulta",
        titulo: "Telas de consulta",
        texto: `- **Notificações:** avisos automáticos enviados todo dia às 08:00 (por padrão, 6 e 4 meses antes do fim) ao fiscal e ao Controle Interno. Tem totais de tentativas, enviados e falhas, busca e filtro por data.
- **Documentos:** todos os documentos gerados, de qualquer contrato, com filtros e **Baixar**.
- **Auditoria:** quem fez o quê e quando (criar, atualizar, excluir, gerar documento, anexar, remover anexo), com filtros por período, entidade, ação, contrato e usuário.`,
      },
      {
        id: "so-admin",
        titulo: "Só o Administrador",
        texto: `- **Parâmetros:** altere **Primeiro alerta (meses)** e **Segundo alerta (meses)** e clique em **Salvar**. Vale a partir da próxima verificação diária. O Controle Interno só consulta.
- **Excluir contrato:** ícone **Deletar contrato** e confirmação. É permanente; use só para contrato cadastrado por engano.
- Criar e editar Administradores, excluir usuários e setores, e alterar datas de contrato com a renovação em andamento.`,
      },
    ],
  },
  {
    id: "duvidas",
    titulo: "Dúvidas e permissões",
    resumo: "Respostas rápidas e o quadro de quem pode o quê.",
    secoes: [
      {
        id: "faq",
        titulo: "Dúvidas comuns",
        texto: `| Dúvida | Resposta |
|---|---|
| Não vejo um contrato. | O fiscal só vê os seus. Peça ao Controle Interno para conferir os Fiscais responsáveis do contrato. |
| O contrato não mudou de status. | O status só avança quando **todos** os fiscais concluíram a etapa. Veja quem falta. |
| Marcar como enviado ou Enviar parecer não habilita. | Primeiro clique em **Copiar texto** (ou **Copiar template inteiro**). No parecer, a opinião não pode estar vazia. |
| Errei o documento do Termo Aditivo. | O registro não se desfaz, mas o documento pode ser trocado em Anexos. |
| O sistema pediu login de novo. | A sessão tem prazo. Entre novamente; se acontecer toda hora, avise a TI. |
| Troquei a senha de alguém e ele continua logado. | A nova senha vale a partir do próximo login. |
| Quem procuro em caso de problema? | O setor de TI ou um chamado no Chamados TI. |`,
      },
      {
        id: "permissoes",
        titulo: "Quadro de permissões",
        texto: `| Tarefa | Fiscal | Controle Interno | Administrador |
|---|---|---|---|
| Ver os contratos em que é fiscal | sim | sim | sim |
| Ver todos os contratos | — | sim | sim |
| Cadastrar e editar contrato | — | sim | sim |
| Alterar datas com a renovação em andamento | — | — | sim |
| Excluir contrato | — | — | sim |
| Confirmar e-mail de interesse; enviar parecer | sim (nos seus) | — | — |
| Copiar a máscara para o prestador | sim | sim | sim |
| Registrar Termo Aditivo | — | sim | sim |
| Lançar, editar e excluir notas | sim (nos seus) | sim | sim |
| Gerar ateste; consultar documentos, notificações e histórico | sim | sim | sim |
| Adicionar e remover anexos | — | sim | sim |
| Telas Notificações, Documentos e Auditoria | — | sim | sim |
| Criar e editar usuários e setores; editar templates | — | sim | sim |
| Excluir usuário ou setor; criar Administrador | — | — | sim |
| Parâmetros: consultar / alterar | — / — | sim / — | sim / sim |`,
      },
    ],
  },
];
