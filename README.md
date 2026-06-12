# BookPulse API 🚀

**BookPulse** es el motor backend de una plataforma SaaS de gestión de reservas y citas en tiempo real. Este proyecto ha sido diseñado poniendo especial foco en el alto rendimiento, el control estricto de la concurrencia, la seguridad stateless y la automatización de procesos en segundo plano.

Desarrollado como proyecto avanzado para demostrar competencias full-stack integrales de cara al futuro.

---

## 🛠️ Stack Tecnológico & Decisiones de Arquitectura

* **Java 21:** Uso nativo de la API `java.time` para el cálculo algorítmico de franjas horarias y activación de **Virtual Threads** (`spring.threads.virtual.enabled=true`) para manejar millones de peticiones concurrentes con un consumo mínimo de memoria.
* **Spring Boot 3.5.x:** Framework base utilizando inyección de dependencias por constructor y transaccionalidad fina (`@Transactional`).
* **Spring Security & JWT Stateless:** Autenticación y autorización robustas mediante tokens **JWT (JSON Web Tokens)** firmados digitalmente. El backend es completamente *stateless*, delegando el ciclo de vida de la sesión en el frontend y optimizando el escalado del servidor.
* **Cifrado BCrypt:** Uso del algoritmo de hashing adaptativo BCrypt para el almacenamiento irreversible de contraseñas, garantizando la protección de credenciales según los estándares de la industria.
* **Persistencia en la Nube (Neon PostgreSQL):** Integración con una base de datos serverless remota mediante conexiones seguras encriptadas (SSL), siguiendo la filosofía *Cloud-Native* a través de variables de entorno.
* **Bloqueo Optimista (Optimistic Locking):** Implementación de la anotación `@Version` en el modelo de datos. Evita el *overbooking* de forma ultraeficiente, impidiendo que dos usuarios reserven el mismo hueco simultáneamente sin bloquear las tablas de la BD.
* **Arquitectura Limpia & DDD Simplificado:** Código desacoplado y organizado por capas (`config`, `controller`, `dto`, `service`, `repository`, `model`, `exception`).

---

## 🔄 Automatizaciones Avanzadas (Background Tasks)

El sistema incluye un **Scheduler Autónomo** (`@Scheduled`) que se ejecuta en segundo plano cada 60 segundos. Su función es buscar pre-reservas en estado `PENDING` que hayan superado el tiempo de cortesía (5 minutos) sin ser confirmadas, pasándolas automáticamente a `CANCELLED` para liberar los huecos en tiempo real.

---

## 🌐 Endpoints de la API

Todos los endpoints incluyen configuración nativa de **CORS** para conectar fluidamente con el frontend en Angular.

### 🔐 Autenticación (Públicos)

| Método | Endpoint | Body (JSON) | Descripción | Código Éxito |
| :--- | :--- | :--- | :--- | :--- |
| **POST** | `/api/auth/register` | `email`, `password`, `name` | Registra un nuevo cliente con password hasheada y devuelve su JWT. | `200 OK` |
| **POST** | `/api/auth/login` | `email`, `password` | Verifica las credenciales y genera un token de acceso válido por 24 horas. | `200 OK` |

### 📅 Gestión de Citas (Protegidos)
*Nota: Próximamente estos endpoints requerirán la cabecera `Authorization: Bearer <token>`.*

| Método | Endpoint | Parámetros | Descripción | Código Éxito |
| :--- | :--- | :--- | :--- | :--- |
| **GET** | `/api/appointments/available` | `date` (yyyy-MM-dd) | Calcula y devuelve los huecos libres del día al vuelo. | `200 OK` |
| **POST** | `/api/appointments/reserve` | `startTime` (ISO DateTime) | Inicia la pre-reserva bloqueando el slot en estado `PENDING`. | `201 Created` |

### 🛡️ Gestión Global de Errores
Cualquier excepción del sistema es interceptada por un `@RestControllerAdvice`. En caso de conflicto de concurrencia (bloqueo optimista) o fallos de negocio, la API responde de forma elegante:
* **Status:** `409 Conflict` / `400 Bad Request`
* **JSON:** Con estructura limpia y mensaje amigable para el usuario final, protegiendo los trazos internos del servidor.

---

## ⚙️ Variables de Entorno Requeridas

Para ejecutar este proyecto en local (usando archivos `.env`) o desplegarlo en entornos Cloud (como **Render**), es necesario configurar las siguientes variables de sistema:

* `DB_URL`: URL de conexión JDBC de PostgreSQL (ej: `jdbc:postgresql://...neon.tech/neondb?sslmode=require`).
* `DB_USERNAME`: Usuario administrador de la base de datos Neon.
* `DB_PASSWORD`: Contraseña de acceso a la base de datos.
* `JWT_SECRET_KEY`: Clave secreta de alta entropía codificada en Base64 para firmar y validar los tokens JWT.

---

## 📖 Documentación Automatizada (CI/CD)
Este repositorio cuenta con un flujo de integración continua que genera y despliega el **Javadoc** de forma automática en cada `git push` a la rama `main`.