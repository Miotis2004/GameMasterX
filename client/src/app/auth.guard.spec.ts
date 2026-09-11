import { TestBed } from '@angular/core/testing';
import { AuthGuard } from './auth.guard';
import { AuthFacadeService } from './auth-facade.service';
import { StatusService } from './status.service';
import { Router } from '@angular/router';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

describe('AuthGuard', () => {
  let guard: AuthGuard;
  let httpMock: HttpTestingController;
  let authFacade: jasmine.SpyObj<AuthFacadeService>;
  let router: jasmine.SpyObj<Router>;
  let statusService: jasmine.SpyObj<StatusService>;

  beforeEach(() => {
    const authSpy = jasmine.createSpyObj('AuthFacadeService', ['getIsAuthenticated']);
    // Setup signals properly for the mock
    authSpy.isAuthenticated = jasmine.createSpyObj('Signal', ['set', 'update']);
    authSpy.username = jasmine.createSpyObj('Signal', ['set', 'update']);

    const routerSpy = jasmine.createSpyObj('Router', ['createUrlTree']);
    const statusSpy = jasmine.createSpyObj('StatusService', ['setUnauthorized', 'setError']);

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        AuthGuard,
        { provide: AuthFacadeService, useValue: authSpy },
        { provide: Router, useValue: routerSpy },
        { provide: StatusService, useValue: statusSpy }
      ]
    });

    guard = TestBed.inject(AuthGuard);
    httpMock = TestBed.inject(HttpTestingController);
    authFacade = TestBed.inject(AuthFacadeService) as jasmine.SpyObj<AuthFacadeService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    statusService = TestBed.inject(StatusService) as jasmine.SpyObj<StatusService>;
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should return true if authenticated session exists', () => {
    guard.canActivate().subscribe(result => {
      expect(result).toBe(true);
      expect(authFacade.isAuthenticated.set).toHaveBeenCalledWith(true);
      expect(statusService.setUnauthorized).toHaveBeenCalledWith(false);
    });

    const req = httpMock.expectOne('http://localhost:5172/api/auth/session');
    expect(req.request.method).toBe('GET');
    req.flush({ authenticated: true, username: 'testuser' });
  });

  it('should redirect to login if session is unauthenticated', () => {
    const urlTree = {} as any;
    router.createUrlTree.and.returnValue(urlTree);

    guard.canActivate().subscribe(result => {
      expect(result).toBe(urlTree);
      expect(authFacade.isAuthenticated.set).toHaveBeenCalledWith(false);
      expect(statusService.setUnauthorized).toHaveBeenCalledWith(true);
      expect(router.createUrlTree).toHaveBeenCalledWith(['/login']);
    });

    const req = httpMock.expectOne('http://localhost:5172/api/auth/session');
    expect(req.request.method).toBe('GET');
    req.flush({ authenticated: false });
  });
});
