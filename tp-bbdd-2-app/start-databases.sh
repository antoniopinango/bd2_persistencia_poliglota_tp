#!/bin/bash

# Script para iniciar todas las bases de datos necesarias para el proyecto
# TP BBDD 2 - Persistencia Políglota

echo "=================================================="
echo "  Iniciando Bases de Datos - TP BBDD 2"
echo "=================================================="
echo

# Verificar que Docker esté corriendo
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker no está corriendo. Iniciando Colima..."
    colima start
    echo "✅ Colima iniciado"
fi

echo "🔄 Verificando estado de las bases de datos..."
echo

# Neo4j
if docker ps | grep -q neo4j-tp-bbdd; then
    echo "✅ Neo4j ya está corriendo"
else
    if docker ps -a | grep -q neo4j-tp-bbdd; then
        echo "🔄 Reiniciando Neo4j..."
        docker start neo4j-tp-bbdd
    else
        echo "🔄 Creando contenedor de Neo4j..."
        docker run -d --name neo4j-tp-bbdd \
            -p 7474:7474 -p 7687:7687 \
            -e NEO4J_AUTH=neo4j/password \
            neo4j:latest
    fi
    echo "✅ Neo4j iniciado"
fi

# Cassandra
if docker ps | grep -q cassandra-tp-bbdd; then
    echo "✅ Cassandra ya está corriendo"
else
    if docker ps -a | grep -q cassandra-tp-bbdd; then
        echo "🔄 Reiniciando Cassandra..."
        docker start cassandra-tp-bbdd
    else
        echo "🔄 Creando contenedor de Cassandra..."
        docker run -d --name cassandra-tp-bbdd \
            -p 9042:9042 \
            -e MAX_HEAP_SIZE=512M \
            -e HEAP_NEWSIZE=128M \
            cassandra:latest
    fi
    echo "✅ Cassandra iniciado"
fi

# MongoDB (asumiendo que está instalado localmente)
if pgrep -x "mongod" > /dev/null; then
    echo "✅ MongoDB ya está corriendo"
else
    echo "⚠️  MongoDB no está corriendo"
    echo "   Para iniciar MongoDB:"
    echo "   - macOS: brew services start mongodb-community"
    echo "   - Linux: sudo systemctl start mongod"
fi

echo
echo "⏳ Esperando a que las bases de datos estén listas..."
sleep 15

echo
echo "=================================================="
echo "  Estado de las Bases de Datos"
echo "=================================================="
echo

# Verificar puertos
echo "Verificando conectividad:"
if nc -z localhost 7687 2>/dev/null; then
    echo "✅ Neo4j (puerto 7687) - Disponible"
else
    echo "❌ Neo4j (puerto 7687) - No disponible"
fi

if nc -z localhost 9042 2>/dev/null; then
    echo "✅ Cassandra (puerto 9042) - Disponible"
else
    echo "❌ Cassandra (puerto 9042) - No disponible"
fi

if nc -z localhost 27017 2>/dev/null; then
    echo "✅ MongoDB (puerto 27017) - Disponible"
else
    echo "❌ MongoDB (puerto 27017) - No disponible"
fi

echo
echo "=================================================="
echo "  Bases de datos listas!"
echo "=================================================="
echo
echo "Para ejecutar la aplicación:"
echo "  mvn exec:java -Dexec.mainClass=\"com.bd2.app.Application\""
echo
echo "Para detener las bases de datos:"
echo "  docker stop neo4j-tp-bbdd cassandra-tp-bbdd"
echo

