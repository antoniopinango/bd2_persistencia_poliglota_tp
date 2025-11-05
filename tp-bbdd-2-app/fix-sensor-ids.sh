#!/bin/bash

echo "🔧 Limpiando sensores con IDs no válidos de Neo4j..."
echo ""

# Esperar a que Neo4j esté listo
sleep 5

# Eliminar el sensor de prueba con ID inválido
docker exec neo4j-tp-bbdd cypher-shell -u neo4j -p neo4j123 "
// Eliminar sensor con ID no-UUID
MATCH (s:Sensor {id: 'sensor_test_001'})
DETACH DELETE s;

// Verificar sensores restantes
MATCH (s:Sensor)
RETURN count(s) AS total_sensores;
" 2>&1 | grep -v "^$"

echo ""
echo "✅ Sensores con IDs inválidos eliminados"
echo "💡 Los sensores válidos están sincronizados desde MongoDB"
echo ""

