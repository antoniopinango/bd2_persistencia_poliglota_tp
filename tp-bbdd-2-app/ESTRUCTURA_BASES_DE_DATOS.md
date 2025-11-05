# 📊 Estructura de Bases de Datos - TP BBDD 2 Persistencia Políglota

## 📄 MongoDB - `tp_sensores`

MongoDB se utiliza para almacenar datos transaccionales y documentos con estructura flexible.

### Colecciones Creadas

#### 1. **users** - Usuarios del sistema
**Campos:**
- `_id` (string) - UUID del usuario
- `fullName` (string) - Nombre completo
- `email` (string) - Email único
- `passwordHash` (string) - Hash de contraseña
- `status` (string) - Estado: "activo" o "inactivo"
- `registeredAt` (date) - Fecha de registro
- `updatedAt` (date) - Última actualización
- `department` (string) - Departamento

**Índices:**
- `idx_users_email` (UNIQUE) - Email único
- `idx_users_status` - Por estado
- `idx_users_registered` - Por fecha de registro (descendente)

**Validación:** JSON Schema con validación de email y campos requeridos

---

#### 2. **sessions** - Sesiones de usuario
**Campos:**
- `_id` (string) - ID de sesión
- `userId` (string) - ID del usuario
- `status` (string) - Estado de la sesión
- `expiresAt` (date) - Fecha de expiración

**Índices:**
- `idx_sessions_ttl` (TTL: 0s) - Expiración automática
- `idx_sessions_user_status` - Por usuario y estado

**Características:** TTL automático para limpieza de sesiones expiradas

---

#### 3. **roles** - Roles del sistema
**Campos:**
- `_id` (string) - ID del rol
- `name` (string) - Nombre del rol
- `description` (string) - Descripción

**Índices:**
- `idx_roles_name` (UNIQUE) - Nombre único

**Datos iniciales:** admin, usuario, tecnico

---

#### 4. **sensors** - Sensores con ubicación
**Campos:**
- `_id` (string) - UUID del sensor
- `name` (string) - Nombre del sensor
- `code` (string) - Código único
- `type` (string) - Tipo de sensor
- `location` (object) - Ubicación geoespacial
- `status` (string) - Estado del sensor
- `city` (string) - Ciudad
- `country` (string) - País
- `installDate` (date) - Fecha de instalación
- `lastMaintenance` (date) - Último mantenimiento
- `ownerId` (string) - Propietario

**Índices:**
- `idx_sensors_code` (UNIQUE) - Código único
- `idx_sensors_state` - Por estado
- `idx_sensors_city` - Por ciudad
- `idx_sensors_country` - Por país
- `idx_sensors_location` (2dsphere) - Índice geoespacial

---

#### 5. **processes** - Procesos disponibles
**Campos:**
- `_id` (string) - ID del proceso
- `name` (string) - Nombre del proceso
- `type` (string) - Tipo (reporte, consulta, etc.)
- `baseCost` (number) - Costo base

**Índices:**
- `idx_processes_name` (UNIQUE) - Nombre único
- `idx_processes_type` - Por tipo

---

#### 6. **process_requests** - Solicitudes de procesos
**Campos:**
- `_id` (string) - ID de la solicitud
- `userId` (string) - Usuario solicitante
- `processId` (string) - Proceso solicitado
- `status` (string) - Estado de la solicitud
- `requestedAt` (date) - Fecha de solicitud

**Índices:**
- `idx_requests_user_date` - Por usuario y fecha
- `idx_requests_status` - Por estado
- `idx_requests_process` - Por proceso

---

#### 7. **process_results** - Resultados de procesos
**Campos:**
- `_id` (string) - ID del resultado
- `requestId` (string) - ID de la solicitud
- `generatedAt` (date) - Fecha de generación

**Índices:**
- `idx_results_request` (UNIQUE) - Por solicitud
- `idx_results_generated` - Por fecha de generación

---

#### 8. **invoices** - Facturas
**Campos:**
- `_id` (string) - ID de la factura
- `userId` (string) - Usuario
- `issuedAt` (date) - Fecha de emisión
- `status` (string) - Estado

