/** Preparação de fotos para envio (feedback e sugestão de ponto). */
import { Directory, File, Paths } from 'expo-file-system';
import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';

import type { ArquivoLocal, DadosFeedback } from '../api/tipos';

/**
 * Reduz a foto (lado maior até 1280 px, JPEG 80%) para ficar bem abaixo do limite de 5 MB da API.
 * O arquivo gerado fica no cache do app; apague-o com `apagarArquivo` depois do envio.
 */
export async function prepararFotoParaEnvio(uri: string, nome = 'foto.jpg'): Promise<ArquivoLocal> {
  const original = await ImageManipulator.manipulate(uri).renderAsync();
  const maior = Math.max(original.width, original.height);
  const contexto = ImageManipulator.manipulate(uri);
  if (maior > 1280) {
    contexto.resize(original.width >= original.height ? { width: 1280 } : { height: 1280 });
  }
  const salvo = await (await contexto.renderAsync()).saveAsync({ format: SaveFormat.JPEG, compress: 0.8 });
  return { uri: salvo.uri, nome, tipo: 'image/jpeg' };
}

/**
 * Antes de enfileirar um feedback offline, copia a foto (se houver consentimento) para a pasta
 * de documentos do app, porque arquivos do cache podem ser apagados pelo sistema.
 */
export async function persistirFotoDoFeedback(dados: DadosFeedback): Promise<DadosFeedback> {
  if (!dados.consentimentoFoto || !dados.foto) return { ...dados, foto: null };
  try {
    const pasta = new Directory(Paths.document, 'fila-fotos');
    if (!pasta.exists) pasta.create({ intermediates: true, idempotent: true });
    const destino = new File(pasta, `${Date.now()}-${dados.foto.nome}`);
    await new File(dados.foto.uri).copy(destino);
    return { ...dados, foto: { ...dados.foto, uri: destino.uri } };
  } catch {
    return { ...dados, foto: null };
  }
}

/** Apaga um arquivo local (melhor esforço; ignora se já não existe). */
export function apagarArquivo(uri: string | null | undefined): void {
  if (!uri) return;
  try {
    const arquivo = new File(uri);
    if (arquivo.exists) arquivo.delete();
  } catch {
    // limpeza é "melhor esforço"
  }
}

/** Apaga fotos da fila que não são mais referenciadas (já enviadas ou descartadas). */
export function limparFotosOrfas(urisEmUso: string[]): void {
  try {
    const pasta = new Directory(Paths.document, 'fila-fotos');
    if (!pasta.exists) return;
    const emUso = new Set(urisEmUso);
    for (const item of pasta.list()) {
      if (item instanceof File && !emUso.has(item.uri)) item.delete();
    }
  } catch {
    // limpeza é "melhor esforço"
  }
}
