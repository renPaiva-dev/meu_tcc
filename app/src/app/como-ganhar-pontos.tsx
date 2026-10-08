import { Cartao, Paragrafo, Subtitulo, Tela, Titulo } from '../componentes/basicos';

/** Regras de pontuação (espelham TipoEvento.java na API). */
export default function ComoGanharPontos() {
  return (
    <Tela>
      <Titulo>Como ganhar pontos</Titulo>
      <Paragrafo>
        Só pontuam ações que o sistema consegue conferir. Assim o ranking reflete quem realmente ajuda a reciclagem em
        Teresina.
      </Paragrafo>

      <Cartao>
        <Subtitulo>+20 · Sugerir um ponto de coleta</Subtitulo>
        <Paragrafo>
          Indique um local que recebe recicláveis (aba Mapa → Sugerir ponto). Os pontos são creditados quando a equipe
          aprova a sugestão.
        </Paragrafo>
      </Cartao>

      <Cartao>
        <Subtitulo>+5 · Check-in em um ponto de coleta</Subtitulo>
        <Paragrafo>
          Ao entregar seus resíduos, abra o ponto no mapa e toque em “Fazer check-in”. É preciso estar a até 150 metros
          do local. Vale uma vez por dia em cada ponto.
        </Paragrafo>
      </Cartao>

      <Cartao>
        <Subtitulo>+2 · Responder se a identificação está certa</Subtitulo>
        <Paragrafo>
          Depois de identificar um resíduo, diga se o resultado está correto (ou qual era o material certo). Limite de 5
          respostas pontuadas por dia.
        </Paragrafo>
      </Cartao>

      <Cartao>
        <Subtitulo>Fotografar não dá pontos</Subtitulo>
        <Paragrafo>
          Tirar a foto de um item não prova que ele foi descartado corretamente, por isso a identificação em si não
          pontua.
        </Paragrafo>
      </Cartao>
    </Tela>
  );
}