**Índices:**
- `idx_invoices_user_date` - Por usuario y fecha
- `idx_invoices_status` - Por estado

---

#### 9. **payments** - Pagos
**Campos:**
- `_id` (string) - ID del pago
- `invoiceId` (string) - Factura asociada
- `paidAt` (date) - Fecha de pago

**Índices:**
- `idx_payments_invoice` - Por factura
- `idx_payments_date` - Por fecha

---

#### 10. **accounts** - Cuentas de usuario
**Campos:**
- `_id` (object) - ID de la cuenta
- `userId` (string) - Usuario propietario
- `balance` (number) - Saldo
- `currency` (string) - Moneda
- `createdAt` (date) - Fecha de creación
- `status` (string) - Estado

**Índices:**
- `idx_accounts_user` (UNIQUE) - Usuario único

---

#### 11. **account_movements** - Movimientos de cuenta
**Campos:**
- `_id` (string) - ID del movimiento
- `accountId` (string) - Cuenta asociada
- `ts` (date) - Timestamp

**Índices:**
- `idx_movements_account_date` - Por cuenta y fecha

---

#### 12. **alerts** - Alertas del sistema
**Campos:**
- `_id` (string) - ID de la alerta
- `sensorId` (string) - Sensor asociado
- `status` (string) - Estado
- `openedAt` (date) - Fecha de apertura

**Índices:**
- `idx_alerts_status_date` - Por estado y fecha
- `idx_alerts_sensor_status` - Por sensor y estado

---

#### 13. **groups_meta** - Metadatos de grupos
**Campos:**
- `_id` (string) - ID del grupo
- `name` (string) - Nombre del grupo

**Índices:**
- `idx_groups_name` (UNIQUE) - Nombre único

---

## 🔗 Cassandra - Keyspace `tp_sensores`

Cassandra se utiliza para series temporales y datos de alta escritura/lectura.

**Configuración del Keyspace:**
- Replication Strategy: SimpleStrategy
- Replication Factor: 1

### Tablas de Mediciones (Time-Series)

#### 1. **measurements_by_sensor_day**
Mediciones por sensor y día (consultas por sensor específico)

**Partition Key:** `(sensor_id, day)`  
**Clustering Key:** `ts DESC`

**Columnas:**
- `sensor_id` (uuid)
- `day` (date)
- `ts` (timeuuid) - Timestamp ordenado
- `city` (text)
- `country` (text)
- `humidity` (double)
- `temperature` (double)
- `type` (text)

**TTL:** 180 días (15552000 segundos)  
**Compaction:** TimeWindowCompactionStrategy (1 día)

---

#### 2. **measurements_by_city_day**
Mediciones por ciudad y día

**Partition Key:** `(city, day)`  
**Clustering Key:** `ts DESC, sensor_id ASC`

**Columnas:**
- `city` (text)
- `day` (date)
- `ts` (timeuuid)
- `sensor_id` (uuid)
- `country` (text)
- `humidity` (double)
- `temperature` (double)
- `type` (text)

**TTL:** 180 días  
**Compaction:** TimeWindowCompactionStrategy (1 día)

---

#### 3. **measurements_by_country_day**
Mediciones por país y día

**Partition Key:** `(country, day)`  
**Clustering Key:** `ts DESC, city ASC, sensor_id ASC`

**Columnas:**
- `country` (text)
- `day` (date)
- `ts` (timeuuid)
- `city` (text)
- `sensor_id` (uuid)
- `humidity` (double)
- `temperature` (double)
- `type` (text)

**TTL:** 180 días  
**Compaction:** TimeWindowCompactionStrategy (1 día)

---

#### 4. **last_measurement_by_sensor**
Última medición de cada sensor (tabla de estado actual)

**Primary Key:** `sensor_id`

**Columnas:**
- `sensor_id` (uuid)
- `city` (text)
- `country` (text)
- `humidity` (double)
- `temperature` (double)
- `ts` (timeuuid)
- `type` (text)

