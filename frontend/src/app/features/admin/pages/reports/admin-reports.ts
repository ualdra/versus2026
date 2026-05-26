// TODO: wire to moderation endpoints when feature Moderación is done (#80-#81)
import { Component } from '@angular/core';
import { AdminSidebarComponent } from '../../components/sidebar/sidebar';

@Component({
  selector: 'app-admin-reports',
  standalone: true,
  imports: [AdminSidebarComponent],
  templateUrl: './admin-reports.html',
  styleUrl: '../dashboard/admin-dashboard.scss',
})
export class AdminReports {
  reports = [
    {
      id: 1, reason: 'Datos desactualizados', reporter: 'numerito', when: 'hace 12 min', count: 4,
      q: '¿Quién tiene MÁS seguidores en Instagram, Cristiano o Messi?',
      opts: [{ name: 'Cristiano Ronaldo', num: '638M', ok: true }, { name: 'Lionel Messi', num: '503M', ok: false }],
    },
    {
      id: 2, reason: 'Respuesta incorrecta', reporter: 'kil4_max', when: 'hace 1 h', count: 12,
      q: '¿Qué película recaudó más, Avatar 2 o Endgame?',
      opts: [{ name: 'Avatar: el sentido del agua', num: '2 320M$', ok: false }, { name: 'Vengadores: Endgame', num: '2 799M$', ok: true }],
    },
    {
      id: 3, reason: 'Pregunta ambigua', reporter: 'tucanela', when: 'hace 2 h', count: 2,
      q: '¿Capital con más habitantes, Tokio o Delhi?',
      opts: [{ name: 'Tokio', num: '37.4M (área metropolitana)', ok: true }, { name: 'Delhi', num: '32.9M (área metropolitana)', ok: false }],
    },
  ];
}
