import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { Redirect, Tabs } from 'expo-router';
import type { ColorValue } from 'react-native';

import { cores } from '../../componentes/tema';
import { useSessao } from '../../contexto/SessaoContexto';

type NomeIcone = keyof typeof MaterialCommunityIcons.glyphMap;

function icone(nome: NomeIcone) {
  return ({ color, size }: { color: ColorValue; size: number }) => (
    <MaterialCommunityIcons name={nome} color={color} size={size} />
  );
}

export default function LayoutAbas() {
  const { carregando, autenticado } = useSessao();
  if (!carregando && !autenticado) return <Redirect href="/entrar" />;

  return (
    <Tabs
      screenOptions={{
        headerStyle: { backgroundColor: cores.primaria },
        headerTintColor: cores.branco,
        headerTitleStyle: { fontWeight: '700' },
        tabBarActiveTintColor: cores.primaria,
        tabBarInactiveTintColor: cores.textoSecundario,
        tabBarLabelStyle: { fontSize: 13, fontWeight: '600' },
      }}
    >
      <Tabs.Screen
        name="identificar"
        options={{ title: 'Identificar', headerTitle: 'ReciclaTHE', tabBarIcon: icone('camera') }}
      />
      <Tabs.Screen name="mapa" options={{ title: 'Mapa', tabBarIcon: icone('map-marker-radius') }} />
      <Tabs.Screen name="perfil" options={{ title: 'Perfil', tabBarIcon: icone('account-circle') }} />
    </Tabs>
  );
}
