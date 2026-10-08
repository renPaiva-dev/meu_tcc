import { AvisoPrivacidade } from '../componentes/AvisoPrivacidade';
import { Paragrafo, Tela, Titulo } from '../componentes/basicos';

export default function Privacidade() {
  return (
    <Tela>
      <Titulo>Privacidade</Titulo>
      <AvisoPrivacidade />
      <Paragrafo secundario>
        Os dados são usados somente para orientar o descarte, contabilizar pontos e melhorar o modelo de
        identificação (no caso das fotos enviadas com consentimento). Não há compartilhamento com terceiros.
      </Paragrafo>
    </Tela>
  );
}
