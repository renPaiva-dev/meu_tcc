import { Link, router } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { Alert, StyleSheet, Text, View } from 'react-native';

import { mensagemDeErro } from '../../api/cliente';
import { api } from '../../api/instancia';
import type { Historico, ItemRanking } from '../../api/tipos';
import { Aviso, Cartao, Chip, GrupoChips, Paragrafo, Subtitulo, Tela, Titulo } from '../../componentes/basicos';
import { Botao } from '../../componentes/Botao';
import { SeletorZona } from '../../componentes/seletores';
import { cores, espaco, fonte } from '../../componentes/tema';
import { useDados } from '../../contexto/DadosContexto';
import { useSessao } from '../../contexto/SessaoContexto';
import { obterLocalizacaoAtual } from '../../utilitarios/localizacao';
import { nomeMaterial, zonasOuPadrao } from '../../utilitarios/materiais';

const NOME_EVENTO: Record<string, string> = {
  SUGESTAO_APROVADA: 'Sugestão de ponto aprovada',
  CHECKIN: 'Check-in em ponto de coleta',
  FEEDBACK: 'Resposta sobre identificação',
};

function data(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR');
}

export default function Perfil() {
  const { usuario, sair, excluirConta, alterarZona, recarregarUsuario } = useSessao();
  const { zonas, sincronizar, sincronizando, materiais, pendentes } = useDados();
  const [abaRanking, setAbaRanking] = useState<'geral' | 'zona'>('geral');
  const [ranking, setRanking] = useState<ItemRanking[] | null>(null);
  const [historico, setHistorico] = useState<Historico | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [mensagemZona, setMensagemZona] = useState<string | null>(null);
  const [detectando, setDetectando] = useState(false);

  const zonaUsuario = usuario?.zona ?? null;
  const nomeZona = zonasOuPadrao(zonas).find((z) => z.codigo === zonaUsuario)?.nome;

  const carregar = useCallback(async () => {
    setErro(null);
    try {
      const [h, r] = await Promise.all([
        api.historico(),
        api.ranking(abaRanking === 'zona' ? zonaUsuario : null),
      ]);
      setHistorico(h);
      setRanking(r);
    } catch (e) {
      setErro(mensagemDeErro(e));
    }
  }, [abaRanking, zonaUsuario]);

  useEffect(() => {
    void carregar();
  }, [carregar]);

  async function atualizarTudo() {
    await Promise.all([sincronizar(), recarregarUsuario(), carregar()]);
  }

  async function trocarZona(codigo: string) {
    setMensagemZona(null);
    try {
      await alterarZona(codigo);
      setMensagemZona('Zona atualizada.');
    } catch (e) {
      setMensagemZona(mensagemDeErro(e));
    }
  }

  async function detectarZona() {
    setDetectando(true);
    setMensagemZona(null);
    try {
      const local = await obterLocalizacaoAtual();
      const r = await api.localizarZona(local.latitude, local.longitude);
      if (!r) {
        // 404: os bairros ainda não foram importados na API, ou o ponto está fora deles.
        setMensagemZona('Não foi possível detectar sua zona automaticamente. Escolha-a na lista.');
        return;
      }
      await alterarZona(r.zona.codigo);
      setMensagemZona(`Zona detectada: ${r.zona.nome} (bairro ${r.bairro}).`);
    } catch (e) {
      setMensagemZona(mensagemDeErro(e));
    } finally {
      setDetectando(false);
    }
  }

  function confirmarSaida() {
    const aviso =
      pendentes > 0 ? `\n\nAtenção: ${pendentes} envio(s) pendente(s) offline serão descartados.` : '';
    Alert.alert('Sair', `Deseja sair da sua conta?${aviso}`, [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Sair',
        onPress: async () => {
          await sair();
          router.replace('/entrar');
        },
      },
    ]);
  }

  function confirmarExclusao() {
    Alert.alert(
      'Excluir conta',
      'Sua conta, seus pontos e seu histórico serão apagados definitivamente. Esta ação não pode ser desfeita.',
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Excluir definitivamente',
          style: 'destructive',
          onPress: async () => {
            try {
              await excluirConta();
              router.replace('/entrar');
            } catch (e) {
              Alert.alert('Não foi possível excluir', mensagemDeErro(e));
            }
          },
        },
      ],
    );
  }

  return (
    <Tela atualizando={sincronizando} aoAtualizar={atualizarTudo}>
      <Titulo>{usuario?.nome ?? 'Perfil'}</Titulo>
      <Paragrafo secundario>{usuario?.email}</Paragrafo>

      <Cartao estilo={estilos.cartaoPontos}>
        <Text style={estilos.pontos} accessibilityLabel={`${usuario?.pontos ?? 0} pontos`}>
          {usuario?.pontos ?? 0}
        </Text>
        <Text style={estilos.pontosRotulo}>pontos</Text>
        <Link href="/como-ganhar-pontos" style={estilos.link}>
          Como ganhar pontos
        </Link>
      </Cartao>

      {erro ? <Aviso tipo="erro">{erro}</Aviso> : null}

      <Subtitulo>Minha zona{nomeZona ? `: ${nomeZona}` : ''}</Subtitulo>
      <SeletorZona zonas={zonas} valor={zonaUsuario} aoMudar={trocarZona} />
      <Botao titulo="Detectar pela minha localização" variante="texto" aoPressionar={detectarZona} carregando={detectando} />
      {mensagemZona ? <Aviso tipo="info">{mensagemZona}</Aviso> : null}

      <Subtitulo>Ranking</Subtitulo>
      <GrupoChips rotulo="Tipo de ranking" unico>
        <Chip papel="radio" rotulo="Geral" selecionado={abaRanking === 'geral'} aoPressionar={() => setAbaRanking('geral')} />
        <Chip
          papel="radio"
          rotulo={nomeZona ? `Zona ${nomeZona}` : 'Minha zona'}
          selecionado={abaRanking === 'zona'}
          aoPressionar={() => setAbaRanking('zona')}
        />
      </GrupoChips>
      {abaRanking === 'zona' && !zonaUsuario ? (
        <Aviso tipo="info">Escolha sua zona acima para ver o ranking dela.</Aviso>
      ) : (
        <Cartao>
          {ranking == null ? (
            <Paragrafo secundario>Carregando…</Paragrafo>
          ) : ranking.length === 0 ? (
            <Paragrafo secundario>Ninguém pontuou ainda.</Paragrafo>
          ) : (
            ranking.map((r) => (
              <View key={r.posicao} style={estilos.linhaRanking} accessible accessibilityLabel={`${r.posicao}º lugar, ${r.nome}, ${r.pontos} pontos`}>
                <Text style={estilos.posicao}>{r.posicao}º</Text>
                <Text style={estilos.nomeRanking}>{r.nome}</Text>
                <Text style={estilos.pontosRanking}>{r.pontos} pts</Text>
              </View>
            ))
          )}
        </Cartao>
      )}

      <Subtitulo>Histórico de pontos</Subtitulo>
      <Cartao>
        {!historico || historico.pontos.length === 0 ? (
          <Paragrafo secundario>Nenhum ponto ainda.</Paragrafo>
        ) : (
          historico.pontos.map((p, i) => (
            <View key={`${p.tipo}-${p.referenciaId}-${i}`} style={estilos.linhaHistorico}>
              <Text style={estilos.textoHistorico}>
                {data(p.criadoEm)} · {NOME_EVENTO[p.tipo] ?? p.tipo}
              </Text>
              <Text style={estilos.pontosRanking}>+{p.pontos}</Text>
            </View>
          ))
        )}
      </Cartao>

      <Subtitulo>Minhas identificações</Subtitulo>
      <Cartao>
        {!historico || historico.identificacoes.length === 0 ? (
          <Paragrafo secundario>Nenhuma identificação registrada.</Paragrafo>
        ) : (
          historico.identificacoes.map((i) => (
            <View key={i.id} style={estilos.linhaHistorico}>
              <Text style={estilos.textoHistorico}>
                {data(i.criadoEm)} · {nomeMaterial(materiais, i.material)} ({Math.round(i.confianca * 100)}%)
                {i.feedbackCorreto === true ? ' · confirmada' : ''}
                {i.feedbackCorreto === false && i.materialCorrigido
                  ? ` · corrigida para ${nomeMaterial(materiais, i.materialCorrigido)}`
                  : ''}
              </Text>
            </View>
          ))
        )}
      </Cartao>
      {pendentes > 0 ? <Aviso tipo="info">{pendentes} envio(s) aguardando conexão.</Aviso> : null}

      <Link href="/privacidade" style={estilos.link}>
        Privacidade e dados coletados
      </Link>
      <Botao titulo="Sair" variante="secundario" aoPressionar={confirmarSaida} />
      <Botao titulo="Excluir minha conta" variante="perigo" aoPressionar={confirmarExclusao} dica="Apaga sua conta e seus dados" />
    </Tela>
  );
}

const estilos = StyleSheet.create({
  cartaoPontos: { alignItems: 'center', backgroundColor: cores.primariaClara },
  pontos: { fontSize: 48, fontWeight: '800', color: cores.primaria },
  pontosRotulo: { fontSize: fonte.media, color: cores.primaria, fontWeight: '700' },
  link: { color: cores.info, fontSize: fonte.normal, textDecorationLine: 'underline', paddingVertical: 12 },
  linhaRanking: { flexDirection: 'row', alignItems: 'center', gap: espaco.m, minHeight: 36 },
  posicao: { width: 36, fontSize: fonte.normal, fontWeight: '800', color: cores.primaria },
  nomeRanking: { flex: 1, fontSize: fonte.normal, color: cores.texto },
  pontosRanking: { fontSize: fonte.normal, fontWeight: '700', color: cores.texto },
  linhaHistorico: { flexDirection: 'row', justifyContent: 'space-between', gap: espaco.s, paddingVertical: 4 },
  textoHistorico: { flex: 1, fontSize: fonte.pequena, color: cores.texto },
});