**Compaction:** SizeTieredCompactionStrategy

---

### Tablas de Agregaciones

#### 5. **agg_city_day**
Agregaciones diarias por ciudad

**Partition Key:** `(city, year)`  
**Clustering Key:** `day ASC`

**Columnas:**
- `city` (text)
- `year` (int)
- `day` (date)
- `temp_sum`, `temp_min`, `temp_max`, `temp_count` (double/bigint)
- `hum_sum`, `hum_min`, `hum_max`, `hum_count` (double/bigint)
- `updated_at` (timestamp)

**Compaction:** TimeWindowCompactionStrategy (7 días)

---

#### 6. **agg_city_month**
Agregaciones mensuales por ciudad

**Primary Key:** `(city, month)`

**Columnas:**
- `city` (text)
- `month` (int)
- `temp_sum`, `temp_min`, `temp_max`, `temp_count` (double/bigint)
- `hum_sum`, `hum_min`, `hum_max`, `hum_count` (double/bigint)
- `updated_at` (timestamp)

---

#### 7. **agg_country_month**
Agregaciones mensuales por país

**Primary Key:** `(country, month)`

**Columnas:**
- `country` (text)
- `month` (int)
- `temp_sum`, `temp_min`, `temp_max` (double)
- `hum_sum`, `hum_min`, `hum_max`, `hum_count` (double/bigint)
- `updated_at` (timestamp)

---

### Tablas de Alertas

#### 8. **alerts_by_sensor**
Alertas por sensor

**Partition Key:** `sensor_id`  
**Clustering Key:** `ts DESC, alert_id ASC`

**Columnas:**
- `sensor_id` (uuid)
- `ts` (timeuuid)
- `alert_id` (uuid)
- `city`, `country` (text)
- `description` (text)
- `severity` (text)
- `status` (text)
- `type` (text)

**Compaction:** TimeWindowCompactionStrategy (7 días)

---

#### 9. **alerts_by_city_day**
Alertas por ciudad y día

**Partition Key:** `(city, day)`  
**Clustering Key:** `ts DESC, sensor_id ASC, alert_id ASC`

**Columnas:**
- `city` (text)
- `day` (date)
- `ts` (timeuuid)
- `sensor_id` (uuid)
- `alert_id` (uuid)
- `description` (text)
- `severity` (text)
- `status` (text)
- `type` (text)

**Compaction:** TimeWindowCompactionStrategy (1 día)

---

### Tablas de Mensajería

#### 10. **messages_by_conversation**
Mensajes por conversación

**Partition Key:** `conversation_id`  
**Clustering Key:** `ts DESC, message_id ASC`

**Columnas:**
- `conversation_id` (uuid)
- `ts` (timeuuid)
- `message_id` (timeuuid)
- `content` (text)
- `sender_id` (uuid)
- `type` (text)
- `metadata` (map<text, text>)

**Compaction:** TimeWindowCompactionStrategy (1 día)

---

#### 11. **conversations_by_user**
Conversaciones por usuario

**Partition Key:** `user_id`  
**Clustering Key:** `last_activity_ts DESC, conversation_id ASC`

**Columnas:**
- `user_id` (uuid)
- `last_activity_ts` (timeuuid)
- `conversation_id` (uuid)
- `last_message_snippet` (text)
- `unread_count` (int)

---

### Tablas de Logs y Salud

#### 12. **exec_log_by_request**
Logs de ejecución por solicitud

**Partition Key:** `request_id`  
**Clustering Key:** `ts ASC`

**Columnas:**
- `request_id` (uuid)
- `ts` (timeuuid)
- `message` (text)
- `result_pointer` (text)
- `status` (text)
- `step` (text)
- `metrics` (map<text, text>)

---

#### 13. **sensor_health_checks**
Chequeos de salud de sensores

**Partition Key:** `(sensor_id, day)`  
**Clustering Key:** `ts DESC`

**Columnas:**
- `sensor_id` (uuid)
- `day` (date)
- `ts` (timeuuid)
- `notes` (text)
- `state` (text)

