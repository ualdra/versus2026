import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Practice } from './practice';
import { QuestionService } from '../../../../core/services/question.service';
import { PracticeService } from '../../../../core/services/practice.service';
import type { QuestionBinary, QuestionNumeric, PracticeAnswerResponse } from '../../../../core/models/game.models';

const BINARY_Q: QuestionBinary = {
  id: 'q-binary-1',
  type: 'BINARY',
  text: '¿Quién tiene más seguidores?',
  category: 'football',
  options: [
    { id: 'opt-a', text: 'Messi' },
    { id: 'opt-b', text: 'Cristiano' },
  ],
  scrapedAt: null,
};

const NUMERIC_Q: QuestionNumeric = {
  id: 'q-numeric-1',
  type: 'NUMERIC',
  text: '¿Cuántos goles marcó Messi en 2023?',
  category: 'football',
  unit: 'goles',
  scrapedAt: null,
};

const CORRECT_RES: PracticeAnswerResponse = {
  correct: true,
  correctOptionId: 'opt-a',
  explanation: 'Messi tiene más seguidores.',
};

const WRONG_RES: PracticeAnswerResponse = {
  correct: false,
  correctOptionId: 'opt-a',
};

const NUMERIC_CORRECT_RES: PracticeAnswerResponse = {
  correct: true,
  correctValue: 58,
  deviationPercent: 1.72,
  unit: 'goles',
};

function makeQuestionSvc(overrides: Partial<QuestionService> = {}): QuestionService {
  return {
    categories: vi.fn().mockReturnValue(of(['football', 'cinema'])),
    random: vi.fn().mockReturnValue(of(BINARY_Q)),
    byId: vi.fn(),
    ...overrides,
  } as unknown as QuestionService;
}

function makePracticeSvc(overrides = {}): PracticeService {
  return {
    submitAnswer: vi.fn().mockReturnValue(of(CORRECT_RES)),
    ...overrides,
  } as unknown as PracticeService;
}

