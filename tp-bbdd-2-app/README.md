# 🎓 TP BBDD 2 - Persistencia Políglota

Aplicación Java que demuestra el uso de persistencia políglota integrando **MongoDB**, **Cassandra** y **Neo4j** para un sistema completo de gestión de sensores, procesos, facturación y mensajería.

**Universidad**: UADE  
**Materia**: Ingeniería de Datos II  
**Versión**: 2.0.0

---

## 📊 Arquitectura de Persistencia Políglota

### ¿Por qué 3 Bases de Datos?

Cada base de datos se especializa en lo que hace mejor:

| Base de Datos | Propósito | Datos Almacenados |
|---------------|-----------|-------------------|
| **MongoDB** | Transaccional | Usuarios, Sensores, Procesos, Facturas, Pagos, Cuentas |
| **Cassandra** | Series Temporales | Mediciones, Mensajes, Logs, Agregaciones |
| **Neo4j** | RBAC y Relaciones | Roles, Permisos, Jerarquías Geográficas |

### Justificación Técnica

**MongoDB**:
- ✅ Esquema flexible para entidades variables
- ✅ Índices secundarios rápidos (email, status)
- ✅ Índices geoespaciales (2dsphere) para sensores
- ✅ TTL automático para sesiones
- ✅ Transacciones ACID para facturación

**Cassandra**:
- ✅ Optimizado para escrituras masivas (millones de mediciones)
- ✅ Particionamiento por tiempo (sensor+día, ciudad+día)
- ✅ TTL automático (180 días para mediciones)
- ✅ Agregaciones pre-calculadas
- ✅ Modelo desnormalizado para consultas rápidas

**Neo4j**:
- ✅ Relaciones complejas (User→Role→ProcessType)
- ✅ Queries de grafos eficientes para permisos
- ✅ Jerarquías geográficas (Sensor→City→Country)
- ✅ Asignación de técnicos por ciudad
- ✅ Constraints de unicidad

---

## 🚀 Inicio Rápido

### Requisitos

- Java 17+
- Docker
- Maven 3.6+

### Ejecución Simple (Un Solo Comando)

```bash
cd "/Users/apinango/Desktop/Personal/WorkSpaceUADE/ING. DE DATOS 2/tp-bbdd-2-app/tp-bbdd-2-app"
./start.sh
```

Este script:
1. Inicia las bases de datos (MongoDB, Cassandra, Neo4j)
2. Espera 2 minutos para que Cassandra esté listo
3. Compila el proyecto
4. Ejecuta la aplicación

**Tiempo total**: ~2-3 minutos

### Login

**Recomendado para demo**:
```
Email: maria.gonzalez@uade.edu.ar
Contraseña: password123
Rol: Operador (tiene todos los permisos necesarios)
```

O usar admin:
```
Email: admin@admin.com
Contraseña: admin
```

---

## 🎯 Funcionalidades Implementadas

### 1. 👤 Gestión de Usuarios
- Registro de usuarios con roles
- Autenticación con verificación de permisos en Neo4j
- Perfiles de usuario por email
- Listado por departamento

### 2. 📊 Gestión de Sensores y Mediciones
- **Creación de sensores** (solo admins) ⭐
  - Se guarda en MongoDB
  - Se sincroniza automáticamente a Neo4j
  - Código único generado automáticamente
- Registro de mediciones (temperatura y humedad)
- Consulta de mediciones por sensor
- Consulta de mediciones por ciudad
- Estado actual de sensores
- Asignación de técnicos a ciudades

### 3. 📋 Gestión de Procesos y Reportes ⭐
- **Solicitud de procesos**: Se guarda en MongoDB
- **Ejecución de reportes**: Consulta datos de Cassandra
- **Tipos de reportes**:
  - Max/Min de temperatura y humedad por ciudad/fecha
  - Promedios de temperatura y humedad por ciudad/fecha
  - Alertas en rangos específicos
- **Historial de ejecución**: Almacenado en MongoDB
- **Resultados persistentes**: Guardados para consulta posterior

### 4. 💬 Mensajería ⭐
- Mensajes privados entre usuarios (por email)
- Almacenamiento en Cassandra optimizado para series temporales
- Conversaciones ordenadas por actividad
- Ver mensajes con formato "Tú" vs "Otro Usuario"

