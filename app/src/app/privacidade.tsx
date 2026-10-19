import { Link } from 'expo-router';
import { StyleSheet } from 'react-native';

import { AvisoPrivacidade } from '../componentes/AvisoPrivacidade';
import { Paragrafo, Tela, Titulo } from '../componentes/basicos';
import { cores, fonte } from '../componentes/tema';

export default function Privacidade() {
  return (
    <Tela>
      <Titulo>Privacidade</Titulo>
      <AvisoPrivacidade />
      <Paragrafo secundario>
        Os dados são usados somente para orientar o descarte, contabilizar pontos e melhorar o modelo de
        identificação (no caso das fotos enviadas com consentimento). Não vendemos nem repassamos seus dados.
      </Paragrafo>
      <Paragrafo secundario>
        Única comunicação com terceiros: as imagens do mapa (tiles) são baixadas diretamente dos servidores do
        OpenStreetMap, que recebem o endereço IP do aparelho e a área do mapa exibida, como em qualquer acesso a um
        site. Nenhum dado da sua conta é enviado a eles.
      </Paragrafo>
      <Link href="/creditos" style={estilos.link}>
        Créditos e licenças
      </Link>
    </Tela>
  );
}

const estilos = StyleSheet.create({
  link: { color: cores.info, fontSize: fonte.normal, textDecorationLine: 'underline', paddingVertical: 12 },
});
