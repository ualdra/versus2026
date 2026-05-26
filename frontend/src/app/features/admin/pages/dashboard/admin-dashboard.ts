import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { AdminSidebarComponent } from '../../components/sidebar/sidebar';
import { AdminService } from '../../../../core/services/admin.service';
import { AdminLog, AdminStats } from '../../../../core/models/admin.models';

interface KpiCard {
  label: string;
  num: string;
  delta: string;
  up: boolean;
  color: string;
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

  kpis: KpiCard[] = [];
  logs: AdminLog[] = [];
  readonly today = new Date();

  // TODO: depende de Pipeline Scrapy / Stats avanzado
  spiders = [
    { name: 'spider_transfermarkt', stat: 'ok',   last: 'hace 14 min', q: '—' },
    { name: 'spider_spotify_charts', stat: 'ok',  last: 'hace 28 min', q: '—' },
    { name: 'spider_box_office',    stat: 'warn', last: 'hace 2 h',    q: '—' },
    { name: 'spider_instagram',     stat: 'err',  last: 'hace 6 h',    q: '—' },
  ];

  // TODO: depende de Stats avanzado por modo
  modes = [
    { mode: 'Supervivencia',    pct: 42, color: 'var(--vs-accent-red)'    },
    { mode: 'Precisión',        pct: 24, color: 'var(--vs-accent-blue)'   },
    { mode: 'Duelo binario',    pct: 16, color: 'var(--vs-accent-gold)'   },
    { mode: 'Sabotaje',         pct: 12, color: 'var(--vs-accent-purple)' },
    { mode: 'Duelo precisión',  pct: 6,  color: 'var(--vs-accent-green)'  },
  ];

  ngOnInit(): void {
    this.loadData();
  }

  reload(): void {
    this.loadData();
  }

  private loadData(): void {
    this.adminService.stats().subscribe(stats => this.kpis = this.buildKpis(stats));
    this.adminService.logs(20).subscribe(entries => this.logs = entries);
  }

  pillClass(stat: string): string {
    return { ok: 'vs-pill--ok', warn: 'vs-pill--warn', err: 'vs-pill--err' }[stat] ?? 'vs-pill--mute';
  }

  pillLabel(stat: string): string {
    return { ok: 'OK', warn: 'LENTA', err: 'CAÍDA' }[stat] ?? stat;
  }

  logClass(level: string): string {
    return { INFO: 'ok', WARN: 'warn', ERR: 'err' }[level] ?? 'ok';
  }

  private buildKpis(s: AdminStats): KpiCard[] {
    return [
      { label: 'Usuarios activos',    num: s.activeUsers.toLocaleString(),   delta: `de ${s.totalUsers.toLocaleString()} totales`, up: true,  color: 'var(--vs-accent-green)' },
      { label: 'Partidas hoy',        num: s.matchesToday.toLocaleString(),  delta: '',  up: true,  color: 'var(--vs-accent-blue)' },
      { label: 'Preguntas en BD',     num: s.totalQuestions.toLocaleString(), delta: '', up: true,  color: 'var(--vs-accent-gold)' },
      { label: 'Reportes pendientes', num: s.pendingReports.toLocaleString(), delta: '', up: false, color: 'var(--vs-accent-red)' },
    ];
  }
}