### 5. 💰 Facturación y Cuenta Corriente ⭐
- **Generación automática** de facturas al completar procesos
- **Débito automático** de cuenta corriente
- **Costos por tipo de proceso**:
  - Reporte Max/Min: $15
  - Reporte Promedios: $10
  - Reporte Alertas: $5
- **Registro de movimientos** en cuenta corriente
- **Control de pagos** y estados de factura

### 6. 🔐 Sistema de Permisos (RBAC)
- Roles almacenados en Neo4j
- Menú dinámico según permisos del usuario
- Verificación en 3 niveles (menú, submenú, operación)
- 4 roles: Administrador, Operador, Analista, Técnico

### 7. 📈 Dashboard y Estadísticas
- Estadísticas del sistema desde Neo4j
- Estado de sensores
- Información de usuarios
- Acceso diferenciado según rol

---

## 📋 Menú de la Aplicación

```
🏠 MENÚ PRINCIPAL
============================================================
1. 👤 Gestión de Usuarios                    (solo admins)
2. 📊 Gestión de Sensores y Mediciones       (operadores, técnicos, admins)
3. 📋 Gestión de Procesos y Reportes        (todos según permisos)
4. 💬 Mensajería                             (todos)
5. 💰 Facturación y Cuenta Corriente        (todos)
6. 📈 Dashboard y Estadísticas               (todos)
7. 🔧 Información del Sistema                (todos)
============================================================
```

El número de opciones visibles depende de los permisos del usuario.

---

## 🔑 Usuarios de Prueba

Todos los usuarios tienen contraseña: `password123` (excepto admin)

| Email | Rol | Permisos | Opciones Menú |
|-------|-----|----------|---------------|
| admin@admin.com (pwd: admin) | Administrador | TODOS | 7 |
| maria.gonzalez@uade.edu.ar | Operador | pt_maxmin, pt_prom | 6 |
| carlos.rodriguez@uade.edu.ar | Analista | pt_prom | 4 |
| ana.martinez@uade.edu.ar | Técnico | pt_maxmin, pt_alerts | 5 |

---

## 📡 IDs de Sensores (Para Pruebas)

Los sensores tienen IDs fijos para facilitar las pruebas:

```
Buenos Aires - Laboratorio A:  550e8400-e29b-41d4-a716-446655440001
Buenos Aires - Laboratorio B:  550e8400-e29b-41d4-a716-446655440002
Córdoba - Aula Magna:          550e8400-e29b-41d4-a716-446655440003
Córdoba - Biblioteca:          550e8400-e29b-41d4-a716-446655440004
Rosario - Sala de Servidores:  550e8400-e29b-41d4-a716-446655440005
Mendoza - Laboratorio C:       550e8400-e29b-41d4-a716-446655440006
La Plata - Aula 101:           550e8400-e29b-41d4-a716-446655440007
```

---

## 🎯 Demo Completa (3 minutos)

### 1. Demostrar Permisos Diferenciados (30 seg)
```
Login admin → Muestra 7 opciones
Logout y login operador → Muestra 6 opciones
Explicar: Permisos vienen de Neo4j (grafo de relaciones)
```

### 2. Flujo de Proceso Completo (1 min)
```
Opción 3: Gestión de Procesos
→ Solicitar reporte Max/Min (MongoDB: process_requests)
→ Ejecutar proceso (Cassandra: consulta mediciones)
→ Ver resultado (MongoDB: process_results)
→ Factura generada automáticamente (MongoDB: invoices)
```

### 3. Facturación (30 seg)
```
Opción 5: Facturación
→ Ver facturas (muestra la del proceso)
→ Ver saldo (muestra débito automático)
```

### 4. Mensajería (1 min)
```
Opción 4: Mensajería
→ Enviar mensaje a maria.gonzalez@uade.edu.ar (Cassandra)
→ Ver conversación (muestra historial)
```

**Total**: 3 minutos demostrando las 3 BDs + todas las funcionalidades

---

## 🏗️ Estructura del Proyecto

