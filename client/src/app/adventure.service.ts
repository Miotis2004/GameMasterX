import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpEvent } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AdventureResult,
  AdventureStatus,
} from '../../../contracts/adventure';

/**
 * Error surfaced by the adventure import endpoint. Mirrors the shape of the
 * server-side {@code com.gamemasterx.server.exception.ErrorResponse} returned
 * for {@code 400 VALIDATION_ERROR}, {@code 409 CONFLICT} and related responses.
 */
export interface AdventureImportError {
  status: number;
  errorCode?: string;
  message: string;
  fieldErrors?: { field: string; message: string }[];
}

/**
 * Client service for the adventure library, detail and import endpoints.
 *
 * <p>The adventure library lists imported adventures via
 * {@link listAdventures}; the adventure-detail screen reads a single adventure
 * via {@link getAdventure}; and the import screen uploads a local package via
 * {@link importAdventure}. All traffic goes to the backend API on port 5172.</p>
 */
@Injectable({ providedIn: 'root' })
export class AdventureService {
  private readonly http = inject(HttpClient);
  private readonly apiBase = 'http://localhost:5172/api';

  /** Lists every adventure imported into the library. */
  listAdventures(): Observable<AdventureResult[]> {
    return this.http.get<AdventureResult[]>(`${this.apiBase}/adventures`);
  }

  /** Reads a single adventure by its stable identifier. */
  getAdventure(id: string): Observable<AdventureResult> {
    return this.http.get<AdventureResult>(`${this.apiBase}/adventures/${encodeURIComponent(id)}`);
  }

  /**
   * Filters the library by lifecycle status. A {@code null} status returns all
   * adventures; otherwise the server restricts to the given status.
   */
  listAdventuresByStatus(status: AdventureStatus | null): Observable<AdventureResult[]> {
    return this.http.get<AdventureResult[]>(`${this.apiBase}/adventures`, {
      params: status ? { status } : undefined,
    });
  }

  /**
   * Uploads a local adventure package (a ZIP archive containing an
   * {@code adventure.json} manifest) and imports it.
   *
   * @param packageFile the ZIP archive to import.
   * @param overwrite   when {@code true}, an adventure with a colliding id is
   *                    replaced; when {@code false} (the default) the import is
   *                    create-only and a collision is reported as an error.
   */
  importAdventure(packageFile: File, overwrite = false): Observable<AdventureResult> {
    const form = new FormData();
    form.append('package', packageFile);
    form.append('overwrite', String(overwrite));
    return this.http.post<AdventureResult>(`${this.apiBase}/adventures/import`, form, {
      observe: 'events',
      reportProgress: true,
    }) as unknown as Observable<AdventureResult>;
  }

  /**
   * Uploads a package and reports progress as {@link HttpEvent}s, so the import
   * screen can show an upload progress indicator.
   */
  importAdventureProgress(
    packageFile: File,
    overwrite = false
  ): Observable<HttpEvent<AdventureResult>> {
    const form = new FormData();
    form.append('package', packageFile);
    form.append('overwrite', String(overwrite));
    return this.http.post<AdventureResult>(`${this.apiBase}/adventures/import`, form, {
      observe: 'events',
      reportProgress: true,
    });
  }

  /** Backs up an adventure to the server. */
  backupAdventure(id: string): Observable<void> {
    return this.http.post<void>(`${this.apiBase}/adventures/${encodeURIComponent(id)}/backup`, {});
  }

  /** Exports an adventure as a file download. */
  exportAdventure(id: string): Observable<Blob> {
    return this.http.get<Blob>(`${this.apiBase}/adventures/${encodeURIComponent(id)}/export`, { responseType: 'blob' as any });
  }
}

/**
 * Extracts a human-readable error message from an HTTP error event, including
 * the server's {@code fieldErrors} when present, so the caller can surface a
 * precise, actionable message.
 */
export function toImportError(error: unknown): AdventureImportError {
  const status = extractStatus(error);

  // Server error bodies look like { message, fieldErrors? } for import failures.
  const body = extractErrorBody(error);
  const fieldErrors = extractFieldErrors(body);

  let message = resolveMessage(body, status, error);
  if (fieldErrors.length > 0) {
    const locations = fieldErrors.map((f) => `${f.field}: ${f.message}`).join('; ');
    message = fieldErrors.length === 1 ? locations : `${message} — ${locations}`;
  }

  return { status, message, fieldErrors: fieldErrors.length ? fieldErrors : undefined };
}

function extractStatus(error: unknown): number {
  const anyErr = error as { status?: number; error?: { status?: number } } | null;
  if (anyErr && typeof anyErr.status === 'number') {
    return anyErr.status;
  }
  if (anyErr?.error && typeof anyErr.error.status === 'number') {
    return anyErr.error.status;
  }
  return 0;
}

function extractErrorBody(error: unknown): unknown {
  const anyErr = error as { error?: unknown } | null;
  const body = anyErr?.error;
  if (body && typeof body === 'object' && !Array.isArray(body)) {
    return body as Record<string, unknown>;
  }
  return undefined;
}

function extractFieldErrors(body: unknown): { field: string; message: string }[] {
  if (!body || typeof body !== 'object') {
    return [];
  }
  const raw = (body as Record<string, unknown>)['fieldErrors'];
  if (Array.isArray(raw)) {
    return raw
      .filter((entry): entry is { field: string; message: string } =>
        entry && typeof entry === 'object' && 'field' in entry && 'message' in entry)
      .map((entry) => ({ field: String((entry as any).field), message: String((entry as any).message) }));
  }
  return [];
}

function resolveMessage(body: unknown, status: number, error: unknown): string {
  if (body && typeof body === 'object') {
    const candidate = (body as Record<string, unknown>)['message'];
    if (typeof candidate === 'string' && candidate.trim().length > 0) {
      return candidate;
    }
  }
  const anyErr = error as { message?: unknown } | null;
  if (anyErr && typeof anyErr.message === 'string') {
    return anyErr.message;
  }
  if (status === 0) {
    return 'Unable to connect to the server. Please check your network connection.';
  }
  if (status === 409) {
    return 'An adventure with the same identifier already exists. Turn on "overwrite" to replace it.';
  }
  if (status >= 500) {
    return 'The import could not be processed. Please try again later.';
  }
  return 'The adventure package could not be imported. Please check the file and try again.';
}
