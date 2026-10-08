import { Redirect } from 'expo-router';
import { ActivityIndicator, View } from 'react-native';

import { cores } from '../componentes/tema';
import { useSessao } from '../contexto/SessaoContexto';

/** Decide a tela inicial conforme a sessão salva. */
export default function Inicio() {
  const { carregando, autenticado } = useSessao();
  if (carregando) {
    return (
      <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator size="large" color={cores.primaria} accessibilityLabel="Carregando" />
      </View>
    );
  }
  return <Redirect href={autenticado ? '/identificar' : '/entrar'} />;
}
