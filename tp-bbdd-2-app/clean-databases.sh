#!/bin/bash

# Script para limpiar las bases de datos
# Ejecutar este script cuando necesites resetear los datos

echo "🧹 Limpiando bases de datos..."
echo ""

# Limpiar MongoDB
echo "📄 Limpiando MongoDB..."
mongosh --quiet --eval "use tp_sensores; db.dropDatabase();" 2>/dev/null
if [ $? -eq 0 ]; then
    echo "✅ MongoDB limpiado"
else
    echo "⚠️  No se pudo limpiar MongoDB (puede que no esté corriendo o mongosh no esté instalado)"
fi

# Limpiar Cassandra
echo ""
echo "🔗 Limpiando Cassandra..."
docker exec tp-cassandra cqlsh -e "DROP KEYSPACE IF EXISTS tp_sensores;" 2>/dev/null
if [ $? -eq 0 ]; then
    echo "✅ Cassandra limpiado"
else
    echo "⚠️  No se pudo limpiar Cassandra (verifica que el contenedor esté corriendo)"
fi

# Limpiar Neo4j
echo ""
echo "🌐 Limpiando Neo4j..."
docker exec tp-neo4j cypher-shell -u neo4j -p password123 "MATCH (n) DETACH DELETE n;" 2>/dev/null
if [ $? -eq 0 ]; then
    echo "✅ Neo4j limpiado"
else
    echo "⚠️  No se pudo limpiar Neo4j (verifica que el contenedor esté corriendo)"
fi

echo ""
echo "🎉 Limpieza completada!"
echo ""
echo "Ahora ejecuta la aplicación y las migraciones recrearán todo:"
echo "  java -jar target/tp-bbdd-2-app-1.0.0.jar"
echo ""

