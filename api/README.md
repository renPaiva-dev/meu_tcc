# ReciclaTHE — API e painel administrativo

Spring Boot 4 (Java 21+) · PostgreSQL 16 + PostGIS 3.5 · Flyway · Spring Security (JWT para o app, sessão para o painel) · Thymeleaf · Actuator · springdoc-openapi.

## Rodando localmente

```bash
# na raiz do projeto: sobe o PostgreSQL com PostGIS
docker compose up -d db

# na pasta api/: perfil "demo" = segredo JWT de desenvolvimento, admin padrão e 5 pontos FICTÍCIOS
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo      # Windows: mvnw.cmd ...
```

O Maven Wrapper (`mvnw`) baixa o Maven 3.9.9 sozinho; só é preciso ter o JDK 21 (`JAVA_HOME`).

- Painel: http://localhost:8080/admin (no perfil demo: `admin@reciclathe.local` / `admin123`)
- API: http://localhost:8080/api/v1/... · documentação: http://localhost:8080/swagger-ui.html
- Saúde (para o orquestrador/balanceador): http://localhost:8080/actuator/health

### Com Docker (API + banco)

```bash
# na raiz do projeto, com JWT_SEGREDO e ADMIN_SENHA no ambiente ou num .env (não versionado)
docker compose up -d --build
```

A imagem (`api/Dockerfile`) é multi-stage: compila com Maven e roda só com o JRE 21, como usuário sem
privilégios. As fotos ficam no volume `fotos` (`/dados/fotos`).

### Variáveis de ambiente

| Variável | Para quê |
|---|---|
| `JWT_SEGREDO` | Segredo HS256 dos tokens (mín. 32 caracteres aleatórios). **Obrigatória** |
| `ADMIN_EMAIL` / `ADMIN_SENHA` | Cria o primeiro administrador na inicialização |
| `DB_URL` / `DB_USUARIO` / `DB_SENHA` | Conexão com o PostgreSQL |
| `FOTOS_DIR` | Pasta onde as fotos enviadas são guardadas (fora da pasta pública) |
| `COOKIE_SEGURO` | `true` quando o painel estiver atrás de HTTPS (cookie de sessão com `Secure`). Padrão `false` |
| `LIMITE_TENTATIVAS` | Tentativas de login/cadastro por IP por minuto. Padrão 10 |
| `DOCS_API` | `false` desliga o Swagger UI e o `/v3/api-docs`. Padrão `true` |

Atrás de um proxy reverso (nginx, Traefik), a API usa os cabeçalhos `X-Forwarded-*`
(`server.forward-headers-strategy=framework`) para saber o IP real, o esquema e o host.
Outros ajustes ficam em `reciclathe.limites.*` e `reciclathe.fotos.tamanho-maximo` no `application.yml`.

## Testes

```bash
./mvnw test   # precisa de Docker: os testes sobem um PostGIS real com Testcontainers
```

## Organização (pacotes por funcionalidade)

| Pacote | Responsabilidade |
|---|---|
| `usuario` | Cadastro, login (JWT), perfil, exclusão de conta (LGPD) |
| `material` | Materiais (cores CONAMA 275) e orientações de descarte |
| `zona` | Zonas da coleta seletiva, telefones do Disk Coleta, bairros (GeoJSON) e localização da zona por coordenada |
| `ponto` | Pontos de coleta, busca por proximidade (PostGIS), sugestões, check-in por GPS |
| `identificacao` | Registro das classificações feitas no celular e feedback (acertou/errou) |
| `pontuacao` | Eventos de pontuação, limites diários, ranking |
| `admin` | Telas do painel (Thymeleaf): indicadores, pontos, orientações, zonas/bairros, feedback do modelo, usuários |
| `comum` / `config` | Erros (RFC 9457), armazenamento de fotos, segurança, limite de tentativas, propriedades |

## Endpoints da API

Erros seguem RFC 9457 (`application/problem+json`); erros de validação trazem `campos`. Códigos usados:
400 (validação), 401 (sem token, token inválido/de outro emissor, conta excluída ou bloqueada), 403 (login de
conta bloqueada), 404, 409 (e-mail já cadastrado ou ação repetida em paralelo), 422 (regra de negócio),
429 (muitas tentativas de login/cadastro, com `Retry-After`). O painel mostra páginas de erro em HTML.

