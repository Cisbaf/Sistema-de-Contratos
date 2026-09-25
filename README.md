# Controle de Contratos CISBAF

O projeto está sendo evoluído para uma arquitetura separada:

- `ContratosBackEnd/`: Java 21, Spring Boot, Spring Security, JWT, JPA/MySQL, Lombok e Springdoc OpenAPI;
- `ContratosFrontEnd/`: Next.js App Router, TypeScript e Material UI (MUI);
- `src/`: código Django anterior, mantido temporariamente como referência para migração de dados.

A referência funcional oficial é `especificacao_sistema_cisbaf.md`. O planejamento técnico está em `PLANO_EVOLUCAO_CISBAF.md`, e o estado atual do projeto (módulos concluídos, decisões e próximo passo) em `CONTEXTO_IA.md`. Situação em 25/09/2026: módulos de contratos, renovação/documentos, anexos e financeiro (com o ateste dos fiscais) concluídos; próximo módulo: notificações automáticas. Esses documentos de planejamento não são versionados neste repositório.

## Executar com Docker

```bash
cp .env.example .env
docker compose up --build
```

Abra `http://localhost:3000`. As credenciais iniciais padrão são `admin@cisbaf.org.br` / `admin123`; altere-as no `.env` antes de usar em produção.

## Endpoints

O backend segue o mesmo tratamento do projeto Troca de Plantão: todos os endpoints ficam sob `/api`, usam respostas HTTP REST e autenticação stateless com JWT no cookie HTTP-only `auth_token` ou no header `Authorization: Bearer`.

- `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/validate`;
- `GET|POST /api/contracts`, `GET|PUT|DELETE /api/contracts/{id}`, `GET /api/contracts/mine`;
- renovação: `/api/contracts/{id}/interest-email/*`, `/api/contracts/{id}/technical-opinion/*`, `GET /api/contracts/{id}/supplier-mask/preview`;
- templates e documentos: `/api/document-templates`, `/api/generate-document` (`/history`, `/download`);
- anexos do contrato: `/api/attachment/*`;
- financeiro: `/api/contracts/{id}/lancamentos` (`/saldo`, `/historico`), `PUT|DELETE /api/lancamentos/{id}`, `/api/lancamentos/{id}/checklist` (`/preview`);
- `GET|POST /api/users`, `PUT|DELETE /api/users/{id}`, `GET /api/users/me`;
- `GET|POST /api/sectors`, `PUT|DELETE /api/sectors/{id}`.

O frontend utiliza rotas BFF em `/api/[...path]`: o JWT não fica disponível ao JavaScript do navegador e é encaminhado internamente ao Spring como Bearer token.

## OpenAPI e Swagger

O backend possui Springdoc OpenAPI. Rotas padrão:

- especificação JSON: `http://localhost:8080/v3/api-docs`;
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`.

Essas rotas ainda devem ser alinhadas à cadeia do Spring Security. Até essa decisão, podem exigir autenticação. A exposição em produção também precisa ser definida.

## Desenvolvimento sem Docker

Backend (requer MySQL):

```bash
cd ContratosBackEnd
mvn spring-boot:run
```

Frontend (requer Node.js 22):

```bash
cd ContratosFrontEnd
npm install
npm run dev
```

Defina `BACKEND_INTERNAL_URL=http://localhost:8080/api` no ambiente do frontend.
