import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

interface TeamMember {
  name: string;
  handle: string;
  githubUrl: string;
  avatarUrl: string;
  role: string;
  focus: string;
  color: string;
  initials: string;
}

@Component({
  selector: 'app-equipo',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './equipo.html',
  styleUrl: './equipo.scss',
})
export class Equipo {
  readonly stack = [
    { label: 'Angular 21', desc: 'SPA con standalone components y Signals API' },
    { label: 'Spring Boot 4', desc: 'API REST + STOMP WebSockets' },
    { label: 'PostgreSQL', desc: 'Base de datos relacional con UUIDs' },
    { label: 'Scrapy', desc: 'Arañas de scraping para preguntas reales' },
    { label: 'Docker', desc: 'Dev containers y despliegue' },
  ];

    readonly members: TeamMember[] = [
    this.member({
      name: 'Raúl Martínez Gutiérrez',
      handle: 'ualrmg429',
      role: 'SRE · QA',
      focus: 'CI/CD · Docker · Testing',
      color: 'blue',
      initials: 'RM',
    }),
    this.member({
      name: 'Alejandro Ortega Ramón',
      handle: 'ualaor983',
      role: 'Frontend',
      focus: 'Angular · UI/UX · Componentes',
      color: 'red',
      initials: 'AO',
    }),
    this.member({
      name: 'Bruno Ramirez Ledesma',
      handle: 'ualbrl973',
      role: 'Backend Lead',
      focus: 'Spring Boot · API REST · Arquitectura',
      color: 'gold',
      initials: 'BR',
    }),
    this.member({
      name: 'Adrián Martínez Granados',
      handle: 'ualamg538',
      role: 'Scraping',
      focus: 'Scrapy · Pipelines · Datos reales',
      color: 'purple',
      initials: 'AM',
    }),
    this.member({
      name: 'Andrés Ruiz Andújar',
      handle: 'UALara584',
      role: 'Backend',
      focus: 'Spring Boot · Features · PostgreSQL',
      color: 'green',
      initials: 'AR',
    }),
    this.member({
      name: 'Ilyas El Hamdi',
      handle: 'ilyas2022',
      role: 'Frontend',
      focus: 'Angular · Features · Integración',
      color: 'blue',
      initials: 'IE',
    }),
    this.member({
      name: 'Sergio Gómez Vico',
      handle: 'ualsgv396',
      role: 'Backend',
      focus: 'Spring Boot · Features · PostgreSQL',
      color: 'red',
      initials: 'SG',
    }),
  ];

  private member(member: Omit<TeamMember, 'githubUrl' | 'avatarUrl'>): TeamMember {
    return {
      ...member,
      githubUrl: `https://github.com/${member.handle}`,
      avatarUrl: `https://github.com/${member.handle}.png?size=128`,
    };
  }
}
