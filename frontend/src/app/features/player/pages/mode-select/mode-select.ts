import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TopbarComponent } from '../../../../shared/components/layout/topbar/topbar';

@Component({
  selector: 'app-mode-select',
  standalone: true,
  imports: [RouterLink, TopbarComponent],
  templateUrl: './mode-select.html',
  styleUrl: './mode-select.scss',
})
export class ModeSelect {
  modes = [
    {
      key: 'survival', name: 'SUPERVIVENCIA', icon: '☠',
      color: 'var(--vs-accent-red)',
      desc: 'Dos opciones. Una es correcta. La otra te quita una vida. 3 vidas y a aguantar.',
      tag: 'Solo · 1J', featured: true,
      route: '/play/survival',
    },
    {
      key: 'precision', name: 'PRECISIÓN', icon: '◎',
      color: 'var(--vs-accent-blue)',
      desc: 'Respuesta numérica. Cuanto más cerca, mejor. Acertar de cerca recupera vida.',
      tag: 'Solo · 1J',
      route: '/play/precision',
    },
    {
      key: 'binary', name: 'DUELO BINARIO', icon: '⚔',
      color: 'var(--vs-accent-gold)',
      desc: 'Supervivencia, pero con alguien respirándote en la nuca. Gana el último en pie.',
      tag: '2J · Online',
      route: '/play/queue/binary',
    },
    {
      key: 'pduel', name: 'DUELO DE PRECISIÓN', icon: '⊕',
      color: 'var(--vs-accent-green)',
      desc: 'Modo Precisión a dos bandas. El primero a cero, fuera.',
      tag: '2J · Online',
      route: '/play/queue/pduel',
    },
    {
      key: 'sabotage', name: 'SABOTAJE', icon: '⚡',
      color: 'var(--vs-accent-purple)',
      desc: 'No pierdes vida fallando: se la quitas al rival si aciertas mejor.',
      tag: '2J · Online',
      route: '/play/queue/sabotage',
    },
  ];

}
