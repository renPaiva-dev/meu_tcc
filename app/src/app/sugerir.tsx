import * as ImagePicker from 'expo-image-picker';
import { router } from 'expo-router';
import { useRef, useState } from 'react';
import { Image, StyleSheet, View } from 'react-native';
import type { CameraRef } from '@maplibre/maplibre-react-native';

import { ErroApi, mensagemDeErro } from '../api/cliente';
import { api } from '../api/instancia';
import { DESCRICAO_TIPO_PONTO, TIPOS_PONTO, type ArquivoLocal, type TipoPonto } from '../api/tipos';
import { Aviso, CampoTexto, Chip, GrupoChips, Paragrafo, Subtitulo, Tela } from '../componentes/basicos';
import { Botao } from '../componentes/Botao';
import { carregarMapa } from '../componentes/mapa';
import { SeletorMateriais } from '../componentes/seletores';
import { espaco } from '../componentes/tema';
import { useDados } from '../contexto/DadosContexto';
import { dentroDeTeresina, type Coordenadas } from '../utilitarios/geo';
import { obterLocalizacaoAtual, pedirPermissaoLocalizacao } from '../utilitarios/localizacao';
import { apagarArquivo, prepararFotoParaEnvio } from '../utilitarios/fotos';

type Campo = 'nome' | 'tipo' | 'endereco' | 'latitude' | 'longitude' | 'horario' | 'telefone' | 'fonte' | 'materiais';

