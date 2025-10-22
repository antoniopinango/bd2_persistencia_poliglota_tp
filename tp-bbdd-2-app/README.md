# TP BBDD 2 - Aplicación de Persistencia Políglota

## Descripción

Aplicación Java que demuestra el uso de persistencia políglota integrando **MongoDB**, **Cassandra** y **Neo4j** con pools de conexiones optimizados. Cada base de datos se especializa en un dominio específico del sistema de sensores universitario.

## Arquitectura de Datos

### 🗄️ Distribución por Base de Datos

| Base de Datos | Propósito | Datos Almacenados |
|---------------|-----------|-------------------|
| **MongoDB** | Dominio Transaccional | Usuarios, Sensores, Facturas, Pagos, Cuentas |
| **Cassandra** | Series Temporales | Mediciones, Agregados, Logs, Mensajería |
| **Neo4j** | RBAC y Geografía | Permisos, Roles, Grupos, Ubicaciones |

### 🔄 Integración Multi-Base

- **IDs Consistentes**: UUIDs compartidos entre sistemas
- **Sincronización**: Cambios en MongoDB se propagan a Neo4j
- **Autorización**: Neo4j valida permisos antes de operaciones
- **Denormalización**: Cassandra optimiza consultas por patrón de uso

## Estructura del Proyecto

```
src/main/java/com/bd2/app/
├── config/
│   └── DatabaseConfig.java          # Configuración centralizada
├── database/
│   ├── MongoConnectionManager.java  # Pool de conexiones MongoDB
│   ├── CassandraConnectionManager.java # Pool de conexiones Cassandra
│   └── Neo4jConnectionManager.java  # Pool de conexiones Neo4j
├── model/
│   ├── User.java                    # Modelo de Usuario
│   ├── Sensor.java                  # Modelo de Sensor
│   └── Measurement.java             # Modelo de Medición
├── dao/
│   ├── UserDAO.java                 # DAO para MongoDB
│   ├── MeasurementDAO.java          # DAO para Cassandra
│   └── AuthorizationDAO.java        # DAO para Neo4j
├── service/
│   ├── UserService.java             # Servicio integrado de usuarios
│   └── SensorService.java           # Servicio integrado de sensores
└── Application.java                 # Aplicación principal
```

## 🚀 Inicio Rápido

### Opción 1: Script Automático (Recomendado)

```bash
# 1. Iniciar todas las bases de datos
./start-databases.sh

# 2. Ejecutar la aplicación (crea automáticamente toda la estructura)
mvn exec:java -Dexec.mainClass="com.bd2.app.Application"
```

¡Eso es todo! La aplicación:
- ✅ Se conecta a las 3 bases de datos
- ✅ Crea automáticamente keyspaces, tablas, colecciones e índices
- ✅ Inserta datos de ejemplo
- ✅ Muestra el menú interactivo

### Opción 2: Inicio Manual

```bash
# 1. Iniciar Docker/Colima
colima start  # En macOS

# 2. Iniciar Neo4j
docker run -d --name neo4j-tp-bbdd -p 7474:7474 -p 7687:7687 \
  -e NEO4J_AUTH=neo4j/password neo4j:latest

# 3. Iniciar Cassandra
docker run -d --name cassandra-tp-bbdd -p 9042:9042 \
  -e MAX_HEAP_SIZE=512M -e HEAP_NEWSIZE=128M cassandra:latest

# 4. Iniciar MongoDB (si no está corriendo)
brew services start mongodb-community  # macOS

# 5. Esperar ~30 segundos para que Cassandra esté listo

# 6. Ejecutar la aplicación
mvn exec:java -Dexec.mainClass="com.bd2.app.Application"
```

## Prerrequisitos

### Software Requerido

- **Java 17** o superior
- **Maven 3.8+**
- **MongoDB 6.0+**
- **Apache Cassandra 4.0+**
- **Neo4j 5.0+**

### Bases de Datos

1. **MongoDB** ejecutándose en `localhost:27017`
2. **Cassandra** ejecutándose en `localhost:9042`
3. **Neo4j** ejecutándose en `localhost:7687`

## Instalación y Configuración

### 1. Clonar y Compilar

```bash
cd tp-bbdd-2-app
mvn clean compile
```

### 2. Configurar Bases de Datos

Editar `src/main/resources/application.properties`:

```properties
# MongoDB
mongodb.host=localhost
mongodb.port=27017
mongodb.database=tp_sensores

# Cassandra
cassandra.host=localhost
cassandra.port=9042
cassandra.keyspace=tp_sensores

# Neo4j
neo4j.uri=bolt://localhost:7687
neo4j.username=neo4j
neo4j.password=tu_password
```

### 3. Inicializar Esquemas

**MongoDB:**
```bash
cd ../mongodb
mongosh --file init-database.js
mongosh --file test-data.js
```

**Cassandra:**
```bash
cd ../cassandra
cqlsh -f init-keyspace.cql
cqlsh -f create-aggregation-tables.cql
cqlsh -f create-messaging-tables.cql
cqlsh -f create-alert-tables.cql
cqlsh -f create-execution-log.cql
cqlsh -f insert-test-data.cql
```

**Neo4j:**
```bash
cd ../neo4j
cypher-shell -f init-constraints.cypher
cypher-shell -f create-initial-nodes.cypher
cypher-shell -f create-test-users.cypher
```

## Ejecución

### Ejecutar la Aplicación

```bash
# Opción 1: Con Maven
mvn exec:java -Dexec.mainClass="com.bd2.app.Application"

# Opción 2: Compilar JAR y ejecutar
mvn clean package
java -jar target/tp-bbdd-2-app-1.0.0.jar
```

