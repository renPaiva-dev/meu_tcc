# Resultados do classificador — ReciclaTHE

Todos os números abaixo vêm dos arquivos em [resultados/](resultados/). Nenhum foi estimado.
Datasets, licenças e mapeamento de classes estão em [DATASETS.md](DATASETS.md).

## 1. Configuração

| Item | Valor |
|---|---|
| Arquitetura | MobileNetV3-Large, pesos ImageNet (torchvision), *fine-tuning* completo |
| Entrada | 224×224 RGB, `Resize((224, 224))` sem recorte, normalização ImageNet |
| Classes (7) | PLASTICO, PAPEL, METAL, VIDRO, ELETRONICO, ORGANICO, REJEITO |
| Dados | TrashNet + RealWaste + TACO (recortes) + GIZ e-waste (recortes) |
| Divisão | 70/15/15 estratificada **por dataset**: 8.462 treino, 1.809 validação, 1.820 teste |
| Seleção do modelo | Early stopping pelo F1-macro de validação (melhor época: 16, F1-macro val = 0,922) |
| Hardware de treino | NVIDIA Tesla T4 (Google Colab, `treinar_colab.ipynb`), 1.541 s no total |

Distribuição de classes por dataset e split: [resultados/distribuicao.md](resultados/distribuicao.md).

## 2. Desempenho no conjunto de teste (modelo final, FP32)

**Acurácia 90,3% · F1-macro 0,908** (n = 1.820)

| Classe | Precisão | Revocação | F1 | Suporte |
|---|---|---|---|---|
| PLASTICO | 0,882 | 0,840 | 0,860 | 488 |
| PAPEL | 0,918 | 0,915 | 0,916 | 341 |
| METAL | 0,886 | 0,936 | 0,910 | 249 |
| VIDRO | 0,905 | 0,894 | 0,899 | 160 |
| ELETRONICO | 0,985 | 0,996 | 0,991 | 270 |
| ORGANICO | 0,947 | 0,977 | 0,962 | 129 |
| REJEITO | 0,803 | 0,825 | 0,814 | 183 |

Matriz de confusão: [resultados/matriz_confusao_fp32.png](resultados/matriz_confusao_fp32.png).

### Por dataset de origem (mesmo modelo)

| Dataset no teste | n | Acurácia | F1-macro |
|---|---|---|---|
| TrashNet (estúdio, fundo branco) | 378 | 94,2% | 0,915 |
| RealWaste (aterro) | 714 | 94,4% | 0,945 |
| **TACO (lixo fotografado no ambiente)** | 459 | **75,2%** | **0,483** |
| GIZ e-waste | 269 | 100% | 1,000 |

O TACO é o domínio mais parecido com uma foto tirada por um cidadão na rua, e é nele que o
modelo vai pior. O F1-macro baixo vem das classes com pouquíssimos exemplos no TACO
(ex.: 2 de ORGANICO e 1 de ELETRONICO no teste).

## 3. Generalização entre domínios (domain shift)

Cada modelo foi avaliado no teste de cada dataset, **restrito às classes em comum**
(o argmax considera só essas classes). Valores: acurácia / F1-macro.

| Treinado com ↓ · Testado em → | TrashNet | RealWaste | TACO |
|---|---|---|---|
| Só TrashNet | **89,4% / 0,861** | 43,4% / 0,419 | 29,6% / 0,300 |
| Só RealWaste | 69,8% / 0,527 | **94,1% / 0,944** | 33,6% / 0,316 |
| TrashNet + RealWaste + GIZ (sem TACO) | 94,2% / 0,915 | 93,7% / 0,938 | 21,4% / 0,248 ¹ |
| Todos (modelo final) | 94,4% / 0,921 | 93,3% / 0,934 | 76,3% / 0,491 |

¹ No teste do TACO. Avaliado no TACO inteiro (3.073 recortes, nunca vistos): 18,8% / 0,208.

**Leitura:**
- Um modelo treinado num único domínio vai muito bem nele (89–94%) e cai para 30–43% em outro.
  Esse é o principal resultado do experimento: **acurácia alta no próprio dataset não garante
  acurácia em fotos reais**.