async function setup(
  questionSvc: QuestionService = makeQuestionSvc(),
  practiceSvc: PracticeService = makePracticeSvc(),
): Promise<{ component: Practice; fixture: ComponentFixture<Practice> }> {
  await TestBed.configureTestingModule({
    imports: [Practice],
    providers: [
      provideRouter([]),
      { provide: QuestionService, useValue: questionSvc },
      { provide: PracticeService, useValue: practiceSvc },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(Practice);
  const component = fixture.componentInstance;
  await fixture.whenStable();
  return { component, fixture };
}

describe('Practice', () => {

  // ─── Creación ──────────────────────────────────────────────────────────────

  it('crea el componente', async () => {
    const { component } = await setup();
    expect(component).toBeTruthy();
  });

  it('empieza en fase setup', async () => {
    const { component } = await setup();
    expect(component.phase()).toBe('setup');
  });

  // ─── Inicialización ────────────────────────────────────────────────────────

  it('carga las categorías al inicializar', async () => {
    const questionSvc = makeQuestionSvc();
    const { component } = await setup(questionSvc);
    expect(questionSvc.categories).toHaveBeenCalledTimes(1);
    expect(component.categories()).toEqual(['football', 'cinema']);
  });

  it('si el servicio de categorías falla, la lista queda vacía', async () => {
    const questionSvc = makeQuestionSvc({
      categories: vi.fn().mockReturnValue(throwError(() => new Error('error'))),
    });
    const { component } = await setup(questionSvc);
    expect(component.categories()).toEqual([]);
  });

  // ─── Selección de filtros ─────────────────────────────────────────────────

  it('selectCategory activa la categoría', async () => {
    const { component } = await setup();
    component.selectCategory('football');
    expect(component.selectedCategory()).toBe('football');
  });

  it('selectCategory desactiva si se pulsa dos veces la misma', async () => {
    const { component } = await setup();
    component.selectCategory('football');
    component.selectCategory('football');
    expect(component.selectedCategory()).toBeNull();
  });

  it('selectType cambia el tipo seleccionado', async () => {
    const { component } = await setup();
    component.selectType('BINARY');
    expect(component.selectedType()).toBe('BINARY');
    component.selectType(null);
    expect(component.selectedType()).toBeNull();
  });

  // ─── Carga de pregunta ────────────────────────────────────────────────────

  it('start() llama a questionService.random y pasa a fase question', async () => {
    const questionSvc = makeQuestionSvc();
    const { component } = await setup(questionSvc);
    component.start();
    await Promise.resolve();
    expect(questionSvc.random).toHaveBeenCalledTimes(1);
    expect(component.phase()).toBe('question');
    expect(component.question()).toEqual(BINARY_Q);
  });

  it('start() pasa el tipo y categoría seleccionados a random', async () => {
    const questionSvc = makeQuestionSvc();
    const { component } = await setup(questionSvc);
    component.selectCategory('football');
    component.selectType('NUMERIC');
    component.start();
    await Promise.resolve();
    expect(questionSvc.random).toHaveBeenCalledWith('NUMERIC', 'football');
  });

  it('start() sin filtros llama a random sin parámetros', async () => {
    const questionSvc = makeQuestionSvc();
    const { component } = await setup(questionSvc);
    component.start();
    await Promise.resolve();
    expect(questionSvc.random).toHaveBeenCalledWith(undefined, undefined);
  });

  it('si random falla, vuelve a fase setup con mensaje de error', async () => {
    const questionSvc = makeQuestionSvc({
      random: vi.fn().mockReturnValue(throwError(() => new Error('no questions'))),
    });
    const { component } = await setup(questionSvc);
    component.start();
    await Promise.resolve();
    expect(component.phase()).toBe('setup');
    expect(component.errorMessage()).toBeTruthy();
  });

  // ─── Respuesta BINARY ─────────────────────────────────────────────────────

  it('pickOption() llama a practiceService.submitAnswer', async () => {
    const practiceSvc = makePracticeSvc();
    const { component } = await setup(makeQuestionSvc(), practiceSvc);
    component.start();
    await Promise.resolve();
    component.pickOption('opt-a');
    await Promise.resolve();
    expect(practiceSvc.submitAnswer).toHaveBeenCalledWith({
      questionId: BINARY_Q.id,
      optionId: 'opt-a',
    });
  });

  it('tras respuesta correcta, pasa a fase feedback con result', async () => {
    const { component } = await setup();
    component.start();
    await Promise.resolve();
    component.pickOption('opt-a');
    await Promise.resolve();
    expect(component.phase()).toBe('feedback');
    expect(component.result()).toEqual(CORRECT_RES);
  });

  it('pickOption ignora clics cuando la fase no es question', async () => {
    const practiceSvc = makePracticeSvc();
    const { component } = await setup(makeQuestionSvc(), practiceSvc);
    // fase setup — no hace nada
    component.pickOption('opt-a');
    expect(practiceSvc.submitAnswer).not.toHaveBeenCalled();
  });

  // ─── Contadores de sesión ─────────────────────────────────────────────────

  it('acierto incrementa streak y precision', async () => {
    const { component } = await setup();
    component.start();
    await Promise.resolve();
    component.pickOption('opt-a');
    await Promise.resolve();
    expect(component.streak()).toBe(1);
    expect(component.accuracy()).toBe(100);
    expect(component.totalAnswered()).toBe(1);
  });

  it('fallo resetea streak pero actualiza precision', async () => {
    const practiceSvc = makePracticeSvc({
      submitAnswer: vi.fn().mockReturnValue(of(WRONG_RES)),
    });
    const { component } = await setup(makeQuestionSvc(), practiceSvc);

    // primer acierto manual (simulado)
    (component as any).applyResult(CORRECT_RES);
    // ahora fallo
    (component as any).applyResult(WRONG_RES);

    expect(component.streak()).toBe(0);
    expect(component.totalAnswered()).toBe(2);
    expect(component.accuracy()).toBe(50);
  });

  it('dos aciertos seguidos acumulan racha', async () => {
    const { component } = await setup();
    (component as any).applyResult(CORRECT_RES);
    (component as any).applyResult(CORRECT_RES);
    expect(component.streak()).toBe(2);
    expect(component.accuracy()).toBe(100);
  });

  // ─── optionState ──────────────────────────────────────────────────────────

  it('optionState: opción correcta → "correct" en feedback', async () => {
    const { component } = await setup();
    component.start();
    await Promise.resolve();
    component.pickedOptionId.set('opt-b');
    component.result.set(CORRECT_RES);
    expect(component.optionState('opt-a')).toBe('correct');
    expect(component.optionState('opt-b')).toBe('wrong');
  });

  it('optionState: sin result → "idle"', async () => {
    const { component } = await setup();
    expect(component.optionState('opt-a')).toBe('idle');
  });

  // ─── changeFilters ────────────────────────────────────────────────────────

  it('changeFilters vuelve a setup y resetea contadores', async () => {
    const { component } = await setup();
    (component as any).applyResult(CORRECT_RES);
    (component as any).applyResult(CORRECT_RES);

    component.changeFilters();

    expect(component.phase()).toBe('setup');
    expect(component.streak()).toBe(0);
    expect(component.totalAnswered()).toBe(0);
    expect(component.accuracy()).toBe(0);
    expect(component.result()).toBeNull();
  });

  // ─── loadNext limpia estado previo ────────────────────────────────────────

  it('loadNext limpia picked y result antes de cargar', async () => {
    const questionSvc = makeQuestionSvc();
    const { component } = await setup(questionSvc);
    component.pickedOptionId.set('opt-a');
    component.result.set(CORRECT_RES);
    component.loadNext();
    expect(component.pickedOptionId()).toBeNull();
    expect(component.result()).toBeNull();
  });

});
