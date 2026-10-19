import { Checkbox } from 'expo-checkbox';
import { router } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { Image, Linking, Pressable, StyleSheet, Text, View } from 'react-native';

import { mensagemDeErro } from '../api/cliente';
import { api } from '../api/instancia';
import { CODIGOS_MATERIAL, type DadosFeedback } from '../api/tipos';
import { enviarFeedback, registrarIdentificacao, type RefIdentificacao } from '../armazenamento/fila';
import { Aviso, Cartao, Info, Paragrafo, Subtitulo, Tela } from '../componentes/basicos';
import { Botao } from '../componentes/Botao';
import { SeletorMaterial } from '../componentes/seletores';
import { ALVO_TOQUE, cores, espaco, fonte } from '../componentes/tema';
import { LIMIAR_CONFIANCA } from '../config';
import { useDados } from '../contexto/DadosContexto';
import { obterIdentificacaoAtual } from '../contexto/identificacaoAtual';
import { useSessao } from '../contexto/SessaoContexto';
import { apagarArquivo, persistirFotoDoFeedback, prepararFotoParaEnvio } from '../utilitarios/fotos';
import { corTextoSobre, encontrarMaterial } from '../utilitarios/materiais';

function porcentagem(p: number): string {
  return `${Math.round(p * 100)}%`;
}

type EstadoRegistro =
  | { fase: 'enviando' }
  | { fase: 'ok'; ref: RefIdentificacao; enfileirado: boolean }
  /** Classificador de desenvolvimento (aleatório): nada é registrado na API. */
  | { fase: 'somenteLocal' }
  | { fase: 'erro'; mensagem: string };

