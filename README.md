# Controle de Contratos CISBAF

O projeto foi migrado para uma arquitetura separada:

- `backend/`: Java 21, Spring Boot, Spring Security, JWT e JPA/MySQL;
- `frontend/`: Next.js App Router, TypeScript e Material UI (MUI);
- `src/`: código Django anterior, mantido temporariamente como referência para migração de dados.

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
- `GET|POST /api/users`, `PUT|DELETE /api/users/{id}`, `GET /api/users/me`;
- `GET|POST /api/sectors`, `PUT|DELETE /api/sectors/{id}`.

O frontend utiliza rotas BFF em `/api/[...path]`: o JWT não fica disponível ao JavaScript do navegador e é encaminhado internamente ao Spring como Bearer token.

## Desenvolvimento sem Docker

Backend (requer MySQL):

```bash
cd backend
mvn spring-boot:run
```

Frontend (requer Node.js 22):

```bash
cd frontend
npm install
npm run dev
```

Defina `BACKEND_INTERNAL_URL=http://localhost:8080/api` no ambiente do frontend.
