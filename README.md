# BookPulse API 🚀

**BookPulse** es el motor backend de una plataforma SaaS de gestión de reservas y citas en tiempo real. Este proyecto ha sido diseñado poniendo especial foco en el alto rendimiento, el control estricto de la concurrencia, la seguridad stateless, la automatización de tareas en segundo plano y la integración de pasarelas de pago y notificaciones omnicanal.

Desarrollado como proyecto avanzado para demostrar competencias full-stack integrales de cara al futuro.

---

## 🛠️ Stack Tecnológico & Decisiones de Arquitectura

* **Java 21:** Uso nativo de la API `java.time` para el cálculo algorítmico de franjas horarias y activación de **Virtual Threads** (`spring.threads.virtual.enabled=true`) para manejar peticiones concurrentes masivas con un consumo mínimo de memoria.
* **Spring Boot 3.5.x:** Framework base utilizando inyección de dependencias por constructor y transaccionalidad fina (`@Transactional`).
* **Spring Security & JWT Stateless:** Autenticación y autorización robustas mediante tokens **JWT (JSON Web Tokens)** firmados digitalmente. El backend es completamente *stateless*, delegando el ciclo de vida de la sesión en el cliente y optimizando el escalado horizontal.
* **Cifrado BCrypt:** Uso del algoritmo de hashing adaptativo BCrypt para el almacenamiento irreversible de contraseñas, garantizando la protección de credenciales según estándares de la industria.
* **Persistencia en la Nube (Neon PostgreSQL):** Integración con una base de datos serverless remota mediante conexiones seguras encriptadas (SSL), siguiendo la filosofía *Cloud-Native* a través de variables de entorno.
* **Bloqueo Optimista (Optimistic Locking):** Implementación de la anotación `@Version` en el modelo de datos. Evita el *overbooking* de forma ultraeficiente, impidiendo que dos usuarios reserven la misma franja simultáneamente sin bloquear las tablas de la BD.
* **Caché en Memoria (`@EnableCaching`):** Optimización del catálogo de servicios mediante `@Cacheable` para acelerar las lecturas públicas y `@CacheEvict` para invalidación instantánea tras modificaciones de administración.
* **Arquitectura Limpia & DDD Simplificado:** Código desacoplado y organizado por capas (`config`, `controller`, `dto`, `service`, `repository`, `model`, `exception`).

---

## ⚡ Integraciones de Terceros & Notificaciones

* **Pasarela de Pagos con Stripe:** Integración completa mediante **Stripe SDK** para la confirmación de reservas y cobros automáticos seguras con la API de Payments.
* **Notificaciones por Email con Resend:** Envíos automáticos de correos transaccionales con plantillas formateadas para confirmación de reserva, cancelación y recordatorios.
* **Notificaciones por WhatsApp con Twilio:** Integración con la API de Twilio para el envío de alertas transaccionales y avisos instantáneos directamente al teléfono móvil del usuario.

---

## 🔄 Automatizaciones Avanzadas (Background Tasks & Cron)

El sistema incluye un **Scheduler Autónomo** (`@Scheduled`) que ejecuta dos tareas automáticas en segundo plano:

1. **Limpieza de Pre-reservas Expiradas:** Se ejecuta cada 60 segundos buscando citas en estado `PENDING` que hayan superado el tiempo de cortesía (5 minutos) sin completar el pago o confirmación, pasándolas automáticamente a `CANCELLED` para liberar el hueco en tiempo real.
2. **Envío de Recordatorios a 24h:** Tarea programada (Cron) que escanea diariamente las citas confirmadas de las próximas 24 horas y dispara alertas por Email (Resend) y WhatsApp (Twilio), utilizando la bandera `reminderSent` para evitar duplicados.

---

## 🌐 Endpoints de la API

Todos los endpoints incluyen configuración nativa de **CORS** para conectar fluidamente con el frontend (React / Angular).

### 🔐 Autenticación (Públicos)

