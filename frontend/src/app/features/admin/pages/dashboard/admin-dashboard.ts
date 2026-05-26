import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { AdminSidebarComponent } from '../../components/sidebar/sidebar';
import { AdminService } from '../../../../core/services/admin.service';
import { AdminLog, AdminSpider, AdminStats } from '../../../../core/models/admin.models';

interface KpiCard {
  label: string;
  num: string;
  delta: string;
  up: boolean;
  color: string;
  spark: number[];
}

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [AdminSidebarComponent, DatePipe],
  templateUrl: './admin-dashboard.html',
  styleUrl: './admin-dashboard.scss',
})
export class AdminDashboard implements OnInit {
  private readonly adminService = inject(AdminService);

  kpis = signal<KpiCard[]>([]);
  spiders = signal<AdminSpider[]>([]);
  logs = signal<AdminLog[]>([]);
  loading = signal(true);
  readonly today = new Date();

  // TODO: depende de Stats avanzado por modo
  modes = [
    { mode: 'Supervivencia',   pct: 42, color: 'var(--vs-accent-red)'    },
    { mode: 'Precisión',       pct: 24, color: 'var(--vs-accent-blue)'   },
    { mode: 'Duelo binario',   pct: 16, color: 'var(--vs-accent-gold)'   },
    { mode: 'Sabotaje',        pct: 12, color: 'var(--vs-accent-purple)' },
    { mode: 'Duelo precisión', pct:  6, color: 'var(--vs-accent-green)'  },
  ];

  ngOnInit(): void {
    this.loadData();
  }

  reload(): void {
    this.loadData();
  }

  private loadData(): void {
    this.loading.set(true);
    this.adminService.stats().subscribe({
      next: (s) => {
        this.kpis.set(this.buildKpis(s));
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    this.adminService.getSpiders().subscribe({
      next: (list) => this.spiders.set(list),
    });
    this.adminService.logs(20).subscribe({
      next: (entries) => this.logs.set(entries),
    });
  }

  private buildKpis(s: AdminStats): KpiCard[] {
    return [
      {
        label: 'Usuarios activos',
        num: s.activeUsers.toLocaleString('es-ES'),
        delta: `de ${s.totalUsers.toLocaleString('es-ES')} totales`,
        up: true,
        color: 'var(--vs-accent-green)',
        spark: [10, 12, 11, 14, 13, 17, 19, 18, 22, 24],
      },
      {
        label: 'Partidas hoy',
        num: s.matchesToday.toLocaleString('es-ES'),
        delta: 'partidas hoy',
        up: true,
        color: 'var(--vs-accent-blue)',
        spark: [40, 38, 52, 49, 61, 58, 73],
      },
      {
        label: 'Preguntas en BD',
        num: s.totalQuestions.toLocaleString('es-ES'),
        delta: 'preguntas activas',
        up: true,
        color: 'var(--vs-accent-gold)',
        spark: [20, 22, 24, 26, 28, 30, 33],
      },
      {
        label: 'Reportes pendientes',
        num: s.pendingReports.toLocaleString('es-ES'),
        delta: s.pendingReports > 0 ? 'requieren revisión' : 'sin reportes',
        up: s.pendingReports === 0,
        color: 'var(--vs-accent-red)',
        spark: [3, 5, 4, 8, 12, 18, s.pendingReports],
      },
    ];
  }

  sparkPoints(data: number[]): string {
    const max = Math.max(...data), min = Math.min(...data), range = max - min || 1;
    return data
      .map((v, i) => `${(i / (data.length - 1)) * 80},${30 - ((v - min) / range) * 26 - 2}`)
      .join(' ');
  }

  pillClass(status: string): string {
    return { IDLE: 'vs-pill--mute', RUNNING: 'vs-pill--info', FAILED: 'vs-pill--err' }[status] ?? 'vs-pill--mute';
  }

  pillLabel(status: string): string {
    return { IDLE: 'INACTIVA', RUNNING: 'EJECUTANDO', FAILED: 'CAÍDA' }[status] ?? status;
  }

  dotClass(status: string): string {
    return { IDLE: 'idle', RUNNING: 'ok', FAILED: 'err' }[status] ?? 'idle';
  }

  lastRunLabel(spider: AdminSpider): string {
    if (!spider.lastRunAt) return 'Sin ejecución';
    const d = new Date(spider.lastRunAt);
    const diff = Math.floor((Date.now() - d.getTime()) / 60000);
    if (diff < 60) return `hace ${diff} min`;
    return `hace ${Math.floor(diff / 60)} h`;
  }

  logClass(level: string): string {
    return { INFO: 'ok', WARN: 'warn', ERR: 'err' }[level] ?? 'ok';
  }
}
