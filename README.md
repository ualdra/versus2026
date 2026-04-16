<div align="center">

<br/>

```
██╗   ██╗███████╗██████╗ ███████╗██╗   ██╗███████╗
██║   ██║██╔════╝██╔══██╗██╔════╝██║   ██║██╔════╝
██║   ██║█████╗  ██████╔╝███████╗██║   ██║███████╗
╚██╗ ██╔╝██╔══╝  ██╔══██╗╚════██║██║   ██║╚════██║
 ╚████╔╝ ███████╗██║  ██║███████║╚██████╔╝███████║
  ╚═══╝  ╚══════╝╚═╝  ╚═╝╚══════╝ ╚═════╝ ╚══════╝
```

**Dos opciones. Una respuesta. Cero piedad.**

<br/>

![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Angular](https://img.shields.io/badge/Angular-DD0031?style=for-the-badge&logo=angular&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Scrapy](https://img.shields.io/badge/Scrapy-60A839?style=for-the-badge&logo=scrapy&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)

![Estado](https://img.shields.io/badge/estado-en%20desarrollo-yellow?style=flat-square)
![Licencia](https://img.shields.io/badge/licencia-pendiente-lightgrey?style=flat-square)

<br/>

</div>

---

## 📖 Descripción

**Versus** es un juego de preguntas en el que no basta con saber: hay que arriesgar. Cada ronda te planta delante decisiones imposibles y números que nadie debería tener en la cabeza (pero que tú vas a intentar adivinar igualmente). Puedes jugar solo contra el cronómetro de tu propio ego, o medirte contra otra persona en tiempo real y ver quién cae primero.

Las preguntas se alimentan de datos reales extraídos de la web, así que nunca se repite el guion: seguidores, goles, taquillas, récords… si el número existe, Versus lo sabe.

Spoiler: casi siempre cae el que se confía.

---

## 🎮 Modos de juego

<table>
<tr>
<td width="50%">

### 🩸 Supervivencia
Dos opciones en pantalla. Una es correcta, la otra te quita una vida. Tienes **3 vidas** y ni un segundo de tregua. ¿Cuánto aguantas antes de que la tercera equis sea definitiva?

### 📉 Precisión
Pregunta con respuesta numérica (*"¿Cuántos seguidores tiene Cristiano?"*). Tú tiras un número. Cuanto más te desvíes, más vida pierdes. Pero si te acercas una barbaridad, **recuperas vida**. El que se atreve, gana.

### 🆚 Duelo binario
Mismas reglas que Supervivencia, pero con alguien respirándote en la nuca. Dos jugadores, 3 vidas cada uno, y gana el último en pie.

</td>
<td width="50%">

### 🥊 Duelo de precisión
El modo Precisión pero a dos bandas. Cada uno con su barra de vida, cada uno con sus nervios. El primero en llegar a cero, se va a casa.

### ☠️ Modo Sabotaje
La variante más sucia. Aquí no pierdes vida por fallar: **se la quitas al rival por acertar mejor que él**. Si tu respuesta está más cerca de la real, tu oponente recibe el golpe. Cada pregunta es un puñetazo.

</td>
</tr>
</table>

---

## ✨ Funcionalidades

<table>
<tr>
<td width="50%">

### 👤 Perfiles de jugador
Crea tu cuenta, personaliza tu avatar y construye tu reputación partida a partida.

### 📊 Estadísticas personales
Consulta tu historial de partidas, tus porcentajes de acierto por modo y tus rachas más épicas (o más vergonzosas).

### 🏆 Ranking global
Compite por entrar en el top. Cada modo tiene su propia clasificación y su propia élite.

</td>
<td width="50%">

### ⚡ Duelos en tiempo real
Partidas multijugador sincronizadas al milisegundo mediante WebSockets. Sin lag, sin excusas.

### 🎯 Matchmaking
Entra en cola y el sistema te empareja con un rival de nivel parecido. O reta directamente a un amigo por código de sala.

### 🕷 Preguntas siempre frescas
La base de datos se actualiza automáticamente con datos scrapeados de la web. Nunca te vas a encontrar dos veces la misma ronda.

</td>
</tr>
</table>

---

## 👥 Tipos de usuario

| Rol | Descripción |
|---|---|
| 🎮 **Jugador** | Usuario estándar con acceso a todos los modos de juego, rankings y estadísticas. |
| 🛡️ **Moderador** | Supervisa las preguntas reportadas y mantiene la calidad del contenido. |
| ⚙️ **Administrador** | Control total sobre la plataforma: gestión de usuarios, spiders de scraping y configuración global. |

---

## 🗺️ Roadmap

```
 FASE 1 — Fundamentos             FASE 2 — Competitivo              FASE 3 — Comunidad
 ─────────────────────────        ──────────────────────────        ──────────────────────────
 ☐ Autenticación                  ☐ Duelo binario                   ☐ Torneos por eliminación
 ☐ Modo Supervivencia             ☐ Duelo de precisión              ☐ Logros y títulos
 ☐ Modo Precisión                 ☐ Modo Sabotaje                   ☐ Chat en partida
 ☐ Scraping de preguntas          ☐ Matchmaking                     ☐ Multi-idioma
 ☐ Perfil básico                  ☐ Ranking global                  ☐ Versión móvil
```

---

## 🛠️ Stack tecnológico

<table>
<tr>
<th>Capa</th>
<th>Tecnología</th>
<th>Descripción</th>
</tr>
<tr>
<td><strong>Frontend</strong></td>
<td>

![Angular](https://img.shields.io/badge/Angular-DD0031?style=flat-square&logo=angular&logoColor=white)

</td>
<td>Framework SPA para una interfaz rápida y reactiva en los duelos en tiempo real.</td>
</tr>
<tr>
<td><strong>Backend</strong></td>
<td>

![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=flat-square&logo=springboot&logoColor=white)

</td>
<td>API REST y WebSockets para sincronizar las partidas multijugador al milisegundo.</td>
</tr>
<tr>
<td><strong>Base de datos</strong></td>
<td>

![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=flat-square&logo=postgresql&logoColor=white)

</td>
<td>Almacén de preguntas, jugadores, partidas y estadísticas.</td>
</tr>
<tr>
<td><strong>Scraping</strong></td>
<td>

![Scrapy](https://img.shields.io/badge/Scrapy-60A839?style=flat-square&logo=scrapy&logoColor=white)

</td>
<td>Arañas de Python que recorren la web para alimentar el juego con datos frescos.</td>
</tr>
<tr>
<td><strong>Infraestructura</strong></td>
<td>

![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)

</td>
<td>Contenedores para el despliegue y la portabilidad del entorno.</td>
</tr>
</table>

---

## 👨‍💻 Equipo

| Miembro | GitHub |
|---|---|
| Adrián Martínez Granados | [ualamg538](https://github.com/ualamg538) |
| Raúl Martínez Gutiérrez | [ualrmg429](https://github.com/ualrmg429) |
| Sergio Gómez Vico | [ualsgv396](https://github.com/ualsgv396) |
| Andrés Ruíz Andujar | [UALara584](https://github.com/UALara584) |
| Bruno Ramirez Ledesma | [ualbrl973](https://github.com/ualbrl973) |
| Ilyas el Hamdi | [ilyas2022](https://github.com/ilyas2022) |
| Alejandro Ortega Ramón | [ualaor983](https://github.com/ualaor983) |

---

## 📄 Licencia

Pendiente de definir.

---

<div align="center">

*Elige modo. Elige rival. Y que gane el que menos dude.*

</div>