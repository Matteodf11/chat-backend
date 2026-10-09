# Retoma del proyecto: 9 de octubre de 2026

## Cambios encontrados y conservados

Git mostraba 32 archivos modificados y 10 nuevos. Incluían cambios de otra IA:
excepciones de dominio, formato de errores, configuración de seguridad,
paginación de listas, campos deletedAt y migración V14 de borrado lógico.

OpenCode.txt y DeepAnalisis.txt son informes locales con credenciales en claro.
Se conservan en disco, pero se excluyen expresamente de Git. Este documento
resume hallazgos comprobados sin copiar los secretos.

## Regresiones corregidas durante la revisión

- Listas REST: los clientes nativos esperan arrays. Se conserva ese contrato
  por defecto y se activa PageResponse únicamente con paged=true.
  Ejemplo: /api/friends/dm?paged=true&page=1&size=20&sort=createdAt,desc.
- DM: se sustituyen dos consultas paginadas y una segunda sublista en memoria
  por una consulta OR que pagina una sola vez y cuenta ambas posiciones.
- Cursor de mensajes: se recuperan createdAt < before y createdAt > after
  en canales y DM; se mantiene el filtro de mensajes borrados.
- Borrado lógico: los servicios usan búsquedas de registros activos, las
  membresías borradas dejan de listar servidores y el borrado masivo de canales
  se marca con @Modifying. V14 conserva las columnas requeridas.
- La consulta de servidores parte de Server para que createdAt ordene sobre
  la entidad correcta.
- Autenticación incorrecta responde 401. Los errores internos devuelven un
  mensaje genérico al cliente y se registran en logs.
- Los errores de negocio de amigos, usuarios y servidores usan excepciones
  tipadas. El cliente se adapta a message/details.
- Se elimina la clave pública de cifrado por defecto y la plantilla deja de
  sugerirla como clave utilizable.
- Render declara el perfil prod y los nombres DB_* que consume ese perfil,
  además de las variables de cifrado y almacenamiento. No se ha desplegado.
- CORS es configurable; se elimina la confianza en cualquier subdominio Render.
  CORS_ALLOWED_ORIGINS admite orígenes concretos separados por comas.

## Pruebas y límites

Comando: .\mvnw.cmd test -q

25 pruebas ejecutadas, sin fallos ni errores.

La suite usa H2 local, Hibernate create-drop y Flyway desactivado. Incluye
pruebas HTTP reales con MockMvc y consultas JPA: ambos formatos de siete listas,
segunda página de DM, cursores en las dos direcciones, filtros de borrado,
borrado masivo de canales, errores de login y CORS.

Estas pruebas no validan las migraciones en PostgreSQL ni conectan a Supabase.
No se ha aplicado V14 a una base de datos real.

## Prioridades pendientes

1. **Credenciales expuestas**: hay configuración histórica que fue retirada del
   seguimiento en un commit posterior. Los informes también copian secretos.
   Tratar DB/JWT/Supabase citados como comprometidos y rotarlos desde los servicios
   correspondientes. No se han rotado ni reescrito commits históricos.
2. **Clave de cifrado existente**: conservar la clave con que se cifraron mensajes.
   Cambiarla sin migración vuelve ilegible el contenido anterior. La aplicación
   ahora exige configurarla explícitamente.
3. **Esquema PostgreSQL desde cero**: las migraciones disponibles crean app_users,
   server_invitations y auth_sessions; faltan CREATE TABLE del núcleo usado por
   las entidades. Obtener el DDL real y diseñar una reparación compatible con
   instalaciones existentes, con una prueba PostgreSQL/Flyway aislada.
4. **Rate limiting**: AuthController confía directamente en X-Forwarded-For.
   Definir proxies confiables; ampliar límites de refresh y cambio de contraseña.
5. **Privacidad y archivos**: revisar emails de perfiles públicos, URLs externas,
   validación de firmas de imágenes y limpieza de adjuntos al borrar servidores.
6. **Escala y operaciones**: búsqueda limitada a 1000 mensajes, backfill de cifrado
   que carga la tabla, timeouts del almacenamiento y CI que omite pruebas.

No todas las recomendaciones de los informes son urgentes o correctas:
CORS no condiciona a un cliente nativo Android/Desktop; Redis o Kubernetes no
son requisitos para una instancia; un índice full-text sobre texto AES-GCM
no permite buscar el texto original; PostgreSQL no crea automáticamente índices
en cada clave foránea. El borrado lógico tampoco equivale a eliminación de datos.

## Recuperación

Se guardó el estado previo completo y un diff binario en:

C:\Users\Matteo\Documents\Proyecto Chat\.recovery\2026-10-09-before-cleanup\chat-backend

Es una copia local privada; contiene los informes originales y debe tratarse
como sensible. No forma parte de los commits ni se ha subido a ningún remoto.
