# 🔍 Queries Útiles - TP BBDD 2

## 👥 Usuarios con Rol Técnico

### Neo4j (Relaciones)

```cypher
// Ver usuarios con rol técnico
MATCH (u:User)-[:HAS_ROLE]->(r:Role {name: 'tecnico'})
RETURN u.id AS userId, 
       u.email AS email, 
       u.fullName AS nombre,
       u.department AS departamento
ORDER BY u.fullName
```

### MongoDB (Datos)

```javascript
use tp_sensores

// Ver todos los técnicos
db.users.find(
    { roleId: "role_technician" },
    { _id: 1, fullName: 1, email: 1, department: 1 }
).pretty()

// Solo IDs y emails
db.users.find(
    { roleId: "role_technician" }
).forEach(u => print(u._id + " | " + u.email))
```

### Terminal (One-liner)

```bash
# MongoDB
docker exec mongodb-tp-bbdd mongosh -u admin -p admin123 --authenticationDatabase admin --quiet --eval "
use tp_sensores;
db.users.find({roleId: 'role_technician'}, {_id:1, email:1}).forEach(u => print(u._id + ' | ' + u.email))
"

# Neo4j  
docker exec neo4j-tp-bbdd cypher-shell -u neo4j -p neo4j123 "
MATCH (u:User)-[:HAS_ROLE]->(r:Role {name: 'tecnico'})
RETURN u.id, u.email
"
```

---

## 📡 Sensores

### Ver Todos los Sensores (MongoDB)

```javascript
use tp_sensores

// Ver todos
db.sensors.find().pretty()

// Solo IDs y nombres
db.sensors.find({}, {_id: 1, name: 1, "location.city": 1, type: 1})

// Por ciudad
db.sensors.find({"location.city": "Buenos Aires"})

// Por tipo
db.sensors.find({type: "temperature"})
db.sensors.find({type: "humidity"})
```

### Sensores en Neo4j

```cypher
// Todos los sensores con ubicación
MATCH (s:Sensor)-[:IN_CITY]->(c:City)-[:IN_COUNTRY]->(co:Country)
RETURN s.id, s.code, s.type, c.name AS ciudad, co.name AS pais
ORDER BY c.name, s.code

// Por ciudad
MATCH (s:Sensor)-[:IN_CITY]->(c:City {name: 'Buenos Aires'})
RETURN s.id, s.code, s.type
```

---

## 👤 Todos los Usuarios por Rol

### MongoDB

```javascript
// Administradores
db.users.find({roleId: "role_admin"}, {fullName: 1, email: 1})

// Operadores
db.users.find({roleId: "role_operator"}, {fullName: 1, email: 1})

// Analistas
db.users.find({roleId: "role_analyst"}, {fullName: 1, email: 1})

// Técnicos
db.users.find({roleId: "role_technician"}, {fullName: 1, email: 1})

// Contar por rol
db.users.aggregate([
    {$group: {_id: "$roleId", total: {$sum: 1}}}
])
```

### Neo4j

```cypher
// Ver todos los usuarios con sus roles
MATCH (u:User)-[:HAS_ROLE]->(r:Role)
RETURN u.email, u.fullName, collect(r.name) AS roles
ORDER BY u.fullName

// Contar usuarios por rol
MATCH (u:User)-[:HAS_ROLE]->(r:Role)
RETURN r.name AS rol, count(u) AS total
ORDER BY total DESC
```

---

## 📊 Mediciones

### Cassandra

```sql
USE tp_sensores;

-- Últimas mediciones
SELECT sensor_id, city, temperature, humidity, ts 
FROM last_measurement_by_sensor 
LIMIT 20;

-- Mediciones de hoy por ciudad
SELECT * FROM measurements_by_city_day
WHERE city = 'Buenos Aires' 
  AND day = '2025-11-03'
LIMIT 10;

-- Contar mediciones por sensor
SELECT sensor_id, COUNT(*) as total
FROM measurements_by_sensor_day
WHERE day = '2025-11-03'
GROUP BY sensor_id;
```

---

## 💰 Facturas y Procesos

### MongoDB

