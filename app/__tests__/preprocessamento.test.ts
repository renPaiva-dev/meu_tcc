import {
  base64ParaBytes,
  recorteCentralQuadrado,
  rgbaParaNCHW,
  softmax,
  topK,
  validarRotulos,
} from '../src/classificacao/preprocessamento';
import { ClassificadorDesenvolvimento } from '../src/classificacao/classificadorDesenvolvimento';

const MEDIA: [number, number, number] = [0.485, 0.456, 0.406];
const DESVIO: [number, number, number] = [0.229, 0.224, 0.225];
const CLASSES = ['PLASTICO', 'PAPEL', 'METAL', 'VIDRO', 'ELETRONICO', 'ORGANICO', 'REJEITO'];

describe('rgbaParaNCHW', () => {
  it('separa os canais em planos (NCHW) e descarta o alfa', () => {
    // Imagem 2x1: pixel 0 = (255, 0, 0, 255), pixel 1 = (0, 255, 51, 0)
    const rgba = new Uint8Array([255, 0, 0, 255, 0, 255, 51, 0]);
    const t = rgbaParaNCHW(rgba, 2, 1, [0, 0, 0], [1, 1, 1]);
    expect(t).toBeInstanceOf(Float32Array);
    expect(t.length).toBe(6);
    // plano R, depois G, depois B
    [1, 0, 0, 1, 0, 0.2].forEach((esperado, i) => expect(t[i]).toBeCloseTo(esperado, 6));
  });

  it('normaliza com (pixel/255 - média) / desvio', () => {
    const rgba = new Uint8Array([255, 128, 0, 255]);
    const t = rgbaParaNCHW(rgba, 1, 1, MEDIA, DESVIO);
    expect(t[0]).toBeCloseTo((1 - 0.485) / 0.229, 5);
    expect(t[1]).toBeCloseTo((128 / 255 - 0.456) / 0.224, 5);
    expect(t[2]).toBeCloseTo((0 - 0.406) / 0.225, 5);
  });

  it('gera o tamanho [1,3,224,224] esperado pelo modelo', () => {
    const rgba = new Uint8Array(224 * 224 * 4).fill(128);
    const t = rgbaParaNCHW(rgba, 224, 224, MEDIA, DESVIO);
    expect(t.length).toBe(3 * 224 * 224);
    // Todos os pixels iguais: cada plano é constante
    expect(t[0]).toBeCloseTo(t[224 * 224 - 1], 6);
    expect(t[224 * 224]).toBeCloseTo((128 / 255 - 0.456) / 0.224, 5);
  });

  it('recusa buffer menor que largura x altura x 4', () => {
    expect(() => rgbaParaNCHW(new Uint8Array(10), 2, 2, MEDIA, DESVIO)).toThrow();
  });
});

describe('softmax', () => {
  it('soma 1 e preserva a ordem', () => {
    const p = softmax([1, 2, 3]);
    expect(p.reduce((a, b) => a + b, 0)).toBeCloseTo(1, 10);
    expect(p[2]).toBeGreaterThan(p[1]);
    expect(p[1]).toBeGreaterThan(p[0]);
    expect(p[0]).toBeCloseTo(0.09003057, 6);
  });

  it('é numericamente estável com logits grandes', () => {
    const p = softmax([1000, 1001, 1002]);
    expect(p.every(Number.isFinite)).toBe(true);
    expect(p[2]).toBeCloseTo(0.66524096, 6);
  });

  it('logits iguais dão probabilidades uniformes', () => {
    const p = softmax(new Float32Array(7));
    p.forEach((v) => expect(v).toBeCloseTo(1 / 7, 6));
  });
});

describe('topK', () => {
  it('devolve as 3 classes mais prováveis em ordem decrescente', () => {
    const probs = [0.05, 0.4, 0.1, 0.3, 0.05, 0.05, 0.05];
    const top = topK(probs, CLASSES, 3);
    expect(top.map((p) => p.classe)).toEqual(['PAPEL', 'VIDRO', 'METAL']);
    expect(top[0].probabilidade).toBe(0.4);
  });

  it('exige mesma quantidade de probabilidades e classes', () => {
    expect(() => topK([0.5, 0.5], CLASSES)).toThrow();
  });
});

