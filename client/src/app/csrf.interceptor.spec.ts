import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HTTP_INTERCEPTORS, HttpClient } from '@angular/common/http';
import { CsrfInterceptor } from './csrf.interceptor';

describe('CsrfInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        { provide: HTTP_INTERCEPTORS, useClass: CsrfInterceptor, multi: true }
      ]
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should fetch csrf token and attach it to unsafe request', () => {
    http.post('http://localhost:5172/api/test', {}).subscribe();

    // The interceptor should first fetch the CSRF token
    const tokenReq = httpMock.expectOne('http://localhost:5172/api/csrf/token');
    expect(tokenReq.request.method).toBe('GET');
    expect(tokenReq.request.withCredentials).toBe(true);
    tokenReq.flush({ csrfToken: 'fake-token-123' });

    // Then it should make the actual POST request with the token attached
    const mainReq = httpMock.expectOne('http://localhost:5172/api/test');
    expect(mainReq.request.method).toBe('POST');
    expect(mainReq.request.headers.get('X-CSRF-Token')).toBe('fake-token-123');
    expect(mainReq.request.withCredentials).toBe(true);
    mainReq.flush({});
  });

  it('should not fetch csrf token for safe methods (GET)', () => {
    http.get('http://localhost:5172/api/test').subscribe();

    // There should only be the GET request, no CSRF token fetch
    const req = httpMock.expectOne('http://localhost:5172/api/test');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.has('X-CSRF-Token')).toBe(false);
    expect(req.request.withCredentials).toBe(true);
    req.flush({});
  });
});