**Compaction:** TimeWindowCompactionStrategy (1 día)

---

## 🕸️ Neo4j - Base de datos de grafos

Neo4j se utiliza para relaciones complejas, permisos y jerarquías.

### Nodos (Labels)

#### 1. **User** - Usuarios
**Propiedades:**
- `id` (string, UNIQUE)
- `email` (string, UNIQUE)
- `fullName` (string)
- `department` (string)

**Constraints:**
- `user_id`: Uniqueness en `id`
- `user_email`: Uniqueness en `email`

---

#### 2. **Role** - Roles
**Propiedades:**
- `id` (string, UNIQUE)
- `name` (string, UNIQUE)
- `description` (string)

**Constraints:**
- `role_id`: Uniqueness en `id`
- `role_name`: Uniqueness en `name`

---

#### 3. **Group** - Grupos
**Propiedades:**
- `id` (string, UNIQUE)
- `name` (string, UNIQUE)
- `description` (string)

**Constraints:**
- `group_id`: Uniqueness en `id`
- `group_name`: Uniqueness en `name`

---

#### 4. **ProcessType** - Tipos de procesos
**Propiedades:**
- `id` (string, UNIQUE)
- `name` (string, UNIQUE)
- `description` (string)

**Constraints:**
- `processtype_id`: Uniqueness en `id`
- `processtype_name`: Uniqueness en `name`

---

#### 5. **Sensor** - Sensores
**Propiedades:**
- `id` (string, UNIQUE)
- `code` (string, UNIQUE)
- `name` (string)
- `type` (string)

**Constraints:**
- `sensor_id`: Uniqueness en `id`
- `sensor_code`: Uniqueness en `code`

---

#### 6. **City** - Ciudades
**Propiedades:**
- `name` (string, UNIQUE)

**Constraints:**
- `city_name`: Uniqueness en `name`

---

#### 7. **Country** - Países
**Propiedades:**
- `name` (string, UNIQUE)

**Constraints:**
- `country_name`: Uniqueness en `name`

---

### Relaciones (Relationship Types)

#### 1. **HAS_ROLE**
Usuario tiene un rol

```
(User)-[:HAS_ROLE]->(Role)
```

---

#### 2. **CAN_EXECUTE**
Rol puede ejecutar un tipo de proceso

```
(Role)-[:CAN_EXECUTE]->(ProcessType)
```

---

#### 3. **IN_CITY**
Sensor está en una ciudad

```
(Sensor)-[:IN_CITY]->(City)
```

---

#### 4. **IN_COUNTRY**
Ciudad está en un país

```
(City)-[:IN_COUNTRY]->(Country)
```

---

## 📋 Resumen de Uso

### MongoDB (13 colecciones)
- **Propósito:** Datos transaccionales, documentos flexibles, metadatos
- **Total documentos actuales:** 42
- **Características:** Validación JSON Schema, TTL, índices geoespaciales

### Cassandra (13 tablas)
- **Propósito:** Series temporales, alta escritura, datos de sensores
- **Características:** Particionamiento por tiempo, TTL, agregaciones pre-calculadas
- **Estrategias de compactación:** TimeWindow y SizeTiered

### Neo4j (7 labels, 4 relaciones)
- **Propósito:** Relaciones complejas, permisos, jerarquías geográficas
- **Características:** Constraints de unicidad, consultas de grafos eficientes

---

## 🔍 Patrones de Consulta Principales

### MongoDB
- Búsqueda de usuarios por email
- Gestión de sesiones con TTL
- Consultas geoespaciales de sensores
- Metadatos de procesos y facturación

### Cassandra
- Mediciones recientes de un sensor específico
- Mediciones por ciudad/país en un rango de fechas
- Última medición de todos los sensores
- Agregaciones diarias/mensuales
- Historial de alertas

### Neo4j
- Permisos de usuario (User -> Role -> ProcessType)
- Ubicación de sensores (Sensor -> City -> Country)
- Jerarquías geográficas
- Consultas de autorización complejas

---