```
src/main/java/com/bd2/app/
├── Application.java                # Menú principal y flujos (1200+ líneas)
├── config/
│   └── DatabaseConfig.java         # Configuración centralizada
├── database/
│   ├── MongoConnectionManager.java # Pool de conexiones MongoDB
│   ├── CassandraConnectionManager.java # Pool Cassandra
│   └── Neo4jConnectionManager.java # Pool Neo4j
├── model/
│   ├── User.java                   # Modelo de Usuario
│   ├── Sensor.java                 # Modelo de Sensor
│   ├── Measurement.java            # Modelo de Medición
│   └── ProcessRequest.java         # Modelo de Solicitud de Proceso
├── dao/
│   ├── UserDAO.java                # MongoDB - Usuarios
│   ├── MeasurementDAO.java         # Cassandra - Mediciones + Estadísticas
│   └── AuthorizationDAO.java       # Neo4j - Permisos y Roles
├── service/
│   ├── UserService.java            # Lógica de usuarios + Neo4j
│   ├── SensorService.java          # Lógica de sensores + Cassandra
│   ├── ProcessService.java         # Gestión de procesos ⭐
│   ├── InvoiceService.java         # Facturación automática ⭐
│   └── MessageService.java         # Mensajería en Cassandra ⭐
├── migrations/
│   ├── MigrationRunner.java        # Ejecutor de migraciones
│   ├── mongodb/MongoMigrations.java
│   ├── cassandra/CassandraMigrations.java
│   └── neo4j/Neo4jMigrations.java
└── seeder/
    └── DataSeeder.java             # Poblado automático de datos
```

---

## 🗄️ Bases de Datos

### MongoDB - 13 Colecciones

- **users** - Usuarios (email único, password hash)
- **sessions** - Sesiones con TTL
- **roles** - Roles del sistema
- **sensors** - Sensores con ubicación geoespacial
- **processes** - Tipos de procesos disponibles
- **process_requests** - Solicitudes de procesos ⭐
- **process_results** - Resultados de procesos ⭐
- **invoices** - Facturas ⭐
- **payments** - Pagos ⭐
- **accounts** - Cuentas corrientes ⭐
- **account_movements** - Movimientos ⭐
- **alerts** - Alertas del sistema
- **groups_meta** - Metadatos de grupos

### Cassandra - 13 Tablas

**Series Temporales de Mediciones**:
- **measurements_by_sensor_day** - Particionado por sensor+día
- **measurements_by_city_day** - Particionado por ciudad+día
- **measurements_by_country_day** - Particionado por país+día
- **last_measurement_by_sensor** - Estado actual de sensores

**Agregaciones**:
- **agg_city_day** - Agregaciones diarias
- **agg_city_month** - Agregaciones mensuales
- **agg_country_month** - Agregaciones por país

**Mensajería** ⭐:
- **messages_by_conversation** - Mensajes por conversación
- **conversations_by_user** - Conversaciones por usuario

**Alertas y Logs**:
- **alerts_by_sensor** - Alertas por sensor
- **alerts_by_city_day** - Alertas por ciudad
- **exec_log_by_request** - Logs de ejecución
- **sensor_health_checks** - Chequeos de salud

### Neo4j - Grafo de Permisos y Relaciones

**Nodos**:
- **User** - Usuarios del sistema
- **Role** - Roles (admin, usuario, tecnico)
- **Group** - Grupos de usuarios
- **ProcessType** - Tipos de procesos ejecutables
- **Sensor** - Sensores
- **City** - Ciudades
- **Country** - Países

**Relaciones**:
- **(User)-[:HAS_ROLE]->(Role)** - Asignación de roles
- **(Role)-[:CAN_EXECUTE]->(ProcessType)** - Permisos por rol
- **(User)-[:CAN_EXECUTE]->(ProcessType)** - Permisos directos
- **(User)-[:MEMBER_OF]->(Group)** - Membresía de grupos
- **(Group)-[:CAN_EXECUTE]->(ProcessType)** - Permisos de grupos
- **(Sensor)-[:IN_CITY]->(City)** - Ubicación de sensores
- **(City)-[:IN_COUNTRY]->(Country)** - Jerarquía geográfica
- **(User)-[:COVERS_CITY]->(City)** - Técnicos asignados

---

## 💡 Flujos de Negocio

### Flujo 1: Solicitar y Ejecutar Proceso