### Menú Interactivo

La aplicación presenta un menú interactivo con las siguientes opciones:

1. **👤 Gestión de Usuarios**
   - Registrar nuevo usuario
   - Autenticar usuario
   - Ver perfil de usuario
   - Listar usuarios por departamento

2. **📊 Gestión de Sensores y Mediciones**
   - Registrar medición
   - Ver últimas mediciones de sensor
   - Ver mediciones por ciudad
   - Ver estado actual de sensores
   - Asignar técnico a ciudad

3. **🔐 Consultas de Autorización**
   - Ver permisos de usuario
   - Verificar permiso específico
   - Ver miembros de grupo
   - Ver técnicos por ciudad

4. **📈 Dashboard y Estadísticas**
   - Estadísticas del sistema
   - Estado de conexiones
   - Métricas de rendimiento

5. **🧪 Ejecutar Demos**
   - Demos automáticos de funcionalidad

## Características Técnicas

### 🔗 Pools de Conexiones

**MongoDB:**
- Pool mínimo: 5 conexiones
- Pool máximo: 20 conexiones
- Timeout de conexión: 30 segundos
- TTL de conexión: 5 minutos

**Cassandra:**
- Conexiones core: 2 por nodo
- Conexiones máximas: 8 por nodo
- Requests por conexión: 1024
- Heartbeat: 30 segundos

**Neo4j:**
- Pool máximo: 50 conexiones
- Timeout de adquisición: 60 segundos
- Timeout de conexión: 30 segundos
- Retry de transacciones: 30 segundos

### 🛡️ Manejo de Errores

- **Reconexión automática** en caso de pérdida de conexión
- **Reintentos configurables** para operaciones fallidas
- **Rollback automático** en transacciones multi-base
- **Logging detallado** para debugging

### 🔄 Patrones de Integración

**Registro de Usuario:**
1. Crear en MongoDB (datos maestros)
2. Sincronizar en Neo4j (relaciones)
3. Asignar rol por defecto

**Registro de Medición:**
1. Verificar permisos en Neo4j
2. Insertar en Cassandra (denormalizado)
3. Actualizar última medición

**Consulta de Datos:**
1. Autenticar en MongoDB
2. Verificar permisos en Neo4j
3. Consultar datos en Cassandra

## Ejemplos de Uso

### Registro de Usuario

```java
UserService userService = new UserService();
String userId = userService.registerUser(
    "Juan Pérez", 
    "juan.perez@universidad.edu", 
    "password123", 
    "Investigación"
);
```

### Autenticación

```java
Map<String, Object> authResult = userService.authenticateUser(
    "juan.perez@universidad.edu", 
    "password123"
);
Set<String> permissions = (Set<String>) authResult.get("permissions");
```

### Registro de Medición

```java
SensorService sensorService = new SensorService();
Measurement measurement = Measurement.createTemperatureMeasurement(
    "sensor-uuid", 
    23.5, 
    "Buenos Aires", 
    "Argentina"
);
boolean success = sensorService.recordMeasurement(userId, measurement);
```

### Consulta de Mediciones

```java
List<Measurement> measurements = sensorService.getLatestMeasurements(
    userId, 
    "sensor-uuid", 
    10
);
```

## Monitoreo y Debugging

### Logs

Los logs se guardan en:
- **Consola**: Nivel INFO
- **Archivo**: `logs/tp-bbdd-2-app.log` (Nivel DEBUG)

### Métricas de Conexión

```java
// Ver estado de pools
MongoConnectionManager.getInstance().logConnectionPoolStats();
CassandraConnectionManager.getInstance().logSessionStats();
Neo4jConnectionManager.getInstance().logConnectionPoolStats();
```

### Verificar Conectividad

```java
// Verificar conexiones
boolean mongoOk = MongoConnectionManager.getInstance().isConnected();
boolean cassandraOk = CassandraConnectionManager.getInstance().isConnected();
boolean neo4jOk = Neo4jConnectionManager.getInstance().isConnected();
```

## Troubleshooting

### Problemas Comunes

1. **Error de conexión MongoDB**
   ```
   Verificar que MongoDB esté ejecutándose:
   brew services start mongodb/brew/mongodb-community
   ```

2. **Error de conexión Cassandra**
   ```
   Verificar que Cassandra esté ejecutándose:
   brew services start cassandra
   ```

3. **Error de conexión Neo4j**
   ```
   Verificar credenciales en application.properties
   Iniciar Neo4j Desktop o servicio
   ```

4. **OutOfMemoryError**
   ```
   Aumentar heap size:
   java -Xmx2g -jar tp-bbdd-2-app-1.0.0.jar
   ```

### Comandos de Diagnóstico

```bash
# Verificar puertos
netstat -an | grep -E "(27017|9042|7687)"

# Verificar logs
tail -f logs/tp-bbdd-2-app.log

# Verificar conexiones Java
jps -l
jstack <pid>
```

## Desarrollo

### Agregar Nueva Funcionalidad

1. **Modelo**: Crear clase en `model/`
2. **DAO**: Implementar acceso a datos en `dao/`
3. **Servicio**: Crear lógica de negocio en `service/`
4. **Integrar**: Agregar al menú en `Application.java`

### Testing

```bash
# Ejecutar tests
mvn test

# Ejecutar con perfil de test
mvn test -Dspring.profiles.active=test
```

## Licencia

Este proyecto es para uso académico en el contexto universitario.

## Contacto

Para preguntas sobre la implementación, consultar la documentación de cada base de datos en sus respectivos directorios:
- `../mongodb/README.md`
- `../cassandra/README.md`
- `../neo4j/README.md`
