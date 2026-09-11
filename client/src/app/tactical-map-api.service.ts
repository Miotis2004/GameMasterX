import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface GridDefinitionDto {
  width: number;
  height: number;
  cellSize: number;
  gridType: string;
}

export interface TokenDto {
  id: string;
  name: string;
  ownerId: string;
  x: number;
  y: number;
  hidden: boolean;
  visibleTo: string[];
}

export interface TacticalMapDto {
  id: string;
  schemaVersion: number;
  createdAt: string;
  updatedAt: string;
  campaignId: string;
  name: string;
  gridDefinition: GridDefinitionDto;
  tokens: TokenDto[];
  revision: number;
}

export interface RangeCell {
  x: number;
  y: number;
  distance: number;
}

@Injectable({ providedIn: 'root' })
export class TacticalMapApiService {
  private readonly http = inject(HttpClient);
  private readonly apiBase = 'http://localhost:5172/api';

  getMap(id: string): Observable<TacticalMapDto> {
    return this.http.get<TacticalMapDto>(`${this.apiBase}/tactical/maps/${id}`);
  }

  updateToken(mapId: string, tokenId: string, token: Partial<TokenDto>): Observable<TacticalMapDto> {
    return this.http.patch<TacticalMapDto>(`${this.apiBase}/tactical/maps/${mapId}/tokens/${tokenId}`, token);
  }

  getRangeOverlay(mapId: string, tokenId: string, range: number): Observable<RangeCell[]> {
    return this.http.get<RangeCell[]>(`${this.apiBase}/tactical/maps/${mapId}/tokens/${tokenId}/range?range=${range}`);
  }
}