export default function Resultado() {
  const atual = obterIdentificacaoAtual();
  const { materiais, zonas, atualizarPendentes } = useDados();
  const { usuario, recarregarUsuario } = useSessao();

  const resultado = atual?.resultado;
  const confiancaBaixa = !!resultado && resultado.melhor.probabilidade < LIMIAR_CONFIANCA;
  // Com confiança baixa, o usuário escolhe entre as 3 mais prováveis antes de ver a orientação.
  const [escolhido, setEscolhido] = useState<string | null>(confiancaBaixa ? null : (resultado?.melhor.classe ?? null));
  const simulado = resultado?.origem === 'desenvolvimento';
  const [registro, setRegistro] = useState<EstadoRegistro>(simulado ? { fase: 'somenteLocal' } : { fase: 'enviando' });
  const registrou = useRef(false);

  // Registra a previsão do modelo (top-1) uma única vez; sem conexão ou com falha do servidor,
  // vai para a fila. Resultados aleatórios do modo desenvolvimento não são enviados à API.
  useEffect(() => {
    if (!resultado || simulado || registrou.current) return;
    registrou.current = true;
    void (async () => {
      try {
        const r = await registrarIdentificacao(api, {
          material: resultado.melhor.classe,
          confianca: Number(resultado.melhor.probabilidade.toFixed(4)),
          zona: usuario?.zona ?? null,
          versaoModelo: resultado.versaoModelo,
        });
        setRegistro({ fase: 'ok', ...r });
        if (r.enfileirado) await atualizarPendentes();
      } catch (e) {
        setRegistro({ fase: 'erro', mensagem: mensagemDeErro(e) });
      }
    })();
  }, [resultado, simulado, usuario?.zona, atualizarPendentes]);

  if (!atual || !resultado) {
    return (
      <Tela>
        <Aviso tipo="erro">Nenhuma foto analisada. Volte e tire uma foto.</Aviso>
        <Botao titulo="Voltar" aoPressionar={() => router.back()} />
      </Tela>
    );
  }

  const material = escolhido ? encontrarMaterial(materiais, escolhido) : null;
  const probEscolhido = escolhido
    ? (resultado.top3.find((p) => p.classe === escolhido)?.probabilidade ?? null)
    : null;
  const zona = zonas.find((z) => z.codigo === usuario?.zona) ?? null;

  return (
    <Tela>
      {resultado.origem === 'desenvolvimento' ? (
        <Aviso tipo="alerta">{atual.descricaoClassificador}</Aviso>
      ) : null}

      <Image
        source={{ uri: atual.uri }}
        style={estilos.foto}
        resizeMode="cover"
        accessibilityIgnoresInvertColors
        accessibilityLabel="Foto analisada"
      />

      {confiancaBaixa && !escolhido ? (
        <Cartao>
          <Subtitulo>Não tenho certeza. Qual destes é o seu resíduo?</Subtitulo>
          {resultado.top3.map((p) => {
            const m = encontrarMaterial(materiais, p.classe);
            return (
              <Pressable
                key={p.classe}
                accessibilityRole="button"
                accessibilityLabel={`${m.nome}, ${porcentagem(p.probabilidade)} de confiança`}
                onPress={() => setEscolhido(p.classe)}
                style={({ pressed }) => [estilos.opcao, pressed && { opacity: 0.7 }]}
              >
                <View style={[estilos.bolinha, { backgroundColor: m.corHex }]} />
                <Text style={estilos.opcaoTexto}>{m.nome}</Text>
                <Text style={estilos.opcaoProb}>{porcentagem(p.probabilidade)}</Text>
              </Pressable>
            );
          })}
          <Paragrafo secundario>Se não for nenhum deles, escolha abaixo:</Paragrafo>
          <SeletorMaterial materiais={materiais} valor={null} aoMudar={(c) => c && setEscolhido(c)} />
        </Cartao>
      ) : null}

      {material ? (
        <>
          <View style={[estilos.faixa, { backgroundColor: material.corHex }]}>
            <Text style={[estilos.faixaTitulo, { color: corTextoSobre(material.corHex) }]} accessibilityRole="header">
              {material.nome}
            </Text>
            <Text style={[estilos.faixaTexto, { color: corTextoSobre(material.corHex) }]}>
              Lixeira {material.corNome.toLowerCase()} (padrão CONAMA 275/2001)
            </Text>
            <Text style={[estilos.faixaTexto, { color: corTextoSobre(material.corHex) }]}>
              {probEscolhido != null
                ? `Confiança do modelo: ${porcentagem(probEscolhido)}`
                : 'Escolhido por você'}
              {material.reciclavel ? ' · Reciclável' : ' · Não reciclável'}
            </Text>
          </View>
          {confiancaBaixa ? (
            <Botao titulo="Escolher outro material" variante="texto" aoPressionar={() => setEscolhido(null)} />
          ) : null}

          <Cartao>
            <Subtitulo>Como descartar</Subtitulo>
            <Info rotulo="Como preparar" valor={material.comoPreparar} />
            <Info rotulo="Onde descartar" valor={material.ondeDescartar} />
            <Info rotulo="Observações" valor={material.observacoes} />
            <Info rotulo="Fonte" valor={material.fonte} />
          </Cartao>

          <Botao
            titulo="Onde entregar"
            aoPressionar={() => router.navigate({ pathname: '/mapa', params: { material: material.codigo } })}
            dica={`Abre o mapa com pontos que recebem ${material.nome}`}
          />

          {zona?.telefoneColeta ? (
            <Botao
              titulo={`Disk Coleta ${zona.nome}: ${zona.telefoneColeta}`}
              variante="secundario"
              aoPressionar={() => Linking.openURL(`tel:${zona.telefoneColeta!.replace(/[^\d+]/g, '')}`)}
              dica="Liga para o serviço de coleta da sua zona"
            />
          ) : !usuario?.zona ? (
            <Aviso tipo="info">Defina sua zona na aba Perfil para ver o telefone do Disk Coleta da sua região.</Aviso>
          ) : null}

          {registro.fase === 'somenteLocal' ? (
            <Aviso tipo="alerta">
              Modo desenvolvimento: esta identificação é aleatória e não foi registrada nem pode receber resposta.
            </Aviso>
          ) : registro.fase === 'erro' ? null : (
            <Feedback
              refIdentificacao={registro.fase === 'ok' ? registro.ref : null}
              classePrevista={resultado.melhor.classe}
              escolhaInicial={escolhido}
              uriFoto={atual.uri}
              aoConcluir={async () => {
                await atualizarPendentes();
                await recarregarUsuario();
              }}
            />
          )}
        </>
      ) : null}

      {registro.fase === 'ok' && registro.enfileirado ? (
        <Aviso tipo="info">
          Não foi possível falar com o servidor agora: a identificação será registrada quando a conexão voltar.
        </Aviso>
      ) : null}
      {registro.fase === 'erro' ? (
        <Aviso tipo="erro">Não foi possível registrar a identificação: {registro.mensagem}</Aviso>
      ) : null}
      <Paragrafo secundario>
        Classificação offline em {resultado.duracaoMs} ms · modelo {resultado.versaoModelo}
      </Paragrafo>
    </Tela>
  );
}

