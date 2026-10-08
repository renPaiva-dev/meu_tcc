import * as ImagePicker from 'expo-image-picker';
import { Link, router } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';

import { obterClassificador, type Classificador } from '../../classificacao';
import { Aviso, Cartao, Paragrafo, Tela, Titulo } from '../../componentes/basicos';
import { Botao } from '../../componentes/Botao';
import { cores, espaco, fonte } from '../../componentes/tema';
import { useDados } from '../../contexto/DadosContexto';
import { definirIdentificacaoAtual } from '../../contexto/identificacaoAtual';

function formatarData(iso: string | null): string {
  if (!iso) return 'nunca';
  return new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
}

export default function Identificar() {
  const { sincronizar, sincronizando, ultimaSincronizacao, erroSincronizacao, pendentes } = useDados();
  const [classificador, setClassificador] = useState<Classificador | null>(null);
  const [processando, setProcessando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    let ativo = true;
    void obterClassificador().then((c) => ativo && setClassificador(c));
    return () => {
      ativo = false;
    };
  }, []);

  async function obterFoto(origem: 'camera' | 'galeria') {
    setErro(null);
    const opcoes: ImagePicker.ImagePickerOptions = { mediaTypes: ['images'], quality: 0.9, exif: false };
    let resultado: ImagePicker.ImagePickerResult;
    if (origem === 'camera') {
      const permissao = await ImagePicker.requestCameraPermissionsAsync();
      if (!permissao.granted) {
        setErro('Permissão da câmera negada. Ative-a nas configurações ou escolha uma foto da galeria.');
        return;
      }
      resultado = await ImagePicker.launchCameraAsync(opcoes);
    } else {
      resultado = await ImagePicker.launchImageLibraryAsync(opcoes);
    }
    if (resultado.canceled || resultado.assets.length === 0) return;
    await classificar(resultado.assets[0].uri);
  }

  async function classificar(uri: string) {
    setProcessando(true);
    try {
      const c = classificador ?? (await obterClassificador());
      const r = await c.classificar(uri);
      definirIdentificacaoAtual({ uri, resultado: r, descricaoClassificador: c.descricao });
      router.push('/resultado');
    } catch (e) {
      setErro(`Não foi possível analisar a foto: ${(e as Error).message}`);
    } finally {
      setProcessando(false);
    }
  }

  return (
    <Tela atualizando={sincronizando} aoAtualizar={sincronizar}>
      <Titulo>O que você quer descartar?</Titulo>
      <Paragrafo>
        Tire uma foto do resíduo. A identificação é feita no próprio celular, mesmo sem internet.
      </Paragrafo>

      {classificador?.origem === 'desenvolvimento' ? (
        <Aviso tipo="alerta">{classificador.descricao}</Aviso>
      ) : null}
      {erro ? <Aviso tipo="erro">{erro}</Aviso> : null}

      {processando ? (
        <View style={estilos.processando} accessibilityLiveRegion="polite">
          <ActivityIndicator size="large" color={cores.primaria} />
          <Text style={estilos.processandoTexto}>Analisando a foto…</Text>
        </View>
      ) : (
        <View style={estilos.botoes}>
          <Botao titulo="Tirar foto" aoPressionar={() => obterFoto('camera')} dica="Abre a câmera" />
          <Botao
            titulo="Escolher da galeria"
            variante="secundario"
            aoPressionar={() => obterFoto('galeria')}
          />
        </View>
      )}

      <Cartao>
        <Paragrafo secundario>Dica: fotografe um item por vez, bem iluminado e ocupando boa parte da foto.</Paragrafo>
        <Paragrafo secundario>
          Fotografar não dá pontos. <Link href="/como-ganhar-pontos" style={estilos.link}>Veja como ganhar pontos</Link>.
        </Paragrafo>
      </Cartao>

      <Cartao>
        <Text style={estilos.status}>Dados offline atualizados em: {formatarData(ultimaSincronizacao)}</Text>
        {pendentes > 0 ? (
          <Text style={estilos.status}>{pendentes} envio(s) aguardando conexão.</Text>
        ) : null}
        {erroSincronizacao ? (
          <Text style={estilos.status}>Última sincronização falhou: {erroSincronizacao}</Text>
        ) : null}
        <Text style={estilos.status}>Puxe a tela para baixo para sincronizar.</Text>
      </Cartao>
    </Tela>
  );
}

const estilos = StyleSheet.create({
  botoes: { gap: espaco.m, marginVertical: espaco.s },
  processando: { alignItems: 'center', gap: espaco.s, paddingVertical: espaco.xl },
  processandoTexto: { fontSize: fonte.media, color: cores.texto },
  link: { color: cores.info, textDecorationLine: 'underline', fontWeight: '700' },
  status: { fontSize: fonte.pequena, color: cores.textoSecundario },
});
