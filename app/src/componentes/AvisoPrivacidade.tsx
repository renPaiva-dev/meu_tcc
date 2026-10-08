import { Cartao, Paragrafo, Subtitulo } from './basicos';

/** Resumo do que o app coleta (LGPD). Usado no cadastro e na tela de privacidade. */
export function AvisoPrivacidade() {
  return (
    <Cartao>
      <Subtitulo>Seus dados</Subtitulo>
      <Paragrafo>• Coletamos apenas nome, e-mail e a zona de Teresina que você escolher.</Paragrafo>
      <Paragrafo>
        • A identificação das fotos é feita no próprio aparelho. Fotos só são enviadas se você marcar o
        consentimento ao responder “A identificação está certa?”.
      </Paragrafo>
      <Paragrafo>
        • A localização é usada na hora (pontos próximos, check-in, sugestão de ponto) e não é armazenada como
        coordenada no seu perfil.
      </Paragrafo>
      <Paragrafo>• No ranking aparece só o primeiro nome e a inicial do sobrenome.</Paragrafo>
      <Paragrafo>• Você pode excluir sua conta e seus dados a qualquer momento, na aba Perfil.</Paragrafo>
    </Cartao>
  );
}