| Método e rota | Auth | Descrição |
|---|---|---|
| `POST /api/v1/auth/cadastro` | — | Cria conta de cidadão (senha com 8+ caracteres) e devolve token |
| `POST /api/v1/auth/login` | — | Devolve token |
| `GET/PATCH/DELETE /api/v1/me` | JWT | Perfil, troca de zona, exclusão de conta |
| `GET /api/v1/me/historico` | JWT | Identificações e pontos recentes |
| `GET /api/v1/materiais` | — | Materiais + orientações (cache offline do app) |
| `GET /api/v1/zonas` | — | Zonas e telefones do Disk Coleta |
| `GET /api/v1/zonas/localizar?lat&lon` | — | Zona/bairro da coordenada (404 se fora dos bairros importados) |
| `GET /api/v1/pontos?lat&lon&raioKm&material&limite` | — | Pontos próximos (raio até 50 km, 1 a 200 pontos); sem `lat/lon` lista todos (cache offline) |
| `POST /api/v1/pontos/sugestoes` | JWT | Sugere ponto (multipart, foto opcional); entra como PENDENTE; até 10 pendentes por usuário |
| `POST /api/v1/pontos/{id}/checkin` | JWT | Check-in, aceito a até 150 m do ponto, 1 por ponto por dia e até 5 por dia no total |
| `POST /api/v1/identificacoes` | JWT | Registra uma classificação feita no aparelho |
| `POST /api/v1/identificacoes/{id}/feedback` | JWT | Acertou/errou; foto só é guardada com consentimento |
| `GET /api/v1/ranking?zona=` | — | Top 10 de cidadãos ativos (nome abreviado); zona inexistente dá 404 |

## Telas do painel

| Tela | O que faz |
|---|---|
| Painel | Indicadores do período, materiais por zona, acurácia do modelo em uso |
| Pontos de coleta | Lista paginada com filtro por situação; importação do OSM; no formulário: quem sugeriu, motivo da recusa, quem revisou e quando, botões de aprovar/recusar |
| Orientações | Textos de preparo/descarte de cada material |
| Zonas | Telefone do Disk Coleta, importação de bairros (GeoJSON, até 20 MB), lista e exclusão de bairros |
| Feedback do modelo | Erros, acertos ou todos, com filtro "só com foto" (dados rotulados para retreino), paginado |
| Usuários | Lista paginada, criação de outro administrador, bloqueio/desbloqueio de contas |
| Minha senha | Troca da senha do administrador logado (mínimo 8 caracteres) |

## Decisões que valem citar no relatório

- **Pontuação só para ações validáveis**: sugestão aprovada (20, uma única vez por ponto, mesmo que seja recusada e aprovada depois), check-in confirmado por GPS (5, um por ponto por dia e no máximo 5 por dia no total), feedback (2, até 5 por dia). Foto não pontua, porque não prova o descarte. O feedback pontua igual para "certo" e "errado", para não incentivar respostas falsas. As regras "uma vez" têm também índices únicos no banco (coluna `dia` em `evento_pontuacao`), e os limites diários travam a linha do usuário (`SELECT ... FOR UPDATE`), para que requisições simultâneas não os furem. Administradores e contas bloqueadas não entram no ranking.
- **Privacidade (LGPD)**: a foto não sai do celular na identificação; só é enviada no feedback com consentimento explícito. A localização é guardada só como zona, nunca como coordenada. O ranking mostra só o primeiro nome + inicial. A exclusão de conta apaga dados pessoais e fotos (inclusive as dos pontos que o usuário sugeriu, que perdem o vínculo com o autor) e anonimiza as identificações, preservando as estatísticas agregadas. Os arquivos só são apagados do disco depois do commit, e uma foto gravada numa transação desfeita é removida.
- **Coluna geográfica gerada** (`localizacao`) a partir de latitude/longitude, com índice GIST: a aplicação não precisa de Hibernate Spatial.
- **Bairros importáveis**: os polígonos não vêm embutidos. O administrador importa um GeoJSON público e, enquanto isso não acontece, o app usa a zona escolhida pelo usuário.
- **Importação do OpenStreetMap** (botão no painel, em Pontos de coleta): consulta via API Overpass os elementos `amenity=recycling` e `amenity=waste_transfer_station` dentro do limite administrativo de Teresina. As etiquetas `recycling:*` viram materiais, e `recycling_type=centre` vira "Centro de reciclagem / sucata". Tudo entra como PENDENTE, porque a maioria dos pontos no OSM não tem nome nem materiais, e um ponto só pode ser aprovado com materiais informados. Reimportar atualiza os pendentes e preserva os já revisados (chave: `osm_tipo` + `osm_id`); pontos do OSM excluídos pelo administrador vão para a tabela `osm_ignorado` e não voltam. A consulta ao Overpass roda fora da transação do banco. Os servidores Overpass públicos vivem sobrecarregados, então a lista em `reciclathe.osm.overpass-urls` é tentada em ordem. Licença dos dados: ODbL, com atribuição "© colaboradores do OpenStreetMap".
- **Dados reais com fonte**: cada ponto de coleta e cada orientação tem campo de fonte. Os pontos do perfil demo são fictícios e marcados com `[DEMO]`.
- **Segurança**: o token JWT é validado quanto a assinatura, validade, emissor (`iss=reciclathe`) e se a conta ainda existe e está ativa (uma consulta por requisição autenticada: bloqueio e exclusão valem na hora). Login e cadastro têm limite de tentativas por IP em memória (por instância). Fotos são aceitas pela assinatura do arquivo (JPEG/PNG/WebP), não pelo `Content-Type`, com até 5 MB.
- **Auditoria**: aprovação e recusa de pontos guardam quem revisou e quando (`revisado_por`, `revisado_em`).
- **Desempenho**: as associações de `PontoColeta` são LAZY e cada consulta busca o que vai usar (`@EntityGraph`), evitando N+1; `open-in-view` fica desligado.
