#!/bin/bash

# Script de ejecución para TP BBDD 2 - Persistencia Políglota
# Ejecutar con: chmod +x run.sh && ./run.sh

echo "=================================================="
echo "  TP BBDD 2 - Persistencia Políglota"
echo "  Aplicación Java con MongoDB, Cassandra y Neo4j"
echo "=================================================="
echo

# Verificar que Java esté instalado
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java no está instalado"
    echo "   Instalar Java 17 o superior"
    exit 1
fi

# Verificar versión de Java
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "❌ Error: Se requiere Java 17 o superior"
    echo "   Versión actual: $(java -version 2>&1 | head -n 1)"
    exit 1
fi

echo "✅ Java $(java -version 2>&1 | head -n 1 | cut -d'"' -f2) encontrado"

# Verificar que Maven esté instalado
if ! command -v mvn &> /dev/null; then
    echo "❌ Error: Maven no está instalado"
    echo "   Instalar Apache Maven 3.8 o superior"
    exit 1
fi

echo "✅ Maven $(mvn -version | head -n 1 | cut -d' ' -f3) encontrado"

# Función para verificar conectividad de bases de datos
check_databases() {
    echo
    echo "🔍 Verificando conectividad de bases de datos..."
    
    # MongoDB
    if nc -z localhost 27017 2>/dev/null; then
        echo "✅ MongoDB (puerto 27017) - Disponible"
    else
        echo "⚠️  MongoDB (puerto 27017) - No disponible"
        echo "   Iniciar con: brew services start mongodb/brew/mongodb-community"
    fi
    
    # Cassandra
    if nc -z localhost 9042 2>/dev/null; then
        echo "✅ Cassandra (puerto 9042) - Disponible"
    else
        echo "⚠️  Cassandra (puerto 9042) - No disponible"
        echo "   Iniciar con: brew services start cassandra"
    fi
    
    # Neo4j
    if nc -z localhost 7687 2>/dev/null; then
        echo "✅ Neo4j (puerto 7687) - Disponible"
    else
        echo "⚠️  Neo4j (puerto 7687) - No disponible"
        echo "   Iniciar Neo4j Desktop o servicio"
    fi
}

# Función para compilar el proyecto
compile_project() {
    echo
    echo "🔨 Compilando proyecto..."
    
    if mvn clean compile -q; then
        echo "✅ Compilación exitosa"
        return 0
    else
        echo "❌ Error en compilación"
        return 1
    fi
}

# Función para ejecutar la aplicación
run_application() {
    echo
    echo "🚀 Iniciando aplicación..."
    echo "   (Presiona Ctrl+C para salir)"
    echo
    
    # Crear directorio de logs si no existe
    mkdir -p logs
    
    # Ejecutar aplicación
    mvn exec:java -Dexec.mainClass="com.bd2.app.Application" -q
}

# Función para crear JAR ejecutable
create_jar() {
    echo
    echo "📦 Creando JAR ejecutable..."
    
    if mvn clean package -q; then
        echo "✅ JAR creado: target/tp-bbdd-2-app-1.0.0.jar"
        echo
        echo "Para ejecutar el JAR:"
        echo "java -jar target/tp-bbdd-2-app-1.0.0.jar"
        return 0
    else
        echo "❌ Error creando JAR"
        return 1
    fi
}

# Menú principal
show_menu() {
    echo
    echo "Selecciona una opción:"
    echo "1) 🔍 Verificar conectividad de bases de datos"
    echo "2) 🔨 Compilar proyecto"
    echo "3) 🚀 Ejecutar aplicación (Maven)"
    echo "4) 📦 Crear JAR ejecutable"
    echo "5) 🏃 Compilar y ejecutar (todo en uno)"
    echo "6) 📋 Ver información del sistema"
    echo "7) 🧹 Limpiar proyecto"
    echo "0) 🚪 Salir"
    echo
}

# Función para mostrar información del sistema
show_system_info() {
    echo
    echo "📋 === INFORMACIÓN DEL SISTEMA ==="
    echo "Java: $(java -version 2>&1 | head -n 1)"
    echo "Maven: $(mvn -version | head -n 1)"
    echo "OS: $(uname -s) $(uname -r)"
    echo "Directorio: $(pwd)"
    echo
    echo "📁 Estructura del proyecto:"
    find src -name "*.java" | head -10 | sed 's/^/   /'
    if [ $(find src -name "*.java" | wc -l) -gt 10 ]; then
        echo "   ... y $(($(find src -name "*.java" | wc -l) - 10)) archivos más"
    fi
    echo
    echo "📊 Estadísticas:"
    echo "   Archivos Java: $(find src -name "*.java" | wc -l)"
    echo "   Líneas de código: $(find src -name "*.java" -exec wc -l {} + | tail -n 1 | awk '{print $1}')"
}

# Función para limpiar proyecto
clean_project() {
    echo
    echo "🧹 Limpiando proyecto..."
    
    mvn clean -q
    rm -rf logs/*.log
    
    echo "✅ Proyecto limpiado"
}

# Loop principal del menú
while true; do
    show_menu
    read -p "Ingresa tu opción (0-7): " option
    
    case $option in
        1)
            check_databases
            ;;
        2)
            compile_project
            ;;
        3)
            if compile_project; then
                run_application
            fi
            ;;
        4)
            create_jar
            ;;
        5)
            echo
            echo "🏃 === COMPILAR Y EJECUTAR ==="
            check_databases
            if compile_project; then
                run_application
            fi
            ;;
        6)
            show_system_info
            ;;
        7)
            clean_project
            ;;
        0)
            echo
            echo "👋 ¡Hasta luego!"
            exit 0
            ;;
        *)
            echo "❌ Opción inválida. Usa 0-7."
            ;;
    esac
    
    # Pausa antes de mostrar el menú de nuevo
    echo
    read -p "Presiona Enter para continuar..."
done
