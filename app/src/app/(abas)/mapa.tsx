import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { FlatList, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';

import { DESCRICAO_TIPO_PONTO, type PontoColeta } from '../../api/tipos';
import { Aviso, Chip } from '../../componentes/basicos';
import { Botao } from '../../componentes/Botao';
import { carregarMapa } from '../../componentes/mapa';
import { ALVO_TOQUE, cores, espaco, fonte } from '../../componentes/tema';
import { useDados } from '../../contexto/DadosContexto';
import { distanciaMetros, formatarDistancia, type Coordenadas } from '../../utilitarios/geo';
import { obterLocalizacaoAtual, pedirPermissaoLocalizacao } from '../../utilitarios/localizacao';
import { encontrarMaterial } from '../../utilitarios/materiais';
import { CODIGOS_MATERIAL } from '../../api/tipos';

type PontoComDistancia = PontoColeta & { distancia: number | null };

export default function Mapa() {
  const params = useLocalSearchParams<{ material?: string }>();
  const { pontos, materiais, sincronizar, sincronizando, ultimaSincronizacao } = useDados();
  const [filtro, setFiltro] = useState<string | null>(params.material ?? null);
  const [local, setLocal] = useState<Coordenadas | null>(null);
  const [erroLocal, setErroLocal] = useState<string | null>(null);
  const [verMapa, setVerMapa] = useState(true);
  const mapa = carregarMapa();

  // "Onde entregar" na tela de resultado abre esta aba com ?material=...
  useEffect(() => {
    if (params.material) setFiltro(params.material);
  }, [params.material]);

  useEffect(() => {
    void (async () => {
      if (!(await pedirPermissaoLocalizacao())) return;
      try {
        setLocal(await obterLocalizacaoAtual());
      } catch (e) {
        setErroLocal((e as Error).message);
      }
    })();
  }, []);

  const lista = useMemo<PontoComDistancia[]>(() => {
    const filtrados = filtro ? pontos.filter((p) => p.materiais.includes(filtro)) : pontos;
    return filtrados
      .map((p) => ({ ...p, distancia: local ? distanciaMetros(local, p) : null }))
      .sort((a, b) => (a.distancia ?? 0) - (b.distancia ?? 0) || a.nome.localeCompare(b.nome));
  }, [pontos, filtro, local]);

  const codigos = materiais.length ? materiais.map((m) => m.codigo) : [...CODIGOS_MATERIAL];
  const corFiltro = filtro ? encontrarMaterial(materiais, filtro).corHex : undefined;

  const abrirPonto = (id: number) => router.push({ pathname: '/ponto/[id]', params: { id: String(id) } });

  return (
    <View style={estilos.tela}>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={estilos.filtros}
        style={estilos.barraFiltros}
        accessibilityLabel="Filtrar por material"
      >
        <Chip papel="radio" rotulo="Todos" selecionado={filtro == null} aoPressionar={() => setFiltro(null)} />
        {codigos.map((c) => {
          const m = encontrarMaterial(materiais, c);
          return (
            <Chip
              key={c}
              papel="radio"
              rotulo={m.nome}
              cor={m.corHex}
              selecionado={filtro === c}
              aoPressionar={() => setFiltro(c)}
            />
          );
        })}
      </ScrollView>

      {mapa ? (
        <View style={estilos.alternar}>
          <Botao
            titulo={verMapa ? 'Ver só a lista' : 'Mostrar mapa'}
            variante="texto"
            aoPressionar={() => setVerMapa((v) => !v)}
          />
          <Botao titulo="Sugerir ponto" variante="texto" aoPressionar={() => router.push('/sugerir')} />
        </View>
      ) : null}

      {mapa && verMapa ? (
        <View style={estilos.areaMapa}>
          <mapa.MapaPontos
            pontos={lista}
            cor={corFiltro}
            centro={local ?? undefined}
            mostrarUsuario={local != null}
            aoSelecionar={abrirPonto}
          />
        </View>
      ) : null}

      <FlatList
        style={estilos.lista}
        data={lista}
        keyExtractor={(p) => String(p.id)}
        refreshControl={<RefreshControl refreshing={sincronizando} onRefresh={sincronizar} colors={[cores.primaria]} />}
        contentContainerStyle={estilos.conteudoLista}
        ListHeaderComponent={
          <View style={{ gap: espaco.s }}>
            {!mapa ? (
              <>
                <Aviso tipo="alerta">
                  Mapa indisponível neste ambiente (Expo Go não inclui o MapLibre). Use um development build.
                  Os pontos aparecem na lista abaixo.
                </Aviso>
                <Botao titulo="Sugerir ponto" variante="secundario" aoPressionar={() => router.push('/sugerir')} />
              </>
            ) : null}
            {erroLocal ? <Aviso tipo="info">{erroLocal}</Aviso> : null}
            {!local && !erroLocal ? (
              <Text style={estilos.legenda}>Permita a localização para ordenar pelos pontos mais próximos.</Text>
            ) : null}
            <Text style={estilos.legenda} accessibilityLiveRegion="polite">
              {lista.length} ponto(s){filtro ? ` que recebem ${encontrarMaterial(materiais, filtro).nome}` : ''}
              {ultimaSincronizacao ? '' : ' · conecte-se para baixar os pontos'}
            </Text>
          </View>
        }
        ListEmptyComponent={
          <Text style={estilos.vazio}>
            Nenhum ponto encontrado. Puxe para baixo para sincronizar ou escolha outro material.
          </Text>
        }
        renderItem={({ item }) => (
          <Pressable
            onPress={() => abrirPonto(item.id)}
            accessibilityRole="button"
            accessibilityLabel={`${item.nome}, ${DESCRICAO_TIPO_PONTO[item.tipo] ?? item.tipo}${
              item.distancia != null ? `, a ${formatarDistancia(item.distancia)}` : ''
            }`}
            style={({ pressed }) => [estilos.item, pressed && { opacity: 0.7 }]}
          >
            <View style={{ flex: 1 }}>
              <Text style={estilos.itemNome}>{item.nome}</Text>
              <Text style={estilos.itemTexto}>{DESCRICAO_TIPO_PONTO[item.tipo] ?? item.tipo}</Text>
              <Text style={estilos.itemTexto} numberOfLines={2}>
                {item.endereco}
              </Text>
            </View>
            {item.distancia != null ? <Text style={estilos.distancia}>{formatarDistancia(item.distancia)}</Text> : null}
          </Pressable>
        )}
      />
    </View>
  );
}

const estilos = StyleSheet.create({
  tela: { flex: 1, backgroundColor: cores.fundo },
  barraFiltros: { flexGrow: 0 },
  filtros: { gap: espaco.s, padding: espaco.m },
  alternar: { flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: espaco.s },
  areaMapa: { height: 300, paddingHorizontal: espaco.m },
  lista: { flex: 1 },
  conteudoLista: { padding: espaco.m, gap: espaco.s },
  legenda: { fontSize: fonte.pequena, color: cores.textoSecundario },
  vazio: { fontSize: fonte.normal, color: cores.textoSecundario, textAlign: 'center', padding: espaco.xl },
  item: {
    minHeight: ALVO_TOQUE,
    flexDirection: 'row',
    alignItems: 'center',
    gap: espaco.m,
    padding: espaco.m,
    borderRadius: 10,
    backgroundColor: cores.fundoSecundario,
  },
  itemNome: { fontSize: fonte.normal, fontWeight: '700', color: cores.texto },
  itemTexto: { fontSize: fonte.pequena, color: cores.textoSecundario },
  distancia: { fontSize: fonte.normal, fontWeight: '700', color: cores.primaria },
});
