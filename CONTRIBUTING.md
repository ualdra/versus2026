# Guía de entorno de desarrollo — DeerDays

Todo el equipo trabajando sobre el mismo entorno, sin instalaciones manuales ni "en mi máquina funciona".

---

## Antes de empezar

Asegúrate de tener instalado:

- **Docker Desktop** — debe estar corriendo antes de abrir VS Code
- **VS Code** con la extensión [Dev Containers](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers) (`ms-vscode-remote.remote-containers`)
- El repositorio clonado y el archivo `.env` creado a partir de `.env.example`

```bash
git clone <url-del-repo>
cd deerdays
cp .env.example .env
```

---

## Frontend — Angular

### 1. Abrir VS Code y abrir en contenedor

Abre VS Code desde cualquier lugar. Si lo haces directamente en la carpeta `frontend`, mejor,
ya que VS Code detectará el `.devcontainer/devcontainer.json` y mostrará una notificación en la esquina inferior derecha. También puedes abrirlo manualmente:


`Ctrl+Shift+P` → **Dev Containers: Reopen in Container**

![Reopen in Container](img/reopen-in-container.png)

Busca la carpeta `frontend`, seleccionala, y abrela.

---

### 2. Espera a que se construya el contenedor

La primera vez tardará varios minutos mientras Docker descarga la imagen de Node e instala Angular CLI. Puedes seguir el progreso en la pestaña **Output → Dev Containers**.

> **Solo la primera vez.** Las siguientes aperturas arrancan en segundos porque Docker cachea las capas de la imagen.

---

### 3. ¡Listo! El servidor ya está corriendo

En cuanto el contenedor arranca, `ng serve` se lanza automáticamente. La barra de estado inferior izquierda de VS Code cambiará a azul e indicará que estás dentro del contenedor.

| Servicio | URL |
|---|---|
| App Angular (hot reload) | http://localhost:4200 |

Guarda cualquier archivo y el navegador se actualizará solo.

![Frontend Setup](img/frontend-setup.png)

---

## Backend — Spring Boot

### 1. Abre una segunda ventana de VS Code con el backend

Puedes tener ambos Dev Containers abiertos al mismo tiempo en ventanas separadas. Abre una nueva ventana (`Ctrl+Shift+N`) y desde ella abre la carpeta `backend/`.

---

### 2. Reabrir en el contenedor

Igual que con el frontend:

`Ctrl+Shift+P` → **Dev Containers: Reopen in Container**

---

### 3. Spring Boot arranca automáticamente

El contenedor lanza `mvn spring-boot:run` solo al arrancar. No tienes que hacer nada — en cuanto VS Code termina de conectarse, el servidor ya está levantando en segundo plano.

La primera vez Maven descargará todas las dependencias del `pom.xml`. Puede tardar unos minutos. Las siguientes veces arranca en segundos gracias al caché de Maven montado como volumen Docker.

![Backend Setup](img/backend-setup.png)

---

### 4. Puertos disponibles

VS Code redirige estos puertos a tu máquina automáticamente (los verás en la pestaña **Ports**):

| Puerto | Servicio |
|---|---|
| `:8080` | API REST de Spring Boot |
| `:5005` | Debug remoto (IntelliJ o VS Code) |
| `:5432` | PostgreSQL |
| `:5050` | pgAdmin en el navegador |

---

## ¿Cambios en las dependencias?

Si alguien añade una dependencia al `pom.xml` o al `package.json`, el resto del equipo solo tiene que ejecutar:

`Ctrl+Shift+P` → **Dev Containers: Rebuild Container**

Nada de `npm install` manuales ni conflictos de versiones.