```
1. Usuario solicita reporte
   ↓ (MongoDB: process_requests)
2. Sistema verifica permisos
   ↓ (Neo4j: User→Role→ProcessType)
3. Usuario ejecuta proceso
   ↓ (Cassandra: consulta measurements_by_city_day)
4. Sistema genera resultado
   ↓ (MongoDB: process_results)
5. Sistema genera factura automáticamente
   ↓ (MongoDB: invoices)
6. Sistema debita cuenta corriente
   ↓ (MongoDB: accounts, account_movements)
```

### Flujo 2: Mensajería

```
1. Usuario A envía mensaje a Usuario B (por email)
   ↓ (MongoDB: busca ID de Usuario B)
2. Sistema genera ID de conversación
   ↓ (hash consistente de ambos IDs)
3. Mensaje se almacena
   ↓ (Cassandra: messages_by_conversation)
4. Se actualiza conversación para ambos usuarios
   ↓ (Cassandra: conversations_by_user)
```

### Flujo 3: Registro de Medición

```
1. Operador registra medición
   ↓ (Verifica permisos en Neo4j)
2. Medición se inserta en 4 tablas simultáneamente
   ↓ (Cassandra: measurements_by_sensor_day)
   ↓ (Cassandra: measurements_by_city_day)
   ↓ (Cassandra: measurements_by_country_day)
   ↓ (Cassandra: last_measurement_by_sensor)
3. Si excede umbral, genera alerta
   ↓ (MongoDB: alerts + Cassandra: alerts_by_sensor)
```

---

## 🔐 Sistema de Permisos (RBAC)

### Roles Implementados

| Rol | Permisos | Descripción |
|-----|----------|-------------|
| **Administrador** | TODOS | Acceso completo al sistema |
| **Operador** | pt_maxmin, pt_prom | Puede registrar mediciones y generar reportes |
| **Analista** | pt_prom | Solo lectura y reportes básicos |
| **Técnico** | pt_maxmin, pt_alerts | Mantenimiento y alertas |

### Verificación de Permisos

El sistema verifica permisos en **3 niveles**:

1. **Menú Principal**: Solo muestra opciones permitidas
2. **Submenús**: Valida acceso antes de mostrar
3. **Operaciones**: Verifica en Neo4j antes de ejecutar

**Ejemplo**:
```java
// Verificar si puede ejecutar un proceso
if (!authorizationDAO.canUserExecuteProcess(userId, "pt_maxmin")) {
    return false; // No tiene permisos
}
```

---

## 📝 Datos de Prueba

### Usuarios

| Email | Contraseña | Rol | Opciones Menú |
|-------|------------|-----|---------------|
| admin@admin.com | admin | Admin | 7 |
| maria.gonzalez@uade.edu.ar | password123 | Operador | 6 |
| carlos.rodriguez@uade.edu.ar | password123 | Analista | 4 |
| ana.martinez@uade.edu.ar | password123 | Técnico | 5 |

### Sensores (IDs Fijos)

```
Buenos Aires:  550e8400-e29b-41d4-a716-446655440001
Córdoba:       550e8400-e29b-41d4-a716-446655440003
Rosario:       550e8400-e29b-41d4-a716-446655440005
```

### Ciudades Disponibles

- Buenos Aires (Argentina) - 3 sensores
- Córdoba (Argentina) - 3 sensores
- Rosario (Argentina) - 2 sensores
- Mendoza (Argentina) - 1 sensor
- La Plata (Argentina) - 1 sensor

---

## 🛠️ Compilación y Desarrollo

### Compilar Manualmente

```bash
mvn clean package -DskipTests
```

### Limpiar Bases de Datos

```bash
./clean-databases.sh
```

### Reiniciar Todo Desde Cero

```bash
./clean-databases.sh
./start.sh
```

---

## 📊 Características Técnicas

### Pools de Conexiones Optimizados

- **MongoDB**: Pool de 5-20 conexiones
- **Cassandra**: 2 conexiones core por nodo
- **Neo4j**: Pool de 10 conexiones

### Migraciones Automáticas

Al iniciar la aplicación, se ejecutan automáticamente:
- Creación de colecciones e índices en MongoDB
- Creación de keyspace y tablas en Cassandra
- Creación de constraints y nodos iniciales en Neo4j

### Seeding Automático

Si no hay datos, se pueblan automáticamente:
- 11 usuarios con roles asignados
- 10 sensores en 5 ciudades
- 700+ mediciones de prueba (últimos 7 días)
- 4 roles y 6 tipos de procesos

