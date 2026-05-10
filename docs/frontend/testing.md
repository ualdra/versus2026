# Testing del frontend

## Stack de testing

| Herramienta | Propósito |
|---|---|
| **Vitest** | Runner de tests unitarios (reemplaza Jest, integrado con ng test) |
| **Angular Testing Library** | Utilidades para renderizar componentes en tests |
| **HttpClientTestingModule** | Intercepta peticiones HTTP en tests sin red real |

## Ejecutar los tests

```bash
# Dentro de frontend/ o del Dev Container
ng test              # Modo watch (re-ejecuta al guardar)
ng test --watch=false # Una sola pasada (útil en CI)
```

## Estructura de un test de componente

```typescript
// register-form.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RegisterFormComponent } from './register-form.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('RegisterFormComponent', () => {
  let fixture: ComponentFixture<RegisterFormComponent>;
  let component: RegisterFormComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RegisterFormComponent],
      providers: [provideHttpClientTesting()]
    }).compileComponents();

    fixture = TestBed.createComponent(RegisterFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should show error when passwords do not match', () => {
    component.password.set('abc123');
    component.passwordConfirm.set('xyz999');
    component.submit();
    expect(component.error()).toBe('Las contraseñas no coinciden');
  });
});
```

## Qué testear y qué no

### Testear ✅

| Qué | Por qué |
|---|---|
| Lógica de validación de formularios | Crítico — errores silenciosos difíciles de detectar |
| Guards (`authGuard`, `adminGuard`) | Seguridad de rutas |
| Transformaciones en servicios (mapeo de DTOs, cálculos) | Lógica de negocio en el cliente |
| Renderizado condicional con `@if` complejo | Asegura que la UI refleja el estado |
| Comportamiento de `AuthInterceptor` (retry en 401) | Flujo de refresco de token |

### No testear ❌

| Qué | Por qué |
|---|---|
| Que Angular enlaza correctamente `[(ngModel)]` | Ya lo testea Angular |
| Llamadas HTTP en bruto | Se cubren con tests de integración del backend |
| Componentes puramente visuales sin lógica | ROI bajo; cámbia con el diseño |
| Detalles de CSS o layout | Inestables y lentos de mantener |

## Test de un servicio con HTTP

```typescript
// auth.service.spec.ts
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { provideHttpClient } from '@angular/common/http';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuthService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should store token after login', () => {
    service.login({ email: 'a@b.com', password: '123' }).subscribe();
    const req = http.expectOne('/api/auth/login');
    req.flush({ accessToken: 'tok', refreshToken: 'rtok', user: { role: 'PLAYER' } });
    expect(localStorage.getItem('vs.accessToken')).toBe('tok');
  });
});
```

## Test de un guard

```typescript
// auth.guard.spec.ts
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard', () => {
  it('should redirect to /login when not authenticated', () => {
    const router = { navigate: vi.fn() } as unknown as Router;
    const auth = { isAuthenticated: () => false } as unknown as AuthService;
    // Configura los providers con los mocks y comprueba la redirección
  });
});
```

## Cobertura objetivo

| Área | Cobertura mínima |
|---|---|
| `core/services/` | 70% |
| `core/guards/` | 90% |
| `core/interceptors/` | 80% |
| `features/auth/` (forms) | 75% |
| `features/survival/`, `features/precision/` | 60% |
| `shared/components/ui/` | 40% |

Generar el informe de cobertura:

```bash
ng test --coverage --watch=false
# Abre coverage/index.html en el navegador
```

## Convenciones de nombrado

- Fichero: `<nombre>.component.spec.ts` junto al componente.
- `describe` con el nombre de la clase: `describe('LoginFormComponent', ...)`.
- `it` en español, en tercera persona, describiendo el comportamiento esperado: `it('debería mostrar el error de contraseña incorrecta')`.
- Un `it` por comportamiento — no agrupes múltiples asserts en un solo test.