- Treinar sem o TACO mantém o desempenho em TrashNet e RealWaste, mas no TACO o modelo fica
  perto do acaso (≈ 21%; o acaso com 7 classes é ≈ 14%).
- Incluir fotos "no ambiente" (TACO) no treino é o que mais melhora o domínio realista
  (21% → 76%). Isso indica que **fotos reais de Teresina**, coletadas pelo feedback do app,
  devem ser o insumo mais valioso de um retreino futuro.

Detalhes: [resultados/dominio.json](resultados/dominio.json).

> Este experimento é da primeira rodada de treino (RTX 3050) e não foi refeito junto com o modelo
> final atual (Colab). A linha "Todos" é, portanto, a do modelo anterior; os números do modelo
> atual estão nas seções 2 e 4 e diferem em até ~1,5 ponto percentual, dentro da variação
> esperada entre execuções (seção 5, item 6).

## 4. Exportação e desempenho de inferência

ONNX opset 17; diferença máxima entre as saídas do PyTorch e do ONNX Runtime: 7,9 × 10⁻⁶.
Latência medida na CPU da máquina do Colab (Intel Xeon @ 2,0 GHz, 2 vCPUs, ONNX Runtime 1.30,
batch 1, 200 execuções). Com só 2 vCPUs, 4 threads disputam núcleos e ficam *mais lentas*
que 1 thread; por isso a tabela mostra 1 thread e o padrão do ONNX Runtime.

| Variante | Tamanho | Acurácia teste | F1-macro | Latência (1 thread) | Latência (padrão ORT) |
|---|---|---|---|---|---|
| **FP32 (escolhida)** | 16,8 MB | **90,3%** | **0,908** | 7,5 ms | 7,5 ms |
| INT8 dinâmica | 4,4 MB | 75,9% | 0,757 | 89,6 ms | 90,5 ms |
| INT8 estática | 4,7 MB | 60,7% | 0,599 | 9,9 ms | 9,9 ms |

**Decisão: o app usa o modelo FP32** (`modelo/reciclathe.onnx`). As duas quantizações perderam
muita acurácia, e a INT8 dinâmica ainda ficou ~12× mais lenta (o ONNX Runtime não acelera bem as
convoluções *depthwise* quantizadas dinamicamente). 16,8 MB é aceitável para um app. A
quantização com treino consciente (QAT) ou FP16 fica como trabalho futuro.

A latência no celular **não foi medida**: deve ser medida no aparelho durante os testes de
usabilidade, porque a CPU de servidor/notebook é bem mais rápida que a de um celular intermediário.
A quantização também variou bastante entre execuções (INT8 estática: 40,8% na primeira rodada,
60,7% nesta), o que reforça que ela é instável para esta arquitetura.

## 5. Limitações e ameaças à validade

1. **ELETRONICO depende de um único dataset.** Quase todos os exemplos vêm do GIZ:
   eletrodomésticos grandes (TV, geladeira, ar-condicionado) em ferros-velhos de Gana. Os 100%
   no GIZ são em parte um **atalho**: o modelo pode reconhecer o "estilo" das fotos desse dataset,
   e não o conceito de eletrônico. **Pilhas, baterias, celulares e carregadores, os itens que
   o cidadão mais leva a pontos de logística reversa, praticamente não estão na base.** Espera-se
   desempenho bem pior nesses itens em uso real.
2. **ORGANICO vem quase só do RealWaste**, com o mesmo risco de atalho em menor grau.
3. **Nenhuma imagem é de Teresina** nem de embalagens brasileiras (ex.: caixa longa vida de marcas
   locais, garrafa de cajuína). O experimento de domínio mostra que essa diferença pode derrubar a
   acurácia.
4. **Acurácia em uso real não é a do teste.** O número de referência para fotos de celular é o
   do TACO (75%), não o geral (90%). A acurácia real será medida pelo feedback dos usuários no
   painel do sistema durante a avaliação com colegas.
5. **Recortes de bounding box (TACO e GIZ)** têm enquadramento diferente de uma foto feita pelo
   usuário, que normalmente tem mais fundo.
6. **Uma única execução por configuração** (semente fixa). Não há intervalo de confiança entre
   execuções; variações de 1 a 2 pontos percentuais entre configurações não devem ser tratadas como
   diferenças reais.
