import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { Tabs } from 'expo-router';
import type { ColorValue } from 'react-native';

import { cores } from '../../componentes/tema';

type NomeIcone = keyof typeof MaterialCommunityIcons.glyphMap;

function icone(nome: NomeIcone) {
  const Icone = ({ color, size }: { color: ColorValue; size: number }) => (
    <MaterialCommunityIcons name={nome} color={color} size={size} />
  );
  Icone.displayName = `Icone(${nome})`;
  return Icone;
}

/** O acesso às abas é protegido no layout raiz (Stack.Protected). */
export default function LayoutAbas() {
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
