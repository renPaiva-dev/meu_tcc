import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { cores } from '../componentes/tema';
import { DadosProvider } from '../contexto/DadosContexto';
import { SessaoProvider } from '../contexto/SessaoContexto';

export default function LayoutRaiz() {
  return (
    <SafeAreaProvider>
      <SessaoProvider>
        <DadosProvider>
          <StatusBar style="light" />
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
            <Stack.Screen name="(abas)" options={{ headerShown: false }} />
            <Stack.Screen name="entrar" options={{ title: 'Entrar' }} />
            <Stack.Screen name="cadastro" options={{ title: 'Criar conta' }} />
            <Stack.Screen name="privacidade" options={{ title: 'Privacidade' }} />
            <Stack.Screen name="resultado" options={{ title: 'Resultado' }} />
            <Stack.Screen name="ponto/[id]" options={{ title: 'Ponto de coleta' }} />
            <Stack.Screen name="sugerir" options={{ title: 'Sugerir ponto' }} />
            <Stack.Screen name="como-ganhar-pontos" options={{ title: 'Como ganhar pontos' }} />
          </Stack>
        </DadosProvider>
      </SessaoProvider>
    </SafeAreaProvider>
  );
}
