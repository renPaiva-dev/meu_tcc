import { ActivityIndicator, Pressable, StyleSheet, Text, type StyleProp, type ViewStyle } from 'react-native';

import { ALVO_TOQUE, cores, espaco, fonte } from './tema';

type Variante = 'primario' | 'secundario' | 'perigo' | 'texto';

interface Props {
  titulo: string;
  aoPressionar: () => void;
  variante?: Variante;
  carregando?: boolean;
  desabilitado?: boolean;
  /** Descrição extra para leitores de tela. */
  dica?: string;
  estilo?: StyleProp<ViewStyle>;
}

export function Botao({ titulo, aoPressionar, variante = 'primario', carregando, desabilitado, dica, estilo }: Props) {
  const inativo = desabilitado || carregando;
  const corTexto = variante === 'primario' || variante === 'perigo' ? cores.branco : cores.primaria;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={titulo}
      accessibilityHint={dica}
      accessibilityState={{ disabled: !!inativo, busy: !!carregando }}
      disabled={inativo}
      onPress={aoPressionar}
      style={({ pressed }) => [
        estilos.base,
        estilos[variante],
        inativo && estilos.inativo,
        pressed && estilos.pressionado,
        estilo,
      ]}
    >
      {carregando ? (
        <ActivityIndicator color={corTexto} />
      ) : (
        <Text style={[estilos.rotulo, { color: corTexto }]}>{titulo}</Text>
      )}
    </Pressable>
  );
}

const estilos = StyleSheet.create({
  base: {
    minHeight: ALVO_TOQUE,
    borderRadius: 10,
    paddingHorizontal: espaco.l,
    paddingVertical: espaco.m,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primario: { backgroundColor: cores.primaria },
  secundario: { backgroundColor: cores.fundo, borderWidth: 2, borderColor: cores.primaria },
  perigo: { backgroundColor: cores.perigo },
  texto: { backgroundColor: 'transparent' },
  inativo: { opacity: 0.5 },
  pressionado: { opacity: 0.8 },
  rotulo: { fontSize: fonte.normal, fontWeight: '700', textAlign: 'center' },
});
