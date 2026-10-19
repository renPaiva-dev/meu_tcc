import { ehDoOpenStreetMap } from '../src/api/tipos';
import { dentroDeTeresina, distanciaMetros, formatarDistancia } from '../src/utilitarios/geo';
import { corTextoSobre } from '../src/utilitarios/materiais';

describe('geo', () => {
  it('distância zero entre o mesmo ponto', () => {
    expect(distanciaMetros({ latitude: -5.09, longitude: -42.8 }, { latitude: -5.09, longitude: -42.8 })).toBe(0);
  });

  it('1 grau de latitude ≈ 111,2 km (haversine, R = 6371 km)', () => {
    const d = distanciaMetros({ latitude: 0, longitude: 0 }, { latitude: 1, longitude: 0 });
    expect(d).toBeCloseTo(111195, -1);
  });

  it('é simétrica e dá um valor plausível dentro de Teresina', () => {
    const centro = { latitude: -5.0892, longitude: -42.8019 };
    const ufpi = { latitude: -5.0567, longitude: -42.7967 };
    const ida = distanciaMetros(centro, ufpi);
    expect(ida).toBeCloseTo(distanciaMetros(ufpi, centro), 6);
    expect(ida).toBeGreaterThan(3500);
    expect(ida).toBeLessThan(3800);
  });

  it('formata metros e quilômetros com vírgula', () => {
    expect(formatarDistancia(149.6)).toBe('150 m');
    expect(formatarDistancia(2345)).toBe('2,3 km');
  });

  it('dentroDeTeresina usa o retângulo aceito pela API (limites inclusos)', () => {
    expect(dentroDeTeresina({ latitude: -5.0892, longitude: -42.8019 })).toBe(true);
    expect(dentroDeTeresina({ latitude: -4.75, longitude: -42.55 })).toBe(true);
    expect(dentroDeTeresina({ latitude: -3.73, longitude: -38.52 })).toBe(false); // Fortaleza
    expect(dentroDeTeresina({ latitude: -5.0892, longitude: -43.1 })).toBe(false);
  });
});

describe('corTextoSobre', () => {
  it('texto branco em fundos escuros e preto em fundos claros', () => {
    expect(corTextoSobre('#1565C0')).toBe('#FFFFFF'); // azul (papel)
    expect(corTextoSobre('#6D4C41')).toBe('#FFFFFF'); // marrom (orgânico)
    expect(corTextoSobre('#F9A825')).toBe('#000000'); // amarelo (metal)
    expect(corTextoSobre('#FFFFFF')).toBe('#000000');
    expect(corTextoSobre('000000')).toBe('#FFFFFF');
  });

  it('valor inválido cai no branco', () => {
    expect(corTextoSobre('vermelho')).toBe('#FFFFFF');
  });
});

describe('ehDoOpenStreetMap', () => {
  it('reconhece a fonte gerada pela importação do OSM', () => {
    expect(
      ehDoOpenStreetMap({
        fonte: 'OpenStreetMap (© colaboradores do OpenStreetMap, licença ODbL): https://www.openstreetmap.org/node/1',
      }),
    ).toBe(true);
    expect(ehDoOpenStreetMap({ fonte: 'Prefeitura de Teresina' })).toBe(false);
    expect(ehDoOpenStreetMap({ fonte: null })).toBe(false);
  });
});