```javascript
// Ver todas las facturas
db.invoices.find().pretty()

// Facturas pendientes
db.invoices.find({status: "pendiente"})

// Facturas por usuario
db.invoices.find({userId: "USER_ID_AQUI"})

// Procesos solicitados
db.process_requests.find().sort({requestedAt: -1}).limit(10)

// Procesos completados
db.process_requests.find({status: "completado"})

// Procesos pendientes
db.process_requests.find({status: "pendiente"})
```

---

## 💬 Mensajes

### Cassandra

```sql
-- Ver conversaciones recientes
SELECT user_id, conversation_id, last_message_snippet
FROM conversations_by_user
LIMIT 20;

-- Mensajes de una conversación específica
SELECT ts, sender_id, content
FROM messages_by_conversation
WHERE conversation_id = UUID_AQUI
LIMIT 50;
```

---

## 🔐 Permisos y Roles

### Neo4j

```cypher
// Ver estructura completa de permisos
MATCH (u:User)-[:HAS_ROLE]->(r:Role)-[:CAN_EXECUTE]->(p:ProcessType)
RETURN u.email, r.name AS rol, collect(p.id) AS permisos
ORDER BY u.email

// Usuarios sin roles asignados
MATCH (u:User)
WHERE NOT (u)-[:HAS_ROLE]->(:Role)
RETURN u.id, u.email, u.fullName

// Verificar permisos del admin
MATCH (u:User {email: 'admin@admin.com'})
OPTIONAL MATCH (u)-[:HAS_ROLE]->(r:Role)
OPTIONAL MATCH (u)-[:CAN_EXECUTE]->(p:ProcessType)
RETURN u.email,
       collect(DISTINCT r.name) AS roles,
       collect(DISTINCT p.id) AS permisos_directos
```

---

## 🏙️ Datos Geográficos

### Neo4j

```cypher
// Jerarquía completa
MATCH (s:Sensor)-[:IN_CITY]->(c:City)-[:IN_COUNTRY]->(co:Country)
RETURN co.name AS pais, 
       c.name AS ciudad, 
       count(s) AS sensores
ORDER BY pais, ciudad

// Ciudades sin sensores
MATCH (c:City)
WHERE NOT (c)<-[:IN_CITY]-(:Sensor)
RETURN c.name

// Técnicos por ciudad
MATCH (u:User)-[:COVERS_CITY]->(c:City)
RETURN c.name AS ciudad, collect(u.email) AS tecnicos
```

---

## 📈 Estadísticas Rápidas

### MongoDB

```javascript
// Totales generales
print("Usuarios: " + db.users.countDocuments())
print("Sensores: " + db.sensors.countDocuments())
print("Procesos solicitados: " + db.process_requests.countDocuments())
print("Facturas: " + db.invoices.countDocuments())

// Usuarios por estado
db.users.aggregate([
    {$group: {_id: "$status", total: {$sum: 1}}}
])

// Sensores por ciudad
db.sensors.aggregate([
    {$group: {_id: "$location.city", total: {$sum: 1}}}
])
```

### Cassandra

```sql
-- Total de mediciones del día
SELECT COUNT(*) FROM measurements_by_city_day
WHERE day = '2025-11-03';
```

### Neo4j

```cypher
// Resumen completo
MATCH (u:User) WITH count(u) AS totalUsers
MATCH (s:Sensor) WITH totalUsers, count(s) AS totalSensors
MATCH (c:City) WITH totalUsers, totalSensors, count(c) AS totalCities
RETURN {
    usuarios: totalUsers,
    sensores: totalSensors,
    ciudades: totalCities
} AS resumen
```

---

## ⚡ Queries Más Usadas para la Demo

### 1. Ver Admin
```cypher
MATCH (u:User {email: 'admin@admin.com'})
RETURN u
```

### 2. Ver Técnicos
```javascript
db.users.find({roleId: "role_technician"}, {_id: 1, email: 1})
```

### 3. Ver Sensores
```javascript
db.sensors.find({}, {_id: 1, name: 1, type: 1, "location.city": 1})
```

### 4. Ver Última Medición
```sql
SELECT * FROM last_measurement_by_sensor LIMIT 5;
```

---

**Fecha**: 3 Noviembre 2025  
**Versión**: 2.0.0