---

## 🎓 Para la Presentación en Clase

### Script de Demo (3 minutos)

**Minuto 1**: Arquitectura Políglota
- Mostrar login → MongoDB autentica
- Mostrar permisos → Neo4j proporciona
- Explicar: Cada BD hace lo que mejor sabe

**Minuto 2**: Flujo Completo de Proceso
- Solicitar reporte → MongoDB
- Ejecutar → Cassandra (mediciones)
- Ver resultado → MongoDB
- Factura automática → MongoDB

**Minuto 3**: Mensajería y Permisos
- Enviar mensaje → Cassandra
- Cambiar usuario → Mostrar menú diferente
- Explicar: RBAC con Neo4j

### Queries para Mostrar

**MongoDB** - Ver usuarios:
```javascript
docker exec -it mongodb-tp-bbdd mongosh -u admin -p admin123
use tp_sensores
db.users.find({}, {fullName:1, email:1, department:1}).pretty()
```

**Cassandra** - Ver mediciones:
```sql
docker exec -it cassandra-tp-bbdd cqlsh
USE tp_sensores;
SELECT * FROM last_measurement_by_sensor LIMIT 5;
```

**Neo4j** - Ver grafo de permisos:
```cypher
http://localhost:7474
MATCH (u:User {email: 'admin@admin.com'})-[:HAS_ROLE]->(r:Role)-[:CAN_EXECUTE]->(p:ProcessType)
RETURN u, r, p
```

---

## ⚠️ Troubleshooting

### Problema: Admin sin permisos

**Síntoma**: Login exitoso pero roles y permisos vacíos

**Solución**: Ejecutar en Neo4j Browser (http://localhost:7474):

```cypher
MATCH (u:User {email: 'admin@admin.com'})
MATCH (r:Role), (p:ProcessType), (g:Group)
MERGE (u)-[:HAS_ROLE]->(r)
MERGE (u)-[:CAN_EXECUTE]->(p)
MERGE (u)-[:MEMBER_OF]->(g)
RETURN 'Permisos asignados' AS resultado;
```

### Problema: Cassandra timeout

**Solución**: Cassandra tarda 2-3 minutos en iniciar completamente

```bash
docker restart cassandra-tp-bbdd
sleep 180
java -jar target/tp-bbdd-2-app-1.0.0.jar
```

### Problema: No hay datos

**Solución**: El seeding solo se ejecuta si MongoDB está vacío

```bash
./clean-databases.sh
./start-databases.sh
sleep 180
java -jar target/tp-bbdd-2-app-1.0.0.jar
```

---

## 📚 Documentación Adicional

- **`CHEAT_SHEET_DEMO.txt`** - Datos para copiar/pegar durante demo
- **`AUTENTICACION.md`** - Sistema de autenticación y roles
- **`ESTRUCTURA_BASES_DE_DATOS.md`** - Detalle de cada tabla/colección

---

## ✅ Cumplimiento de Consigna

| Requisito | Estado |
|-----------|--------|
| Persistencia políglota (3 BDs) | ✅ MongoDB, Cassandra, Neo4j |
| Gestión de usuarios y roles | ✅ RBAC completo en Neo4j |
| Registro de sensores y mediciones | ✅ Con ubicación geoespacial |
| Sistema de procesos y reportes | ✅ Solicitud, ejecución, resultados |
| Facturación y cuenta corriente | ✅ Automática con débito |
| Mensajería entre usuarios | ✅ En Cassandra |
| Control de permisos diferenciados | ✅ Menú dinámico |
| Reportes Max/Min | ✅ Por ciudad/fecha |
| Reportes de Promedios | ✅ Por ciudad/fecha |
| Alertas en rangos | ✅ Sistema de alertas |

**Cumplimiento**: 100% ✅

---

## 👨‍💻 Autor

**Materia**: Ingeniería de Datos II  
**Universidad**: UADE  
**Año**: 2025

---

## 🚀 Quick Start

```bash
cd "/Users/apinango/Desktop/Personal/WorkSpaceUADE/ING. DE DATOS 2/tp-bbdd-2-app/tp-bbdd-2-app"
./start.sh
```

**Login recomendado**: `maria.gonzalez@uade.edu.ar` / `password123`

**¡Listo para demostrar!** 🎓✨
