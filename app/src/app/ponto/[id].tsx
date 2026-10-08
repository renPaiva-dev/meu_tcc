import { Stack, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Linking, Platform, StyleSheet, Text, View } from 'react-native';

import { ErroApi, mensagemDeErro } from '../../api/cliente';
import { api } from '../../api/instancia';
import { DESCRICAO_TIPO_PONTO } from '../../api/tipos';
import { Aviso, Cartao, Info, Paragrafo, Tela, Titulo } from '../../componentes/basicos';
import { Botao } from '../../componentes/Botao';
import { cores, espaco, fonte } from '../../componentes/tema';
import { useDados } from '../../contexto/DadosContexto';
import { useSessao } from '../../contexto/SessaoContexto';
import { distanciaMetros, formatarDistancia } from '../../utilitarios/geo';
import { obterLocalizacaoAtual } from '../../utilitarios/localizacao';
import { encontrarMaterial } from '../../utilitarios/materiais';

/** Abre o app de mapas do aparelho com a rota até o ponto (sem depender de Google). */
async function abrirComoChegar(lat: number, lon: number, nome: string) {
  const rotulo = encodeURIComponent(nome);
  const url =
    Platform.OS === 'ios'
      ? `https://maps.apple.com/?daddr=${lat},${lon}&q=${rotulo}`
      : `geo:${lat},${lon}?q=${lat},${lon}(${rotulo})`;
  try {
    await Linking.openURL(url);
  } catch {
    await Linking.openURL(`https://www.openstreetmap.org/directions?to=${lat}%2C${lon}#map=17/${lat}/${lon}`);
  }
}

export default function DetalhePonto() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { pontos, materiais } = useDados();
  const { recarregarUsuario } = useSessao();
  const ponto = pontos.find((p) => String(p.id) === id);
  const [fazendoCheckin, setFazendoCheckin] = useState(false);
  const [mensagem, setMensagem] = useState<{ tipo: 'sucesso' | 'erro' | 'info'; texto: string } | null>(null);

  if (!ponto) {
    return (
      <Tela>
        <Aviso tipo="erro">Ponto não encontrado. Sincronize os dados e tente novamente.</Aviso>
      </Tela>
    );
  }

  async function checkin() {
    if (!ponto) return;
    setFazendoCheckin(true);
    setMensagem(null);
    try {
      const local = await obterLocalizacaoAtual();
      const r = await api.checkin(ponto.id, local.latitude, local.longitude);
      setMensagem({ tipo: 'sucesso', texto: `Check-in feito! Você ganhou ${r.pontosGanhos} pontos.` });
      await recarregarUsuario();
    } catch (e) {
      // 422: longe do ponto (> 150 m) ou já fez check-in hoje — a API explica no "detail".
      const texto = e instanceof ErroApi && e.status === 422 ? e.detalhe : mensagemDeErro(e);
      setMensagem({ tipo: 'erro', texto });
    } finally {
      setFazendoCheckin(false);
    }
  }

  return (
    <Tela>
      <Stack.Screen options={{ title: ponto.nome }} />
      <Titulo>{ponto.nome}</Titulo>
      <Paragrafo secundario>{DESCRICAO_TIPO_PONTO[ponto.tipo] ?? ponto.tipo}</Paragrafo>

      <Cartao>
        <Info rotulo="Endereço" valor={ponto.endereco} />
        <Info rotulo="Horário" valor={ponto.horario ?? 'Não informado'} />
        <Info rotulo="Telefone" valor={ponto.telefone} />
        <Info rotulo="Fonte da informação" valor={ponto.fonte} />
      </Cartao>

      <Cartao>
        <Text style={estilos.rotulo}>Materiais aceitos</Text>
        <View style={estilos.materiais}>
          {ponto.materiais.map((c) => {
            const m = encontrarMaterial(materiais, c);
            return (
              <View key={c} style={estilos.material}>
                <View style={[estilos.bolinha, { backgroundColor: m.corHex }]} />
                <Text style={estilos.materialTexto}>{m.nome}</Text>
              </View>
            );
          })}
        </View>
      </Cartao>

      <Botao titulo="Como chegar" aoPressionar={() => abrirComoChegar(ponto.latitude, ponto.longitude, ponto.nome)} />
      {ponto.telefone ? (
        <Botao
          titulo={`Ligar: ${ponto.telefone}`}
          variante="secundario"
          aoPressionar={() => Linking.openURL(`tel:${ponto.telefone!.replace(/[^\d+]/g, '')}`)}
        />
      ) : null}

      <Cartao>
        <Text style={estilos.rotulo}>Está no local?</Text>
        <Paragrafo secundario>
          O check-in usa sua localização atual (precisa estar a até 150 m do ponto) e vale 5 pontos, uma vez por dia
          em cada ponto. A coordenada é usada só para conferir a distância.
        </Paragrafo>
        <Botao titulo="Fazer check-in" variante="secundario" aoPressionar={checkin} carregando={fazendoCheckin} />
      </Cartao>
      {mensagem ? <Aviso tipo={mensagem.tipo}>{mensagem.texto}</Aviso> : null}
      <DistanciaAtual lat={ponto.latitude} lon={ponto.longitude} />
    </Tela>
  );
}

/** Mostra a distância até o ponto se o usuário pedir (evita pedir localização sem necessidade). */
function DistanciaAtual({ lat, lon }: { lat: number; lon: number }) {
  const [texto, setTexto] = useState<string | null>(null);
  return texto ? (
    <Paragrafo secundario>{texto}</Paragrafo>
  ) : (
    <Botao
      titulo="Calcular distância até aqui"
      variante="texto"
      aoPressionar={async () => {
        try {
          const local = await obterLocalizacaoAtual();
          setTexto(`Você está a ${formatarDistancia(distanciaMetros(local, { latitude: lat, longitude: lon }))} do ponto.`);
        } catch (e) {
          setTexto((e as Error).message);
        }
      }}
    />
  );
}

const estilos = StyleSheet.create({
  rotulo: { fontSize: fonte.pequena, fontWeight: '700', color: cores.textoSecundario },
  materiais: { flexDirection: 'row', flexWrap: 'wrap', gap: espaco.s },
  material: { flexDirection: 'row', alignItems: 'center', gap: espaco.xs, paddingVertical: espaco.xs },
  bolinha: { width: 14, height: 14, borderRadius: 7 },
  materialTexto: { fontSize: fonte.normal, color: cores.texto },
});