export default function Sugerir() {
  const { materiais } = useDados();
  const mapa = carregarMapa();
  const cameraRef = useRef<CameraRef>(null);
  const [nome, setNome] = useState('');
  const [tipo, setTipo] = useState<TipoPonto>('PEV');
  const [endereco, setEndereco] = useState('');
  const [horario, setHorario] = useState('');
  const [telefone, setTelefone] = useState('');
  const [fonte, setFonte] = useState('');
  const [selecionados, setSelecionados] = useState<string[]>([]);
  const [posicao, setPosicao] = useState<Coordenadas | null>(null);
  const [foto, setFoto] = useState<string | null>(null);
  const [erros, setErros] = useState<Partial<Record<Campo, string>>>({});
  const [erroGeral, setErroGeral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [localizando, setLocalizando] = useState(false);
  const [mostrarUsuario, setMostrarUsuario] = useState(false);
  const [enviado, setEnviado] = useState(false);

  async function usarLocalizacaoAtual() {
    setLocalizando(true);
    try {
      const c = await obterLocalizacaoAtual();
      setPosicao(c);
      setMostrarUsuario(await pedirPermissaoLocalizacao());
      cameraRef.current?.flyTo({ center: [c.longitude, c.latitude], zoom: 16 });
    } catch (e) {
      setErroGeral((e as Error).message);
    } finally {
      setLocalizando(false);
    }
  }

  async function escolherFoto() {
    const r = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.8 });
    if (!r.canceled && r.assets[0]) setFoto(r.assets[0].uri);
  }

  async function tirarFoto() {
    const p = await ImagePicker.requestCameraPermissionsAsync();
    if (!p.granted) {
      setErroGeral('Permissão da câmera negada.');
      return;
    }
    const r = await ImagePicker.launchCameraAsync({ mediaTypes: ['images'], quality: 0.8 });
    if (!r.canceled && r.assets[0]) setFoto(r.assets[0].uri);
  }

  function validar() {
    const e: Partial<Record<Campo, string>> = {};
    if (!nome.trim()) e.nome = 'Informe o nome do local.';
    if (!endereco.trim()) e.endereco = 'Informe o endereço.';
    if (selecionados.length === 0) e.materiais = 'Escolha pelo menos um material.';
    if (!posicao) e.latitude = 'Marque a posição no mapa ou use sua localização.';
    else if (!dentroDeTeresina(posicao)) e.latitude = 'A posição precisa estar em Teresina.';
    return e;
  }

  async function enviar() {
    setErroGeral(null);
    const e = validar();
    setErros(e);
    if (Object.keys(e).length > 0 || !posicao) {
      setErroGeral('Confira os campos destacados.');
      return;
    }
    setEnviando(true);
    let arquivo: ArquivoLocal | null = null;
    try {
      if (foto) arquivo = await prepararFotoParaEnvio(foto, 'ponto.jpg');
      await api.sugerirPonto({
        nome: nome.trim(),
        tipo,
        endereco: endereco.trim(),
        latitude: posicao.latitude,
        longitude: posicao.longitude,
        horario: horario.trim() || undefined,
        telefone: telefone.trim() || undefined,
        fonte: fonte.trim() || undefined,
        materiais: selecionados,
        foto: arquivo,
      });
      setEnviado(true);
    } catch (err) {
      if (err instanceof ErroApi && Object.keys(err.campos).length > 0) {
        setErros(err.campos as Partial<Record<Campo, string>>);
        setErroGeral('A API recusou alguns campos. Confira abaixo.');
      } else {
        setErroGeral(mensagemDeErro(err));
      }
    } finally {
      // A cópia reduzida da foto só serve para o envio.
      apagarArquivo(arquivo?.uri);
      setEnviando(false);
    }
  }

  if (enviado) {
    return (
      <Tela>
        <Aviso tipo="sucesso">
          Sugestão enviada! Ela será analisada pela equipe antes de aparecer no mapa. Se for aprovada, você ganha 20
          pontos.
        </Aviso>
        <Botao titulo="Voltar ao mapa" aoPressionar={() => router.back()} />
      </Tela>
    );
  }

  return (
    <Tela>
      <Aviso tipo="info">
        Conhece um local que recebe recicláveis? Sugira aqui. A sugestão vai para aprovação e só aparece no mapa
        depois de conferida. Sugestão aprovada vale 20 pontos.
      </Aviso>
      {erroGeral ? <Aviso tipo="erro">{erroGeral}</Aviso> : null}

      <CampoTexto rotulo="Nome do local *" value={nome} onChangeText={setNome} erro={erros.nome} />

      <Subtitulo>Tipo *</Subtitulo>
      <GrupoChips rotulo="Tipo de ponto" unico>
        {TIPOS_PONTO.map((t) => (
          <Chip key={t} papel="radio" rotulo={DESCRICAO_TIPO_PONTO[t]} selecionado={tipo === t} aoPressionar={() => setTipo(t)} />
        ))}
      </GrupoChips>

      <CampoTexto rotulo="Endereço *" value={endereco} onChangeText={setEndereco} erro={erros.endereco} />

      <Subtitulo>Posição *</Subtitulo>
      <Paragrafo secundario>
        {mapa ? 'Toque no mapa para marcar o local ou use sua localização atual.' : 'Use sua localização atual (o mapa não está disponível no Expo Go).'}
      </Paragrafo>
      {mapa ? (
        <View style={estilos.mapa}>
          <mapa.MapaSelecao posicao={posicao} aoMudar={setPosicao} cameraRef={cameraRef} mostrarUsuario={mostrarUsuario} />
        </View>
      ) : null}
      <Botao titulo="Usar minha localização atual" variante="secundario" aoPressionar={usarLocalizacaoAtual} carregando={localizando} />
      {posicao ? (
        <Paragrafo secundario>
          Marcado: {posicao.latitude.toFixed(5)}, {posicao.longitude.toFixed(5)}
        </Paragrafo>
      ) : null}
      {erros.latitude || erros.longitude ? <Aviso tipo="erro">{erros.latitude ?? erros.longitude}</Aviso> : null}

      <Subtitulo>Materiais aceitos *</Subtitulo>
      <SeletorMateriais materiais={materiais} valor={selecionados} aoMudar={setSelecionados} />
      {erros.materiais ? <Aviso tipo="erro">{erros.materiais}</Aviso> : null}

      <CampoTexto rotulo="Horário de funcionamento" value={horario} onChangeText={setHorario} placeholder="Ex.: seg a sex, 8h às 17h" erro={erros.horario} />
      <CampoTexto rotulo="Telefone" value={telefone} onChangeText={setTelefone} keyboardType="phone-pad" erro={erros.telefone} />
      <CampoTexto rotulo="Fonte da informação" value={fonte} onChangeText={setFonte} placeholder="Ex.: placa no local, site da prefeitura" erro={erros.fonte} />

      <Subtitulo>Foto (opcional)</Subtitulo>
      {foto ? (
        <Image source={{ uri: foto }} style={estilos.foto} accessibilityLabel="Foto do local" accessibilityIgnoresInvertColors />
      ) : null}
      <View style={estilos.linha}>
        <Botao titulo="Tirar foto" variante="secundario" aoPressionar={tirarFoto} estilo={estilos.metade} />
        <Botao titulo="Da galeria" variante="secundario" aoPressionar={escolherFoto} estilo={estilos.metade} />
      </View>
      {foto ? <Botao titulo="Remover foto" variante="texto" aoPressionar={() => setFoto(null)} /> : null}

      <Botao titulo="Enviar sugestão" aoPressionar={enviar} carregando={enviando} />
    </Tela>
  );
}

const estilos = StyleSheet.create({
  mapa: { height: 280 },
  foto: { width: '100%', aspectRatio: 4 / 3, borderRadius: 12 },
  linha: { flexDirection: 'row', gap: espaco.m },
  metade: { flex: 1 },
});