// ---------- "A identificação está certa?" ----------

interface PropsFeedback {
  refIdentificacao: RefIdentificacao | null;
  classePrevista: string;
  /** Material escolhido pelo usuário entre as opções (confiança baixa). */
  escolhaInicial: string | null;
  uriFoto: string;
  aoConcluir: () => Promise<void>;
}

function Feedback({ refIdentificacao, classePrevista, escolhaInicial, uriFoto, aoConcluir }: PropsFeedback) {
  const { materiais } = useDados();
  // Se o usuário escolheu outro material que não o top-1, já sugerimos "Não" com a correção.
  const escolheuOutro = escolhaInicial != null && escolhaInicial !== classePrevista;
  const [correto, setCorreto] = useState<boolean | null>(escolheuOutro ? false : null);
  const [materialCorreto, setMaterialCorreto] = useState<string | null>(escolheuOutro ? escolhaInicial : null);
  const [consentimento, setConsentimento] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [mensagem, setMensagem] = useState<{ tipo: 'sucesso' | 'info' | 'erro'; texto: string } | null>(null);
  const [concluido, setConcluido] = useState(false);
  const nomePrevisto = encontrarMaterial(materiais, classePrevista).nome;
  const opcoesCorrecao = (materiais.length ? materiais.map((m) => m.codigo) : [...CODIGOS_MATERIAL]).filter(
    (c) => c !== classePrevista,
  );

  async function enviar() {
    if (correto == null || !refIdentificacao) return;
    if (!correto && (!materialCorreto || materialCorreto === classePrevista)) {
      setMensagem({ tipo: 'erro', texto: 'Escolha o material correto.' });
      return;
    }
    setEnviando(true);
    setMensagem(null);
    let fotoReduzida: string | null = null;
    try {
      const foto = consentimento ? await prepararFotoParaEnvio(uriFoto, 'identificacao.jpg') : null;
      fotoReduzida = foto?.uri ?? null;
      const dados: DadosFeedback = {
        correto,
        materialCorreto: correto ? null : materialCorreto,
        consentimentoFoto: consentimento,
        foto,
      };
      const r = await enviarFeedback(api, refIdentificacao, dados, persistirFotoDoFeedback);
      if (r.enfileirado) {
        setMensagem({ tipo: 'info', texto: 'Sem conexão com o servidor: sua resposta será enviada quando ela voltar.' });
      } else if (r.pontosGanhos > 0) {
        setMensagem({ tipo: 'sucesso', texto: `Obrigado! Você ganhou ${r.pontosGanhos} pontos.` });
      } else {
        setMensagem({
          tipo: 'sucesso',
          texto: 'Obrigado! Resposta registrada (limite diário de pontos por respostas já atingido).',
        });
      }
      setConcluido(true);
      await aoConcluir();
    } catch (e) {
      setMensagem({ tipo: 'erro', texto: mensagemDeErro(e) });
    } finally {
      // A cópia reduzida fica no cache; se o feedback foi para a fila, a fila tem a própria cópia.
      apagarArquivo(fotoReduzida);
      setEnviando(false);
    }
  }

  if (concluido && mensagem) return <Aviso tipo={mensagem.tipo}>{mensagem.texto}</Aviso>;

  return (
    <Cartao>
      <Subtitulo>A identificação está certa?</Subtitulo>
      <Paragrafo secundario>O modelo identificou: {nomePrevisto}. Sua resposta ajuda a melhorar o app (+2 pontos).</Paragrafo>
      <View style={estilos.linha}>
        <Botao
          titulo="Sim"
          variante={correto === true ? 'primario' : 'secundario'}
          aoPressionar={() => {
            setCorreto(true);
            setMaterialCorreto(null);
          }}
          estilo={estilos.metade}
        />
        <Botao
          titulo="Não"
          variante={correto === false ? 'primario' : 'secundario'}
          aoPressionar={() => setCorreto(false)}
          estilo={estilos.metade}
        />
      </View>
      {correto === false ? (
        <>
          <Paragrafo>Qual é o material correto?</Paragrafo>
          {/* O material previsto não aparece: "Não" + o mesmo material seria contraditório. */}
          <SeletorMaterial
            materiais={materiais}
            valor={materialCorreto}
            aoMudar={setMaterialCorreto}
            opcoes={opcoesCorrecao}
          />
        </>
      ) : null}
      {correto != null ? (
        <>
          <Pressable
            style={estilos.consentimento}
            onPress={() => setConsentimento((v) => !v)}
            accessibilityRole="checkbox"
            accessibilityState={{ checked: consentimento }}
            accessibilityLabel="Autorizo enviar esta foto para melhorar o modelo de identificação"
          >
            <Checkbox
              value={consentimento}
              onValueChange={setConsentimento}
              color={consentimento ? cores.primaria : undefined}
              accessible={false}
            />
            <Text style={estilos.consentimentoTexto}>
              Autorizo enviar esta foto para melhorar o modelo de identificação (opcional).
            </Text>
          </Pressable>
          <Botao
            titulo="Enviar resposta"
            aoPressionar={enviar}
            carregando={enviando}
            desabilitado={!refIdentificacao}
          />
          {!refIdentificacao ? <Paragrafo secundario>Aguardando o registro da identificação…</Paragrafo> : null}
        </>
      ) : null}
      {mensagem ? <Aviso tipo={mensagem.tipo}>{mensagem.texto}</Aviso> : null}
    </Cartao>
  );
}

