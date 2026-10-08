import { LIMITES_TERESINA } from '../config';

export interface Coordenadas {
  latitude: number;
  longitude: number;
}

/** Distância em metros entre duas coordenadas (fórmula de haversine). */
export function distanciaMetros(a: Coordenadas, b: Coordenadas): number {
  const R = 6371000;
  const rad = (g: number) => (g * Math.PI) / 180;
  const dLat = rad(b.latitude - a.latitude);
  const dLon = rad(b.longitude - a.longitude);
  const h =
    Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.latitude)) * Math.cos(rad(b.latitude)) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

export function formatarDistancia(metros: number): string {
  if (metros < 1000) return `${Math.round(metros)} m`;
  return `${(metros / 1000).toFixed(1).replace('.', ',')} km`;
}

export function dentroDeTeresina(c: Coordenadas): boolean {
  return (
    c.latitude >= LIMITES_TERESINA.latMin &&
    c.latitude <= LIMITES_TERESINA.latMax &&
    c.longitude >= LIMITES_TERESINA.lonMin &&
    c.longitude <= LIMITES_TERESINA.lonMax
  );
}
