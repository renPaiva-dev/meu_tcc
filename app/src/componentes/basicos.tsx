/** Componentes de interface pequenos e reutilizáveis. */
import type { ReactNode } from 'react';
import {
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  type StyleProp,
  type TextInputProps,
  type ViewStyle,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ALVO_TOQUE, cores, espaco, fonte } from './tema';

// ---------- Tela com rolagem e pull-to-refresh ----------

interface PropsTela {
  children: ReactNode;
  atualizando?: boolean;
  aoAtualizar?: () => void;
  /** As abas e o cabeçalho já tratam a borda superior. */
  bordas?: ('top' | 'bottom' | 'left' | 'right')[];
}

export function Tela({ children, atualizando, aoAtualizar, bordas = ['left', 'right'] }: PropsTela) {
  return (
    <SafeAreaView style={estilos.tela} edges={bordas}>
      <ScrollView
        contentContainerStyle={estilos.conteudo}
        keyboardShouldPersistTaps="handled"
        refreshControl={
          aoAtualizar ? (
            <RefreshControl refreshing={!!atualizando} onRefresh={aoAtualizar} colors={[cores.primaria]} />
          ) : undefined
        }
      >
        {children}
      </ScrollView>
    </SafeAreaView>
  );
}

// ---------- Textos ----------

export function Titulo({ children }: { children: ReactNode }) {
  return (
    <Text style={estilos.titulo} accessibilityRole="header">
      {children}
    </Text>
  );
}

export function Subtitulo({ children }: { children: ReactNode }) {
  return (
    <Text style={estilos.subtitulo} accessibilityRole="header">
      {children}
    </Text>
  );
}

export function Paragrafo({ children, secundario }: { children: ReactNode; secundario?: boolean }) {
  return <Text style={[estilos.paragrafo, secundario && estilos.secundario]}>{children}</Text>;
}

// ---------- Cartão ----------

export function Cartao({ children, estilo }: { children: ReactNode; estilo?: StyleProp<ViewStyle> }) {
  return <View style={[estilos.cartao, estilo]}>{children}</View>;
}

// ---------- Aviso (banner) ----------

type TipoAviso = 'info' | 'alerta' | 'erro' | 'sucesso';

const CORES_AVISO: Record<TipoAviso, { fundo: string; texto: string }> = {
  info: { fundo: cores.infoClaro, texto: cores.info },
  alerta: { fundo: cores.alertaClaro, texto: cores.alerta },
  erro: { fundo: cores.perigoClaro, texto: cores.perigo },
  sucesso: { fundo: cores.primariaClara, texto: cores.primaria },
};

export function Aviso({ tipo = 'info', children }: { tipo?: TipoAviso; children: ReactNode }) {
  const c = CORES_AVISO[tipo];
  return (
    <View
      style={[estilos.aviso, { backgroundColor: c.fundo, borderLeftColor: c.texto }]}
      accessibilityRole={tipo === 'erro' ? 'alert' : 'text'}
      accessibilityLiveRegion="polite"
    >
      <Text style={[estilos.avisoTexto, { color: c.texto }]}>{children}</Text>
    </View>
  );
}

// ---------- Campo de texto ----------

interface PropsCampo extends TextInputProps {
  rotulo: string;
  erro?: string | null;
}

export function CampoTexto({ rotulo, erro, style, ...props }: PropsCampo) {
  return (
    <View style={estilos.campo}>
      <Text style={estilos.rotuloCampo}>{rotulo}</Text>
      <TextInput
        accessibilityLabel={rotulo}
        accessibilityHint={erro ?? undefined}
        placeholderTextColor={cores.textoSecundario}
        style={[estilos.entrada, erro ? estilos.entradaErro : null, style]}
        {...props}
      />
      {erro ? <Text style={estilos.erroCampo}>{erro}</Text> : null}
    </View>
  );
}

// ---------- Chip selecionável ----------

interface PropsChip {
  rotulo: string;
  selecionado: boolean;
  aoPressionar: () => void;
  cor?: string;
  /** Use 'radio' em grupos de escolha única. */
  papel?: 'checkbox' | 'radio';
}

