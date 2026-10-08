# ReciclaTHE — API e painel administrativo

Spring Boot 4 (Java 21+) · PostgreSQL 16 + PostGIS 3.5 · Flyway · Spring Security (JWT para o app, sessão para o painel) · Thymeleaf.

## Rodando localmente

```bash
# na raiz do projeto: sobe o PostgreSQL com PostGIS
docker compose up -d db

# na pasta api/: perfil "demo" = segredo JWT de desenvolvimento, admin padrão e 5 pontos FICTÍCIOS
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

- Painel: http://localhost:8080/admin (no perfil demo: `admin@reciclathe.local` / `admin123`)
- API: http://localhost:8080/api/v1/...

Em produção, sem o perfil demo, defina as variáveis:

| Variável | Para quê |
|---|---|
| `JWT_SEGREDO` | Segredo HS256 dos tokens (mín. 32 caracteres aleatórios). **Obrigatória** |
| `ADMIN_EMAIL` / `ADMIN_SENHA` | Cria o primeiro administrador na inicialização |
| `DB_URL` / `DB_USUARIO` / `DB_SENHA` | Conexão com o PostgreSQL |
| `FOTOS_DIR` | Pasta onde as fotos enviadas são guardadas (fora da pasta pública) |

## Testes

```bash
mvn test   # precisa de Docker: os testes sobem um PostGIS real com Testcontainers
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
| `admin` | Telas do painel (Thymeleaf) e indicadores agregados |
| `comum` / `config` | Erros (RFC 9457), armazenamento de fotos, segurança, propriedades |

## Endpoints da API

Erros seguem RFC 9457 (`application/problem+json`); erros de validação trazem `campos`.

| Método e rota | Auth | Descrição |
|---|---|---|
| `POST /api/v1/auth/cadastro` | — | Cria conta de cidadão e devolve token |
| `POST /api/v1/auth/login` | — | Devolve token |
| `GET/PATCH/DELETE /api/v1/me` | JWT | Perfil, troca de zona, exclusão de conta |
| `GET /api/v1/me/historico` | JWT | Identificações e pontos recentes |
| `GET /api/v1/materiais` | — | Materiais + orientações (cache offline do app) |
| `GET /api/v1/zonas` | — | Zonas e telefones do Disk Coleta |
| `GET /api/v1/zonas/localizar?lat&lon` | — | Zona/bairro da coordenada (404 se fora dos bairros importados) |
| `GET /api/v1/pontos?lat&lon&raioKm&material` | — | Pontos próximos; sem `lat/lon` lista todos (cache offline) |
| `POST /api/v1/pontos/sugestoes` | JWT | Sugere ponto (multipart, foto opcional); entra como PENDENTE |
| `POST /api/v1/pontos/{id}/checkin` | JWT | Check-in, aceito a até 150 m do ponto, 1 por dia |
| `POST /api/v1/identificacoes` | JWT | Registra uma classificação feita no aparelho |
| `POST /api/v1/identificacoes/{id}/feedback` | JWT | Acertou/errou; foto só é guardada com consentimento |
| `GET /api/v1/ranking?zona=` | — | Top 10 (nome abreviado) |

## Decisões que valem citar no relatório

- **Pontuação só para ações validáveis**: sugestão aprovada (20), check-in confirmado por GPS (5, um por ponto por dia), feedback (2, até 5 por dia). Foto não pontua, porque não prova o descarte. O feedback pontua igual para "certo" e "errado", para não incentivar respostas falsas.
- **Privacidade (LGPD)**: a foto não sai do celular na identificação; só é enviada no feedback com consentimento explícito. A localização é guardada só como zona, nunca como coordenada. O ranking mostra só o primeiro nome + inicial. A exclusão de conta apaga dados pessoais e fotos e anonimiza as identificações, preservando as estatísticas agregadas.
- **Coluna geográfica gerada** (`localizacao`) a partir de latitude/longitude, com índice GIST: a aplicação não precisa de Hibernate Spatial.
- **Bairros importáveis**: os polígonos não vêm embutidos. O administrador importa um GeoJSON público e, enquanto isso não acontece, o app usa a zona escolhida pelo usuário.
- **Importação do OpenStreetMap** (botão no painel, em Pontos de coleta): consulta via API Overpass os elementos `amenity=recycling` e `amenity=waste_transfer_station` dentro do limite administrativo de Teresina. As etiquetas `recycling:*` viram materiais, e `recycling_type=centre` vira "Centro de reciclagem / sucata". Tudo entra como PENDENTE, porque a maioria dos pontos no OSM não tem nome nem materiais, e um ponto só pode ser aprovado com materiais informados. Reimportar atualiza os pendentes e preserva os já revisados (chave: `osm_tipo` + `osm_id`). Os servidores Overpass públicos vivem sobrecarregados, então a lista em `reciclathe.osm.overpass-urls` é tentada em ordem. Licença dos dados: ODbL, com atribuição "© colaboradores do OpenStreetMap".
- **Dados reais com fonte**: cada ponto de coleta e cada orientação tem campo de fonte. Os pontos do perfil demo são fictícios e marcados com `[DEMO]`.
