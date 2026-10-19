import { Linking, Pressable, StyleSheet, Text } from 'react-native';

import { Cartao, Paragrafo, Subtitulo, Tela, Titulo } from '../componentes/basicos';
import { ALVO_TOQUE, cores, fonte } from '../componentes/tema';

/** Item de crédito com link opcional (abre no navegador). */
function Credito({ titulo, texto, url }: { titulo: string; texto: string; url?: string }) {
  return (
    <Cartao>
      <Text style={estilos.titulo}>{titulo}</Text>
      <Paragrafo secundario>{texto}</Paragrafo>
      {url ? (
        <Pressable accessibilityRole="link" onPress={() => Linking.openURL(url)} style={estilos.alvoLink}>
          <Text style={estilos.link}>{url}</Text>
        </Pressable>
      ) : null}
    </Cartao>
  );
}

/** Atribuições exigidas pelas licenças dos dados, do modelo e das bibliotecas (ver ml/DATASETS.md). */
export default function Creditos() {
  return (
    <Tela>
      <Titulo>Créditos e licenças</Titulo>

      <Subtitulo>Dados de treino do modelo</Subtitulo>
      <Credito
        titulo="TrashNet — licença MIT"
        texto="THUNG, G.; YANG, M. Classification of Trash for Recyclability Status. CS229 Project Report, Stanford University, 2016."
        url="https://github.com/garythung/trashnet"
      />
      <Credito
        titulo="RealWaste — CC BY 4.0"
        texto="SINGLE, S.; IRANMANESH, S.; RAAD, R. RealWaste: A Novel Real-Life Data Set for Landfill Waste Classification Using Deep Learning. Information, v. 14, n. 12, p. 633, 2023. Dataset: UCI Machine Learning Repository (Wollongong City Council), DOI 10.24432/C5SS4G."
        url="https://doi.org/10.24432/C5SS4G"
      />
      <Credito
        titulo="TACO — CC BY 4.0"
        texto="PROENÇA, P. F.; SIMÕES, P. TACO: Trash Annotations in Context for Litter Detection. arXiv:2003.06975, 2020. Dataset: Zenodo 3587843."
        url="https://doi.org/10.5281/zenodo.3587843"
      />
      <Credito
        titulo="GIZ e-waste dataset (COCO labels) — CC BY 4.0"
        texto="Deutsche Gesellschaft für Internationale Zusammenarbeit (GIZ). e-waste dataset with COCO labels. Hugging Face, 2025."
        url="https://huggingface.co/datasets/GIZ/e-waste-dataset-COCO-labels"
      />
      <Paragrafo secundario>
        As imagens foram redimensionadas, recortadas e agrupadas nas 7 classes do app. Os autores dos dados não
        endossam este aplicativo.
      </Paragrafo>

      <Subtitulo>Modelo</Subtitulo>
      <Credito
        titulo="MobileNetV3 (torchvision) — licença BSD 3-Clause"
        texto="Arquitetura e pesos pré-treinados do torchvision (PyTorch), ajustados para as classes do ReciclaTHE e exportados para ONNX."
        url="https://github.com/pytorch/vision"
      />

      <Subtitulo>Mapa e pontos de coleta</Subtitulo>
      <Credito
        titulo="© colaboradores do OpenStreetMap — ODbL"
        texto="Parte dos pontos de coleta vem do OpenStreetMap, disponível sob a Open Database License (ODbL). As imagens do mapa (tiles) são servidas pela OpenStreetMap Foundation."
        url="https://www.openstreetmap.org/copyright"
      />

      <Subtitulo>Software livre</Subtitulo>
      <Credito
        titulo="Bibliotecas de código aberto"
        texto="React Native, Expo e Expo Router (MIT), MapLibre React Native (MIT) e MapLibre Native (BSD-2-Clause), ONNX Runtime (MIT), jpeg-js (BSD-3-Clause), AsyncStorage e NetInfo (MIT), @expo/vector-icons (MIT) com Material Design Icons (Apache 2.0), entre outras. As licenças completas acompanham cada pacote."
      />
    </Tela>
  );
}

const estilos = StyleSheet.create({
  titulo: { fontSize: fonte.normal, fontWeight: '700', color: cores.texto },
  alvoLink: { minHeight: ALVO_TOQUE, justifyContent: 'center' },
  link: { color: cores.info, fontSize: fonte.pequena, textDecorationLine: 'underline' },
});
