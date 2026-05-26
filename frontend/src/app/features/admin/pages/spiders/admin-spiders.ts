// TODO: wire to /api/admin/spiders when Pipeline Scrapy feature is done (#45-#50)
import { Component, signal } from '@angular/core';
import { AdminSidebarComponent } from '../../components/sidebar/sidebar';

@Component({
  selector: 'app-admin-spiders',
  standalone: true,
  imports: [AdminSidebarComponent],
  templateUrl: './admin-spiders.html',
  styleUrl: '../dashboard/admin-dashboard.scss',
})
export class AdminSpiders {
  filter = signal('');

  spiders = [
    { name: 'spider_transfermarkt',  url: 'https://transfermarkt.es/...',        stat: 'ok',   runs: 1280, lastQ: 1240, lastRun: '10:31', schedule: 'cada 4h', cat: 'Fútbol'     },
    { name: 'spider_spotify_charts', url: 'https://charts.spotify.com',           stat: 'ok',   runs: 980,  lastQ: 845,  lastRun: '10:18', schedule: 'diario',  cat: 'Música'     },
    { name: 'spider_box_office',     url: 'https://www.boxofficemojo.com',        stat: 'warn', runs: 612,  lastQ: 320,  lastRun: '08:42', schedule: 'cada 6h', cat: 'Cine'       },
    { name: 'spider_instagram',      url: 'https://www.instagram.com',            stat: 'err',  runs: 1488, lastQ: 0,    lastRun: '04:12', schedule: 'cada 2h', cat: 'Social'     },
    { name: 'spider_twitch_top',     url: 'https://www.twitch.tv/directory',      stat: 'ok',   runs: 218,  lastQ: 156,  lastRun: '09:55', schedule: 'cada 6h', cat: 'Streaming'  },
    { name: 'spider_geo_pop',        url: 'https://en.wikipedia.org/wiki/...',    stat: 'idle', runs: 12,   lastQ: 0,    lastRun: '—',     schedule: 'manual',  cat: 'Geografía'  },
  ];

  barData = [12,18,24,9,6,4,32,78,90,64,22,18,14,11,9,16,28,34,42,36,28,22,18,14];
  barMax  = Math.max(...[12,18,24,9,6,4,32,78,90,64,22,18,14,11,9,16,28,34,42,36,28,22,18,14]);

  pillClass(s: string) { return { ok:'vs-pill--ok', warn:'vs-pill--warn', err:'vs-pill--err' }[s] ?? 'vs-pill--mute'; }
  pillLabel(s: string) { return { ok:'OK', warn:'LENTA', err:'CAÍDA', idle:'INACTIVA' }[s] ?? s; }
}
