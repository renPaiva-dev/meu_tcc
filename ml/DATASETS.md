# Datasets utilizados

Todos públicos, baixados sem credenciais, com licença explícita que permite uso
acadêmico (com atribuição). **Nenhuma imagem foi coletada pelo aluno nem obtida por
scraping.** Download automatizado em `src/baixar_dados.py`; checksums SHA-256 dos
arquivos baixados ficam em `dados/brutos/downloads.json` (gerado no download).

| Dataset | Fonte / URL | Licença | Imagens originais | Amostras usadas* | Domínio visual |
|---|---|---|---|---|---|
| **TrashNet** | Hugging Face `garythung/trashnet` (arquivo `dataset-resized.zip`, revisão `94cd17e`) — https://huggingface.co/datasets/garythung/trashnet | MIT | 2.527 | 2.512 | objeto único sobre cartolina branca, luz controlada (iPhone, 512×384) |
| **RealWaste** | UCI Machine Learning Repository, id 908 — https://archive.ics.uci.edu/dataset/908/realwaste (DOI 10.24432/C5SS4G) | CC BY 4.0 | 4.752 | 4.744 | resíduo real na recepção de um aterro (Wollongong, Austrália), 524×524 |
| **TACO** | Zenodo — https://doi.org/10.5281/zenodo.3587843 (TACO.zip, v1.0) | CC BY 4.0 (dataset no Zenodo); código do repositório: MIT | 1.500 fotos / 4.784 anotações | 3.073 recortes | lixo "no ambiente" (ruas, praias, mato), fotos de celular |
| **GIZ e-waste (COCO)** | Hugging Face `GIZ/e-waste-dataset-COCO-labels` (revisão registrada em `dados/brutos/giz_ewaste/REVISAO.txt`) — https://huggingface.co/datasets/GIZ/e-waste-dataset-COCO-labels | CC BY 4.0 | 752 fotos / 1.790 anotações | 1.762 recortes | eletroeletrônicos de grande porte em ferros-velhos de Gana (out/2025) |

\* após mapeamento, filtro de recortes pequenos e deduplicação (ver abaixo). Total final: **12.091** amostras.

## Citações

- **TrashNet** — THUNG, G.; YANG, M. *Classification of Trash for Recyclability Status*. CS229 Project Report, Stanford University, 2016. Dataset: https://github.com/garythung/trashnet.
- **RealWaste** — SINGLE, S.; IRANMANESH, S.; RAAD, R. RealWaste: A Novel Real-Life Data Set for Landfill Waste Classification Using Deep Learning. *Information*, v. 14, n. 12, p. 633, 2023. https://www.mdpi.com/2078-2489/14/12/633. Dataset: UCI Machine Learning Repository, https://doi.org/10.24432/C5SS4G (Wollongong City Council, CC BY 4.0).
- **TACO** — PROENÇA, P. F.; SIMÕES, P. TACO: Trash Annotations in Context for Litter Detection. arXiv:2003.06975, 2020. Dataset: https://doi.org/10.5281/zenodo.3587843.
- **GIZ e-waste** — Deutsche Gesellschaft für Internationale Zusammenarbeit (GIZ). *e-waste dataset with COCO labels*. Hugging Face, 2025. https://huggingface.co/datasets/GIZ/e-waste-dataset-COCO-labels (CC BY 4.0).

CC BY 4.0 exige atribuição: as citações acima devem constar no TCC (e, se o modelo
for distribuído, num arquivo de créditos do app).

## Mapeamento para as 7 classes do ReciclaTHE

Implementado em `src/mapeamento.py`. "—" = descartado.

| Classe ReciclaTHE | TrashNet | RealWaste | TACO (categorias) | GIZ |
|---|---|---|---|---|
| **PLASTICO** | plastic | Plastic | Clear/Other plastic bottle, Plastic bottle cap, Plastic lid, Plastic film, Other plastic wrapper, Single-use carrier bag, Garbage bag, Polypropylene bag, Six pack rings, Disposable plastic cup, Other plastic cup, Disposable food container, Other plastic container, Spread tub, Tupperware, Plastic utensils, Plastic straw, Other plastic | — |
| **PAPEL** | paper, cardboard | Paper, Cardboard | Normal paper, Magazine paper, Wrapping paper, Paper bag, Corrugated carton, Egg carton, Meal carton, Other carton, Pizza box, Toilet tube | — |
| **METAL** | metal | Metal | Drink can, Food Can, Metal bottle cap, Metal lid, Pop tab, Aluminium foil, Scrap metal | — |
| **VIDRO** | glass | Glass | Glass bottle, Glass jar, Glass cup, Broken glass | — |
| **ELETRONICO** | — | — | Battery (apenas 2 recortes) | Computers, Laptops, TV, ACs, Compressors, Microwave, Fridges |
| **ORGANICO** | — | Food Organics, Vegetation | Food waste (7) | — |
| **REJEITO** | trash | Miscellaneous Trash, **Textile Trash** | Cigarette, Tissues, Crisp packet, Rope & strings, Shoe, Plastic glooves | — (`unsure` descartado) |
| *descartado* | — | — | Unlabeled litter, Drink carton (longa vida), Paper cup, Foam cup, Foam food container, Styrofoam piece, Aerosol, Aluminium/Carded blister pack, Squeezable tube, Paper straw | unsure |

