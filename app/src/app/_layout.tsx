import { Stack, type ErrorBoundaryProps } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { StyleSheet, Text, View } from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';

import { Botao } from '../componentes/Botao';
import { cores, espaco, fonte } from '../componentes/tema';
import { DadosProvider } from '../contexto/DadosContexto';
import { SessaoProvider, useSessao } from '../contexto/SessaoContexto';

/**
 * Tela mostrada quando algum componente lança um erro não tratado (o Expo Router usa o
 * ErrorBoundary exportado pelo layout). "Tentar novamente" renderiza a rota de novo.
 */
export function ErrorBoundary({ error, retry }: ErrorBoundaryProps) {
  return (
    <SafeAreaView style={estilos.erro}>
      <Text style={estilos.erroTitulo} accessibilityRole="header">
        Algo deu errado
      </Text>
      <Text style={estilos.erroTexto}>
        O app encontrou um problema inesperado. Seus envios pendentes continuam guardados.
      </Text>
      {__DEV__ ? <Text style={estilos.erroDetalhe}>{error.message}</Text> : null}
      <View style={{ alignSelf: 'stretch' }}>
        <Botao titulo="Tentar novamente" aoPressionar={() => void retry()} />
      </View>
    </SafeAreaView>
  );
}

/** Stack com as telas que exigem sessão protegidas: ao sair/expirar, o Router volta ao index. */
function Navegacao() {
  const { carregando, autenticado } = useSessao();
  // Enquanto a sessão salva é lida, não bloqueia (o index mostra o carregamento).
  const logado = carregando || autenticado;
  return (
    <Stack
      screenOptions={{
        headerStyle: { backgroundColor: cores.primaria },
        headerTintColor: cores.branco,
        headerTitleStyle: { fontWeight: '700' },
        headerBackButtonDisplayMode: 'minimal',
        contentStyle: { backgroundColor: cores.fundo },
      }}
    >
      <Stack.Screen name="index" options={{ headerShown: false }} />
      <Stack.Screen name="entrar" options={{ title: 'Entrar' }} />
      <Stack.Screen name="cadastro" options={{ title: 'Criar conta' }} />
      <Stack.Screen name="privacidade" options={{ title: 'Privacidade' }} />
      <Stack.Screen name="creditos" options={{ title: 'Créditos e licenças' }} />
      <Stack.Protected guard={logado}>
        <Stack.Screen name="(abas)" options={{ headerShown: false }} />
        <Stack.Screen name="resultado" options={{ title: 'Resultado' }} />
        <Stack.Screen name="ponto/[id]" options={{ title: 'Ponto de coleta' }} />
        <Stack.Screen name="sugerir" options={{ title: 'Sugerir ponto' }} />
        <Stack.Screen name="como-ganhar-pontos" options={{ title: 'Como ganhar pontos' }} />
      </Stack.Protected>
    </Stack>
  );
}

export default function LayoutRaiz() {
  return (
    <SafeAreaProvider>
      <SessaoProvider>
        <DadosProvider>
          <StatusBar style="light" />
          <Navegacao />
        </DadosProvider>
      </SessaoProvider>
    </SafeAreaProvider>
  );
}

const estilos = StyleSheet.create({
  erro: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: espaco.m,
    padding: espaco.xl,
    backgroundColor: cores.fundo,
  },
  erroTitulo: { fontSize: fonte.titulo, fontWeight: '800', color: cores.texto },
  erroTexto: { fontSize: fonte.normal, color: cores.texto, textAlign: 'center', lineHeight: 22 },
  erroDetalhe: { fontSize: fonte.pequena, color: cores.textoSecundario, textAlign: 'center' },
});
