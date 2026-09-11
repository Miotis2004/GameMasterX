import { TestBed } from '@angular/core/testing';
import { FirstRunGuard } from './first-run.guard';
import { Router } from '@angular/router';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

describe('FirstRunGuard', () => {
  let guard: FirstRunGuard;
  let httpMock: HttpTestingController;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    const routerSpy = jasmine.createSpyObj('Router', ['createUrlTree']);

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        FirstRunGuard,
        { provide: Router, useValue: routerSpy }
      ]
    });

    guard = TestBed.inject(FirstRunGuard);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should redirect to setup if setupRequired is true', () => {
    const urlTree = {} as any;
    router.createUrlTree.and.returnValue(urlTree);

    guard.canActivate().subscribe(result => {
      expect(result).toBe(urlTree);
      expect(router.createUrlTree).toHaveBeenCalledWith(['/setup']);
    });

    const req = httpMock.expectOne('http://localhost:5172/api/admin/setup/status');
    expect(req.request.method).toBe('GET');
    req.flush({ setupRequired: true });
  });

  it('should allow navigation if setupRequired is false', () => {
    guard.canActivate().subscribe(result => {
      expect(result).toBe(true);
    });

    const req = httpMock.expectOne('http://localhost:5172/api/admin/setup/status');
    expect(req.request.method).toBe('GET');
    req.flush({ setupRequired: false });
  });
});
