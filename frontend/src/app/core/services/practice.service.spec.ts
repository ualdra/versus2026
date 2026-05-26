import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { PracticeService } from './practice.service';
import { environment } from '../../../environments/environment';
import type { PracticeAnswerResponse } from '../models/game.models';

describe('PracticeService', () => {
  let service: PracticeService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        PracticeService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(PracticeService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('crea el servicio', () => {
    expect(service).toBeTruthy();
  });

  it('submitAnswer POST al endpoint correcto con el body', () => {
    const req = { questionId: 'q1', optionId: 'opt-a' };
    const mockResponse: PracticeAnswerResponse = {
      correct: true,
      correctOptionId: 'opt-a',
    };

    service.submitAnswer(req).subscribe((res) => {
      expect(res).toEqual(mockResponse);
    });

    const testReq = http.expectOne(`${environment.apiBaseUrl}/practice/answer`);
    expect(testReq.request.method).toBe('POST');
    expect(testReq.request.body).toEqual(req);
    testReq.flush(mockResponse);
  });

  it('submitAnswer numérico incluye value en el body', () => {
    const req = { questionId: 'q1', value: 650000000 };

    service.submitAnswer(req).subscribe();

    const testReq = http.expectOne(`${environment.apiBaseUrl}/practice/answer`);
    expect(testReq.request.body).toEqual(req);
    testReq.flush({ correct: false, correctValue: 640000000, deviationPercent: 1.56, unit: 'millones' });
  });
});