const estilos = StyleSheet.create({
  foto: { width: '100%', aspectRatio: 4 / 3, borderRadius: 12, backgroundColor: cores.fundoSecundario },
  faixa: { borderRadius: 12, padding: espaco.l, gap: espaco.xs },
  faixaTitulo: { fontSize: fonte.titulo, fontWeight: '800' },
  faixaTexto: { fontSize: fonte.normal, fontWeight: '600' },
  opcao: {
    minHeight: ALVO_TOQUE + 8,
    flexDirection: 'row',
    alignItems: 'center',
    gap: espaco.m,
    paddingHorizontal: espaco.m,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: cores.borda,
    backgroundColor: cores.fundo,
  },
  bolinha: { width: 20, height: 20, borderRadius: 10 },
  opcaoTexto: { flex: 1, fontSize: fonte.media, fontWeight: '700', color: cores.texto },
  opcaoProb: { fontSize: fonte.normal, color: cores.textoSecundario },
  linha: { flexDirection: 'row', gap: espaco.m },
  metade: { flex: 1 },
  consentimento: { flexDirection: 'row', alignItems: 'center', gap: espaco.m, minHeight: ALVO_TOQUE },
  consentimentoTexto: { flex: 1, fontSize: fonte.normal, color: cores.texto },
});
