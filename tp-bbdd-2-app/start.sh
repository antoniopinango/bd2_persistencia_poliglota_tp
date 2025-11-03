#!/bin/bash

# Script simple para iniciar todo

# Iniciar bases de datos
docker start mongodb-tp-bbdd cassandra-tp-bbdd neo4j-tp-bbdd 2>/dev/null

# Esperar que estén listas
sleep 120

# Compilar
mvn clean package -DskipTests -q

# Ejecutar aplicación
java -jar target/tp-bbdd-2-app-1.0.0.jar