| Método | Endpoint | Body (JSON) | Descripción | Código Éxito |
| :--- | :--- | :--- | :--- | :--- |
| **POST** | `/api/auth/register` | `email`, `password`, `name` | Registra un nuevo cliente con password hasheada y devuelve su JWT. | `200 OK` |
| **POST** | `/api/auth/login` | `email`, `password` | Verifica credenciales y genera un token de acceso válido por 24 horas. | `200 OK` |

### 💇‍♂️ Catálogo de Servicios (Públicos con Caché)

| Método | Endpoint | Parámetros / Body | Descripción | Código Éxito |
| :--- | :--- | :--- | :--- | :--- |
| **GET** | `/api/services` | Ninguno | Obtiene la lista de servicios activos (Optimizado con `@Cacheable`). | `200 OK` |

### 📅 Gestión de Citas (Protegidos por Token JWT)

| Método | Endpoint | Parámetros / Body | Descripción | Código Éxito |
| :--- | :--- | :--- | :--- | :--- |
| **GET** | `/api/appointments/available` | `date` (yyyy-MM-dd) | Calcula y devuelve los huecos libres del día al vuelo. | `200 OK` |
| **POST** | `/api/appointments/reserve` | `serviceId`, `startTime` | Inicia la pre-reserva bloqueando el slot en estado `PENDING`. | `201 Created` |
| **POST** | `/api/appointments/confirm` | `appointmentId`, `paymentIntentId` | Confirma la reserva previo pago en Stripe y dispara notificaciones. | `200 OK` |

### 🛠️ Panel de Administración (Rol `ADMIN`)

| Método | Endpoint | Body (JSON) | Descripción | Código Éxito |
| :--- | :--- | :--- | :--- | :--- |
| **POST** | `/api/admin/services` | Datos del servicio | Crea un nuevo servicio e invalida la caché con `@CacheEvict`. | `201 Created` |
| **PUT** | `/api/admin/services/{id}` | Datos actualizados | Edita un servicio del catálogo y refresca la caché. | `200 OK` |
| **DELETE**| `/api/admin/services/{id}` | Ninguno | Desactiva/elimina un servicio del catálogo. | `204 No Content` |

---

## 🛡️ Gestión Global de Errores

Cualquier excepción del sistema es interceptada por un `@RestControllerAdvice`. En caso de conflicto de concurrencia (bloqueo optimista), errores de Stripe o fallos de negocio, la API responde de forma elegante:
* **Status:** `409 Conflict` / `400 Bad Request` / `401 Unauthorized`
* **JSON:** Con estructura limpia y mensaje amigable para el usuario final, protegiendo los trazos internos del servidor.

---

## ⚙️ Variables de Entorno Requeridas

Para ejecutar este proyecto en local (usando archivos `.env`) o desplegarlo en entornos Cloud (como **Render**), es necesario configurar las siguientes variables de sistema:

### 🗄️ Base de Datos & Seguridad
* `DB_URL`: URL de conexión JDBC de PostgreSQL (ej: `jdbc:postgresql://...neon.tech/neondb?sslmode=require`).
* `DB_USERNAME`: Usuario administrador de la base de datos Neon.
* `DB_PASSWORD`: Contraseña de acceso a la base de datos.
* `JWT_SECRET_KEY`: Clave secreta de alta entropía codificada en Base64 para firmar y validar los tokens JWT.

### 💳 Pasarela de Pagos (Stripe)
* `STRIPE_SECRET_KEY`: Clave privada de API de Stripe (`sk_test_...` o `sk_live_...`).

### 📧 Correo Electrónico (Resend)
* `RESEND_API_KEY`: API Key de la plataforma Resend.
* `RESEND_FROM_EMAIL`: Dirección de correo del remitente configurada en Resend.

### 💬 WhatsApp (Twilio)
* `TWILIO_ACCOUNT_SID`: Identificador de cuenta de Twilio.
* `TWILIO_AUTH_TOKEN`: Token de autenticación de Twilio.
* `TWILIO_WHATSAPP_NUMBER`: Número emisor de WhatsApp en formato internacional (ej: `whatsapp:+14155238886`).

---

## 📖 Documentación Automatizada (CI/CD)
Este repositorio cuenta con un flujo de integración continua que genera y despliega la **Javadoc** de forma automática en cada `git push` a la rama `main`.