export function Chip({ rotulo, selecionado, aoPressionar, cor, papel = 'checkbox' }: PropsChip) {
  return (
    <Pressable
      accessibilityRole={papel}
      accessibilityLabel={rotulo}
      accessibilityState={papel === 'radio' ? { selected: selecionado } : { checked: selecionado }}
      onPress={aoPressionar}
      hitSlop={4}
      style={[estilos.chip, selecionado && estilos.chipSelecionado]}
    >
      {cor ? <View style={[estilos.bolinha, { backgroundColor: cor }]} /> : null}
      <Text style={[estilos.chipTexto, selecionado && estilos.chipTextoSelecionado]}>
        {selecionado ? '✓ ' : ''}
        {rotulo}
      </Text>
    </Pressable>
  );
}

export function GrupoChips({ children, rotulo, unico }: { children: ReactNode; rotulo?: string; unico?: boolean }) {
  return (
    <View accessibilityRole={unico ? 'radiogroup' : undefined} accessibilityLabel={rotulo} style={estilos.grupoChips}>
      {children}
    </View>
  );
}

// ---------- Linha rótulo/valor ----------

export function Info({ rotulo, valor }: { rotulo: string; valor?: string | null }) {
  if (!valor) return null;
  return (
    <View style={estilos.info}>
      <Text style={estilos.infoRotulo}>{rotulo}</Text>
      <Text style={estilos.infoValor}>{valor}</Text>
    </View>
  );
}

export const estilos = StyleSheet.create({
  tela: { flex: 1, backgroundColor: cores.fundo },
  conteudo: { padding: espaco.l, gap: espaco.m, paddingBottom: espaco.xl * 2 },
  titulo: { fontSize: fonte.titulo, fontWeight: '800', color: cores.texto },
  subtitulo: { fontSize: fonte.media, fontWeight: '700', color: cores.texto, marginTop: espaco.s },
  paragrafo: { fontSize: fonte.normal, color: cores.texto, lineHeight: 22 },
  secundario: { color: cores.textoSecundario, fontSize: fonte.pequena },
  cartao: {
    backgroundColor: cores.fundoSecundario,
    borderRadius: 12,
    padding: espaco.l,
    gap: espaco.s,
  },
  aviso: { borderLeftWidth: 4, borderRadius: 8, padding: espaco.m },
  avisoTexto: { fontSize: fonte.pequena, fontWeight: '600', lineHeight: 20 },
  campo: { gap: espaco.xs },
  rotuloCampo: { fontSize: fonte.pequena, fontWeight: '700', color: cores.texto },
  entrada: {
    minHeight: ALVO_TOQUE,
    borderWidth: 1,
    borderColor: cores.borda,
    borderRadius: 8,
    paddingHorizontal: espaco.m,
    fontSize: fonte.normal,
    color: cores.texto,
    backgroundColor: cores.fundo,
  },
  entradaErro: { borderColor: cores.perigo, borderWidth: 2 },
  erroCampo: { color: cores.perigo, fontSize: fonte.pequena },
  chip: {
    minHeight: 40,
    flexDirection: 'row',
    alignItems: 'center',
    gap: espaco.xs,
    paddingHorizontal: espaco.m,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: cores.borda,
    backgroundColor: cores.fundo,
  },
  chipSelecionado: { backgroundColor: cores.primaria, borderColor: cores.primaria },
  chipTexto: { fontSize: fonte.pequena, color: cores.texto, fontWeight: '600' },
  chipTextoSelecionado: { color: cores.branco },
  bolinha: { width: 12, height: 12, borderRadius: 6, borderWidth: 1, borderColor: cores.branco },
  grupoChips: { flexDirection: 'row', flexWrap: 'wrap', gap: espaco.s },
  info: { gap: 2 },
  infoRotulo: { fontSize: fonte.pequena, fontWeight: '700', color: cores.textoSecundario },
  infoValor: { fontSize: fonte.normal, color: cores.texto, lineHeight: 22 },
});