describe('recorteCentralQuadrado', () => {
  it('recorta o centro de uma foto em paisagem', () => {
    expect(recorteCentralQuadrado(4000, 3000)).toEqual({ originX: 500, originY: 0, width: 3000, height: 3000 });
  });
  it('recorta o centro de uma foto em retrato', () => {
    expect(recorteCentralQuadrado(3000, 4000)).toEqual({ originX: 0, originY: 500, width: 3000, height: 3000 });
  });
});

describe('base64ParaBytes', () => {
  it('decodifica com e sem padding', () => {
    expect(Array.from(base64ParaBytes('TWFu'))).toEqual([77, 97, 110]);
    expect(Array.from(base64ParaBytes('TWE='))).toEqual([77, 97]);
    expect(Array.from(base64ParaBytes('TQ=='))).toEqual([77]);
    expect(Array.from(base64ParaBytes('data:image/jpeg;base64,/9j/'))).toEqual([0xff, 0xd8, 0xff]);
  });
});

describe('validarRotulos', () => {
  const valido = {
    versao: 'mobilenetv3-v1',
    classes: CLASSES,
    entrada: { tamanho: 224, media: MEDIA, desvio: DESVIO, layout: 'NCHW', escala: 'pixel/255' },
  };
  it('aceita o formato do labels.json', () => {
    expect(validarRotulos(valido).classes).toHaveLength(7);
  });
  it('recusa layout diferente de NCHW e campos faltando', () => {
    expect(() => validarRotulos({ ...valido, entrada: { ...valido.entrada, layout: 'NHWC' } })).toThrow();
    expect(() => validarRotulos({ versao: 'x' })).toThrow();
  });
});

describe('ClassificadorDesenvolvimento (mock)', () => {
  it('produz top-3 coerente e se identifica como desenvolvimento', async () => {
    let semente = 1;
    const aleatorio = () => {
      semente = (semente * 16807) % 2147483647;
      return semente / 2147483647;
    };
    const c = new ClassificadorDesenvolvimento('teste', CLASSES, aleatorio);
    const r = await c.classificar('file:///foto.jpg');
    expect(r.origem).toBe('desenvolvimento');
    expect(r.top3).toHaveLength(3);
    expect(r.melhor).toEqual(r.top3[0]);
    expect(r.probabilidades.reduce((a, b) => a + b, 0)).toBeCloseTo(1, 6);
    expect(c.descricao).toMatch(/aleatório/);
  });
});

describe('JPEG → pixels → tensor (jpeg-js, como no app)', () => {
  it('decodifica um JPEG 224x224 e gera o tensor NCHW', () => {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const jpeg = require('jpeg-js') as typeof import('jpeg-js');
    const lado = 224;
    const rgba = new Uint8Array(lado * lado * 4);
    for (let i = 0; i < lado * lado; i++) {
      rgba.set([200, 30, 30, 255], i * 4); // vermelho
    }
    const codificado = jpeg.encode({ data: rgba, width: lado, height: lado }, 95);
    const decodificado = jpeg.decode(new Uint8Array(codificado.data), { useTArray: true, formatAsRGBA: true });
    expect(decodificado.width).toBe(lado);
    const t = rgbaParaNCHW(decodificado.data, decodificado.width, decodificado.height, MEDIA, DESVIO);
    expect(t.length).toBe(3 * lado * lado);
    const meio = (lado * lado) / 2;
    // Compressão JPEG altera um pouco os valores; o canal R deve continuar bem acima do G.
    expect(t[meio]).toBeCloseTo((200 / 255 - MEDIA[0]) / DESVIO[0], 0);
    expect(t[meio]).toBeGreaterThan(t[lado * lado + meio]);
  });
});
