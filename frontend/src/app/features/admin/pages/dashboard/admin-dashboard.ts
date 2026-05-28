import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AdminSidebarComponent } from '../../components/sidebar/sidebar';
import { AdminService } from '../../../../core/services/admin.service';
import { AdminLog, AdminSpider, AdminStats } from '../../../../core/models/admin.models';

interface KpiCard {
  label: string;
  num: string;
  delta: string;
  up: boolean;
  color: string;
}

interface ModeBar {
  mode: string;
  label: string;
  count: number;
  pct: number;
  color: string;
}

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [AdminSidebarComponent, DatePipe, RouterLink],
  templateUrl: './admin-dashboard.html',
  styleUrl: './admin-dashboard.scss',
})
export class AdminDashboard implements OnInit {
  private readonly adminService = inject(AdminService);

  kpis = signal<KpiCard[]>([]);
  spiders = signal<AdminSpider[]>([]);
  logs = signal<AdminLog[]>([]);
  modes = signal<ModeBar[]>([]);
  totalModeMatches = signal(0);
  loading = signal(true);
  readonly today = new Date();

  private readonly modeMeta: Record<string, { label: string; color: string }> = {
    SURVIVAL:       { label: 'Supervivencia',   color: 'var(--vs-accent-red)'    },
    PRECISION:      { label: 'Precisión',       color: 'var(--vs-accent-blue)'   },
    BINARY_DUEL:    { label: 'Duelo binario',   color: 'var(--vs-accent-gold)'   },
    PRECISION_DUEL: { label: 'Duelo precisión', color: 'var(--vs-accent-green)'  },
    SABOTAGE:       { label: 'Sabotaje',        color: 'var(--vs-accent-purple)' },
  };

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
    this.adminService.modeDistribution().subscribe({
      next: (dist) => {
        const total = dist.reduce((sum, d) => sum + d.count, 0);
        this.totalModeMatches.set(total);
        this.modes.set(
          dist.map((d) => {
            const meta = this.modeMeta[d.mode] ?? { label: d.mode, color: 'var(--vs-accent-blue)' };
            return {
              mode: d.mode,
              label: meta.label,
              count: d.count,
              pct: total > 0 ? Math.round((d.count / total) * 100) : 0,
              color: meta.color,
            };
          }),
        );
      },
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
      },
      {
        label: 'Partidas hoy',
        num: s.matchesToday.toLocaleString('es-ES'),
        delta: 'partidas hoy',
        up: true,
        color: 'var(--vs-accent-blue)',
      },
      {
        label: 'Preguntas en BD',
        num: s.totalQuestions.toLocaleString('es-ES'),
        delta: 'preguntas activas',
        up: true,
        color: 'var(--vs-accent-gold)',
      },
      {
        label: 'Reportes pendientes',
        num: s.pendingReports.toLocaleString('es-ES'),
        delta: s.pendingReports > 0 ? 'requieren revisión' : 'sin reportes',
        up: s.pendingReports === 0,
        color: 'var(--vs-accent-red)',
      },
    ];
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
