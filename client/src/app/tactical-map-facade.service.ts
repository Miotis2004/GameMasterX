import { Injectable, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { TacticalMapApiService, TacticalMapDto, TokenDto, RangeCell } from './tactical-map-api.service';

@Injectable({ providedIn: 'root' })
export class TacticalMapFacadeService {
  private readonly api = inject(TacticalMapApiService);

  private readonly _map$ = new BehaviorSubject<TacticalMapDto | null>(null);
  private readonly _loading$ = new BehaviorSubject<boolean>(false);
  private readonly _error$ = new BehaviorSubject<string | null>(null);

  readonly map$ = this._map$.asObservable();
  readonly loading$ = this._loading$.asObservable();
  readonly error$ = this._error$.asObservable();

  loadMap(mapId: string) {
    this._loading$.next(true);
    this._error$.next(null);
    this.api.getMap(mapId).subscribe({
      next: (map) => {
        this._map$.next(map);
        this._loading$.next(false);
      },
      error: (err) => {
        this._loading$.next(false);
        this._error$.next(this.resolveError(err));
      }
    });
  }

  moveToken(mapId: string, tokenId: string, x: number, y: number) {
    return this.api.updateToken(mapId, tokenId, { x, y });
  }

  getRangeOverlay(mapId: string, tokenId: string, range: number) {
    return this.api.getRangeOverlay(mapId, tokenId, range);
  }

  private resolveError(err: unknown): string {
    if (err && typeof err === 'object' && 'message' in err) {
      const message = (err as { message?: string }).message;
      if (typeof message === 'string' && message.trim()) {
        return message;
      }
    }
    return 'Request failed. Please try again later.';
  }

  clearError() {
    this._error$.next(null);
  }

  get currentMap(): TacticalMapDto | null {
    return this._map$.value;
  }
}