Decisões e justificativas:

- **Papelão → PAPEL**: na coleta seletiva (e nos PEVs) papel e papelão vão juntos.
- **Têxtil → REJEITO**: Teresina não possui coleta seletiva de têxteis; sem ponto de entrega
  específico, o descarte padrão é o rejeito (o app pode sugerir doação quando em bom estado).
  Alternativa documentada: excluir `Textile Trash` (318 imagens) — muda a composição de REJEITO.
- **Lenço de papel, embalagem metalizada (salgadinho), bituca, luva** → REJEITO, pois não são
  recicláveis na prática.
- **Itens ambíguos foram descartados** em vez de forçados numa classe: longa vida (multicamada,
  aceitação varia), copo de papel (filme plástico), isopor (aceitação varia entre cooperativas),
  aerossol (resíduo especial), blister, bisnaga e lixo sem rótulo de material.
- **Recortes TACO/GIZ**: cada bounding box vira uma amostra, com 10% de margem de contexto;
  caixas com lado < 48 px na imagem original são descartadas (isso remove a maioria das bitucas,
  514 de 667). Recortes da mesma foto ficam sempre no mesmo split (sem vazamento).

## Lacunas conhecidas (honestas)

- **ELETRONICO** vem quase só do GIZ: TVs, geladeiras, ar-condicionado, micro-ondas, computadores
  e notebooks **em ferro-velho em Gana**. **Não há pilhas/baterias, celulares, carregadores,
  cabos ou lâmpadas** em quantidade (TACO tem só 2 baterias). O modelo, portanto, **não foi
  treinado para reconhecer pilhas e pequenos eletrônicos**, que são justamente os mais comuns
  no descarte doméstico. Além disso, por vir de um único domínio, ELETRONICO pode estar
  parcialmente associado ao *fundo* (ferro-velho) e não ao objeto.
- **ORGANICO** vem quase só do RealWaste (aterro australiano): 846 imagens + 7 recortes do TACO.
- **TACO** é dominado por PLASTICO (~59% dos recortes).
- Nenhuma imagem é de Teresina/Brasil.

## Avaliados e não usados

| Fonte | Motivo |
|---|---|
| `viola77data/recycling-dataset` (HF, declara Apache-2.0; tem "batteries") | imagens obtidas por *scraping* do DuckDuckGo — a licença declarada não cobre os direitos autorais das fotos originais |
| `khoaliamle/Garbage_Classification_YOLO` (HF, declara MIT; tem "battery") | aparenta ser redistribuição do "Garbage Classification" do Kaggle (imagens coletadas da web); proveniência/licença das fotos incerta |
| `edwinpalegre/trashnet_enhanced` (HF) | sem licença declarada; ~4 GB |
| `dmedhi/garbage-image-classification-detection` (HF/Roboflow, CC BY 4.0) | classes redundantes com TrashNet; não adiciona ELETRONICO/ORGANICO — candidato a uso futuro |
| `GIZ/E-Waste-Database` (HF, CC BY 4.0) | mesmo domínio do GIZ COCO, porém 17 GB sem anotações por objeto |
| Kaggle (ex.: "Garbage Classification" 12 classes, com *battery*; "E-Waste Image Dataset") | exige credencial; além disso, vários são compilações da web com licença das imagens incerta. **Opção futura** se a licença for verificada |

Nenhuma fonte pública verificada com licença clara forneceu um conjunto razoável de
**pilhas/baterias** ou **pequenos eletrônicos**; recomenda-se, como trabalho futuro,
buscar parceria/autorização para usar imagens de PEVs de Teresina, ou um dataset com licença clara.
