import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

interface PolicySection {
  id: string;
  title: string;
  content: string;
}

@Component({
  selector: 'app-privacidad',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './privacidad.html',
  styleUrl: './privacidad.scss',
})
export class Privacidad {
  readonly lastUpdated = '27 de mayo de 2026';

  readonly sections: PolicySection[] = [
    {
      id: 'responsable',
      title: '1. RESPONSABLE DEL TRATAMIENTO',
      content: `Versus es un proyecto académico desarrollado por estudiantes de la Universidad de Almería (UAL) en el marco de la asignatura Desarrollo de Requisitos y Arquitectura. No tiene carácter comercial ni entidad jurídica propia. Para cualquier consulta relacionada con tus datos, puedes contactar al equipo a través del repositorio oficial del proyecto.`,
    },
    {
      id: 'datos',
      title: '2. DATOS QUE RECOGEMOS',
      content: `Al registrarte en Versus recopilamos: nombre de usuario, dirección de correo electrónico y contraseña (almacenada en formato hash bcrypt). Durante el uso de la plataforma registramos tus estadísticas de juego (puntuaciones, rachas, partidas disputadas) y tu historial de partidas. No recogemos datos de pago, dirección postal ni información sensible de ningún tipo.`,
    },
    {
      id: 'finalidad',
      title: '3. FINALIDAD Y BASE LEGAL',
      content: `Tus datos se utilizan exclusivamente para: (a) gestionar tu cuenta y autenticación, (b) mostrar estadísticas y rankings dentro del juego, (c) permitir la funcionalidad multijugador. La base legal es el consentimiento que otorgas al registrarte. Puedes retirar ese consentimiento en cualquier momento eliminando tu cuenta.`,
    },
    {
      id: 'almacenamiento',
      title: '4. ALMACENAMIENTO Y SEGURIDAD',
      content: `Los datos se almacenan en una base de datos PostgreSQL alojada en servidores de desarrollo académico. Las contraseñas se hashean con BCrypt antes de persistirse. Las comunicaciones entre cliente y servidor usan HTTPS y los tokens de sesión se gestionan mediante JWT con expiración. Al tratarse de un proyecto académico, no garantizamos SLAs de producción.`,
    },
    {
      id: 'conservacion',
      title: '5. CONSERVACIÓN DE DATOS',
      content: `Conservamos tus datos mientras tu cuenta esté activa. Si eliminas tu cuenta, todos tus datos personales serán borrados en un plazo máximo de 30 días. Los datos anonimizados de partidas (sin vinculación a usuario identificable) pueden conservarse con fines estadísticos del proyecto.`,
    },
    {
      id: 'derechos',
      title: '6. TUS DERECHOS',
      content: `De acuerdo con el RGPD, tienes derecho a: acceder a tus datos, rectificarlos si son incorrectos, solicitar su supresión, oponerte a su tratamiento y solicitar la limitación del tratamiento. Para ejercer cualquiera de estos derechos, contacta al equipo a través del repositorio del proyecto. Tienes también derecho a presentar una reclamación ante la Agencia Española de Protección de Datos (AEPD).`,
    },
    {
      id: 'cookies',
      title: '7. COOKIES Y ALMACENAMIENTO LOCAL',
      content: `Versus utiliza localStorage del navegador para conservar el token de sesión JWT entre visitas. No se utilizan cookies de terceros, píxeles de seguimiento ni tecnologías de análisis externas. No hay integración con plataformas publicitarias.`,
    },
    {
      id: 'cambios',
      title: '8. CAMBIOS EN ESTA POLÍTICA',
      content: `Esta política puede actualizarse a medida que el proyecto evolucione. Notificaremos cambios significativos mediante un aviso visible en la plataforma. La fecha de última actualización se muestra siempre al inicio de este documento.`,
    },
  ];
}
