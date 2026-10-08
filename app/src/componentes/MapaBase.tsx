/**
 * Mapa base com MapLibre Native + tiles raster do OpenStreetMap (sem chave de API).
 * A atribuição "© OpenStreetMap" é obrigatória pela licença ODbL e fica visível no mapa.
 */
import {
  Camera,
  GeoJSONSource,
  Layer,
  Map,
  UserLocation,
  type CameraRef,
  type MapProps,
  type StyleSpecification,
} from '@maplibre/maplibre-react-native';
import { useMemo, type ReactNode, type Ref } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import type { PontoColeta } from '../api/tipos';
import { CENTRO_TERESINA } from '../config';
import type { Coordenadas } from '../utilitarios/geo';
import { cores } from './tema';

export const ESTILO_OSM: StyleSpecification = {
  version: 8,
  sources: {
    osm: {
      type: 'raster',
      tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
      tileSize: 256,
      maxzoom: 19,
      attribution: '© OpenStreetMap contributors',
    },
  },
  layers: [{ id: 'osm', type: 'raster', source: 'osm' }],
};

interface Props {
  children?: ReactNode;
  cameraRef?: Ref<CameraRef>;
  centro?: { latitude: number; longitude: number };
  zoom?: number;
  aoPressionar?: MapProps['onPress'];
  rotuloAcessivel: string;
}

export function MapaBase({ children, cameraRef, centro = CENTRO_TERESINA, zoom = 12, aoPressionar, rotuloAcessivel }: Props) {
  return (
    <View style={estilos.container} accessibilityLabel={rotuloAcessivel}>
      <Map
        style={estilos.mapa}
        mapStyle={ESTILO_OSM}
        onPress={aoPressionar}
        logo={false}
        attribution
        attributionPosition={{ bottom: 4, right: 4 }}
        compass
      >
        <Camera ref={cameraRef} initialViewState={{ center: [centro.longitude, centro.latitude], zoom }} />
        {children}
      </Map>
      {/* Atribuição também em texto, legível e acessível. */}
      <Text style={estilos.atribuicao} accessibilityRole="text">
        © colaboradores do OpenStreetMap
      </Text>
    </View>
  );
}

// ---------- Mapa com os pontos de coleta ----------

export interface PropsMapaPontos {
  pontos: PontoColeta[];
  /** Cor dos marcadores (ex.: cor do material filtrado). */
  cor?: string;
  centro?: Coordenadas;
  mostrarUsuario?: boolean;
  cameraRef?: Ref<CameraRef>;
  aoSelecionar: (idPonto: number) => void;
}

export function MapaPontos({ pontos, cor = cores.primaria, centro, mostrarUsuario, cameraRef, aoSelecionar }: PropsMapaPontos) {
  const dados = useMemo<GeoJSON.FeatureCollection<GeoJSON.Point>>(
    () => ({
      type: 'FeatureCollection',
      features: pontos.map((p) => ({
        type: 'Feature',
        id: p.id,
        properties: { id: p.id, nome: p.nome },
        geometry: { type: 'Point', coordinates: [p.longitude, p.latitude] },
      })),
    }),
    [pontos],
  );
  return (
    <MapaBase
      rotuloAcessivel={`Mapa com ${pontos.length} pontos de coleta. Use a lista abaixo para navegar pelos pontos.`}
      centro={centro}
      cameraRef={cameraRef}
    >
      <GeoJSONSource
        id="pontos"
        data={dados}
        onPress={(e) => {
          const id = e.nativeEvent.features[0]?.properties?.id;
          if (typeof id === 'number') aoSelecionar(id);
        }}
      >
        <Layer
          id="pontos-circulos"
          type="circle"
          paint={{
            'circle-radius': 9,
            'circle-color': cor,
            'circle-stroke-width': 2,
            'circle-stroke-color': '#FFFFFF',
          }}
        />
      </GeoJSONSource>
      {mostrarUsuario ? <UserLocation accuracy /> : null}
    </MapaBase>
  );
}

// ---------- Mapa para marcar uma posição (sugestão de ponto) ----------

export interface PropsMapaSelecao {
  posicao: Coordenadas | null;
  aoMudar: (c: Coordenadas) => void;
  cameraRef?: Ref<CameraRef>;
  mostrarUsuario?: boolean;
}

export function MapaSelecao({ posicao, aoMudar, cameraRef, mostrarUsuario }: PropsMapaSelecao) {
  const dados = useMemo<GeoJSON.FeatureCollection<GeoJSON.Point>>(
    () => ({
      type: 'FeatureCollection',
      features: posicao
        ? [{ type: 'Feature', properties: {}, geometry: { type: 'Point', coordinates: [posicao.longitude, posicao.latitude] } }]
        : [],
    }),
    [posicao],
  );
  return (
    <MapaBase
      rotuloAcessivel="Mapa: toque para marcar a posição do ponto de coleta"
      centro={posicao ?? undefined}
      zoom={posicao ? 15 : 12}
      cameraRef={cameraRef}
      aoPressionar={(e) => {
        const [longitude, latitude] = e.nativeEvent.lngLat;
        aoMudar({ latitude, longitude });
      }}
    >
      <GeoJSONSource id="selecao" data={dados}>
        <Layer
          id="selecao-circulo"
          type="circle"
          paint={{ 'circle-radius': 11, 'circle-color': cores.perigo, 'circle-stroke-width': 3, 'circle-stroke-color': '#FFFFFF' }}
        />
      </GeoJSONSource>
      {mostrarUsuario ? <UserLocation /> : null}
    </MapaBase>
  );
}

const estilos = StyleSheet.create({
  container: { flex: 1, minHeight: 240, borderRadius: 12, overflow: 'hidden', backgroundColor: cores.fundoSecundario },
  mapa: { flex: 1 },
  atribuicao: {
    position: 'absolute',
    left: 4,
    bottom: 4,
    fontSize: 11,
    color: cores.texto,
    backgroundColor: 'rgba(255,255,255,0.85)',
    paddingHorizontal: 4,
    borderRadius: 4,
  },
});
