package com.bd2.app;

import com.bd2.app.config.DatabaseConfig;
import com.bd2.app.database.CassandraConnectionManager;
import com.bd2.app.database.MongoConnectionManager;
import com.bd2.app.database.Neo4jConnectionManager;
import com.bd2.app.migrations.MigrationRunner;
import com.bd2.app.model.Measurement;
import com.bd2.app.model.User;
import com.bd2.app.service.SensorService;
import com.bd2.app.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Aplicación principal del sistema de persistencia políglota
 * Demuestra el uso integrado de MongoDB, Cassandra y Neo4j
 */
public class Application {
    
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    
    private UserService userService;
    private SensorService sensorService;
    private final Scanner scanner;
    
    public Application() {
        this.scanner = new Scanner(System.in);
    }
    
    private void initializeServices() {
        logger.info("Inicializando servicios...");
        this.userService = new UserService();
        this.sensorService = new SensorService();
        logger.info("Servicios inicializados correctamente");
    }
    
    public static void main(String[] args) {
        Application app = new Application();
        app.run();
    }
    
    public void run() {
        try {
            logger.info("=== Iniciando TP BBDD 2 - Persistencia Políglota ===");
            
            // Inicializar conexiones
            initializeConnections();
            
            // Ejecutar migraciones automáticamente
            runMigrations();
            
            // Inicializar servicios después de las migraciones
            initializeServices();
            
            // Mostrar menú principal
            showMainMenu();
            
        } catch (Exception e) {
            logger.error("Error en la aplicación", e);
        } finally {
            cleanup();
        }
    }
    
    private void runMigrations() {
        try {
            System.out.println("\n🔧 Ejecutando migraciones de base de datos...");
            logger.info("Iniciando migraciones automáticas");
            
            MigrationRunner.runAllMigrations();
            
            System.out.println("✅ Migraciones completadas exitosamente");
            logger.info("Migraciones completadas");
            
            // Ejecutar seeding de datos
            runDataSeeding();
            
        } catch (Exception e) {
            logger.error("Error ejecutando migraciones", e);
            System.out.println("⚠️  Advertencia: Algunas migraciones fallaron, pero la aplicación continuará");
        }
    }
    
    private void runDataSeeding() {
        try {
            System.out.println("\n🌱 Poblando bases de datos con datos de ejemplo...");
            logger.info("Iniciando seeding de datos");
            
            com.bd2.app.seeder.DataSeeder seeder = new com.bd2.app.seeder.DataSeeder();
            seeder.seedAll();
            
            System.out.println("✅ Datos de ejemplo cargados exitosamente");
            logger.info("Seeding completado");
            
        } catch (Exception e) {
            logger.error("Error ejecutando seeding", e);
            System.out.println("⚠️  Advertencia: El seeding falló, pero la aplicación continuará");
        }
    }
    
    private void initializeConnections() {
        try {
            DatabaseConfig config = DatabaseConfig.getInstance();
            
            System.out.println("\n🔄 Inicializando conexiones a bases de datos...");
            
            // MongoDB
            System.out.print("📄 Conectando a MongoDB... ");
            MongoConnectionManager mongoManager = MongoConnectionManager.getInstance();
            mongoManager.registerShutdownHook();
            if (mongoManager.isConnected()) {
                System.out.println("✅ Conectado");
                mongoManager.logConnectionPoolStats();
            } else {
                System.out.println("❌ Error");
                throw new RuntimeException("No se pudo conectar a MongoDB");
            }
            
            // Cassandra
            System.out.print("🔗 Conectando a Cassandra... ");
            CassandraConnectionManager cassandraManager = CassandraConnectionManager.getInstance();
            cassandraManager.registerShutdownHook();
            if (cassandraManager.isConnected()) {
                System.out.println("✅ Conectado");
                cassandraManager.logClusterInfo();
            } else {
                System.out.println("❌ Error");
                throw new RuntimeException("No se pudo conectar a Cassandra");
            }
            
            // Neo4j
            System.out.print("🌐 Conectando a Neo4j... ");
            Neo4jConnectionManager neo4jManager = Neo4jConnectionManager.getInstance();
            neo4jManager.registerShutdownHook();
            if (neo4jManager.isConnected()) {
                System.out.println("✅ Conectado");
                neo4jManager.logServerInfo();
            } else {
                System.out.println("❌ Error");
                throw new RuntimeException("No se pudo conectar a Neo4j");
            }
            
            System.out.println("\n🎉 Todas las conexiones establecidas exitosamente!");
            System.out.println("📊 Configuración:");
            System.out.println("   - Aplicación: " + config.getAppName() + " v" + config.getAppVersion());
            System.out.println("   - Entorno: " + config.getAppEnvironment());
            
        } catch (Exception e) {
            logger.error("Error inicializando conexiones", e);
            throw new RuntimeException("No se pudieron inicializar las conexiones", e);
        }
    }
    
    private void showMainMenu() {
        while (true) {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("🏠 MENÚ PRINCIPAL - TP BBDD 2 Persistencia Políglota");
            System.out.println("=".repeat(60));
            System.out.println("1. 👤 Gestión de Usuarios");
            System.out.println("2. 📊 Gestión de Sensores y Mediciones");
            System.out.println("3. 🔐 Consultas de Autorización");
            System.out.println("4. 📈 Dashboard y Estadísticas");
            System.out.println("5. 🧪 Ejecutar Demos");
            System.out.println("6. 🔧 Información del Sistema");
            System.out.println("7. 🗄️ Ejecutar Migraciones");
            System.out.println("0. 🚪 Salir");
            System.out.println("=".repeat(60));
            
            System.out.print("Selecciona una opción: ");
            
            try {
                // Verificar si hay entrada disponible
                if (!scanner.hasNextLine()) {
                    logger.warn("No hay entrada disponible. Saliendo...");
                    System.out.println("\n⚠️  No hay entrada estándar disponible. La aplicación se cerrará.");
                    return;
                }
                
                String input = scanner.nextLine();
                int option = Integer.parseInt(input);
                
                switch (option) {
                    case 1 -> showUserMenu();
                    case 2 -> showSensorMenu();
                    case 3 -> showAuthorizationMenu();
                    case 4 -> showDashboard();
                    case 5 -> runDemos();
                    case 6 -> showSystemInfo();
                    case 7 -> showMigrationsMenu();
                    case 0 -> {
                        System.out.println("\n👋 ¡Hasta luego!");
                        return;
                    }
                    default -> System.out.println("❌ Opción inválida. Intenta de nuevo.");
                }
                
            } catch (NumberFormatException e) {
                System.out.println("❌ Por favor ingresa un número válido.");
            } catch (Exception e) {
                logger.error("Error en menú principal", e);
                System.out.println("❌ Error inesperado: " + e.getMessage());
                // Salir del bucle si hay un error crítico
                return;
            }
        }
    }
    
    private void showUserMenu() {
        System.out.println("\n👤 === GESTIÓN DE USUARIOS ===");
        System.out.println("1. Registrar nuevo usuario");
        System.out.println("2. Autenticar usuario");
        System.out.println("3. Ver perfil de usuario");
        System.out.println("4. Listar usuarios por departamento");
        System.out.println("0. Volver al menú principal");
        
        System.out.print("Selecciona una opción: ");
        int option = Integer.parseInt(scanner.nextLine());
        
        switch (option) {
            case 1 -> registerUser();
            case 2 -> authenticateUser();
            case 3 -> viewUserProfile();
            case 4 -> listUsersByDepartment();
            case 0 -> { /* Volver */ }
            default -> System.out.println("❌ Opción inválida.");
        }
    }
    
    private void showSensorMenu() {
        System.out.println("\n📊 === GESTIÓN DE SENSORES ===");
        System.out.println("1. Registrar medición");
        System.out.println("2. Ver últimas mediciones de sensor");
        System.out.println("3. Ver mediciones por ciudad");
        System.out.println("4. Ver estado actual de sensores");
        System.out.println("5. Asignar técnico a ciudad");
        System.out.println("0. Volver al menú principal");
        
        System.out.print("Selecciona una opción: ");
        int option = Integer.parseInt(scanner.nextLine());
        
        switch (option) {
            case 1 -> recordMeasurement();
            case 2 -> viewSensorMeasurements();
            case 3 -> viewCityMeasurements();
            case 4 -> viewSensorStatus();
            case 5 -> assignTechnician();
            case 0 -> { /* Volver */ }
            default -> System.out.println("❌ Opción inválida.");
        }
    }
    
    private void showAuthorizationMenu() {
        System.out.println("\n🔐 === CONSULTAS DE AUTORIZACIÓN ===");
        System.out.println("1. Ver permisos de usuario");
        System.out.println("2. Verificar permiso específico");
        System.out.println("3. Ver miembros de grupo");
        System.out.println("4. Ver técnicos por ciudad");
        System.out.println("0. Volver al menú principal");
        
        System.out.print("Selecciona una opción: ");
        int option = Integer.parseInt(scanner.nextLine());
        
        switch (option) {
            case 1 -> viewUserPermissions();
            case 2 -> checkSpecificPermission();
            case 3 -> viewGroupMembers();
            case 4 -> viewTechniciansByCity();
            case 0 -> { /* Volver */ }
            default -> System.out.println("❌ Opción inválida.");
        }
    }
    
    private void showDashboard() {
        System.out.println("\n📈 === DASHBOARD Y ESTADÍSTICAS ===");
        
        // Para el dashboard necesitamos un usuario admin
        System.out.print("Ingresa ID de usuario administrador: ");
        String adminId = scanner.nextLine();
        
        Map<String, Object> stats = sensorService.getDashboardStats(adminId);
        
        if (stats.isEmpty()) {
            System.out.println("❌ No se pudieron obtener estadísticas (verifica permisos)");
            return;
        }
        
        System.out.println("\n📊 Estadísticas del Sistema:");
        System.out.println("─".repeat(40));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> users = (Map<String, Object>) stats.get("users");
        if (users != null) {
            System.out.println("👥 Usuarios:");
            System.out.println("   Total: " + users.get("total"));
            System.out.println("   Activos: " + users.get("active"));
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> sensors = (Map<String, Object>) stats.get("sensors");
        if (sensors != null) {
            System.out.println("📡 Sensores:");
            System.out.println("   Total: " + sensors.get("total"));
            System.out.println("   Activos: " + sensors.get("active"));
        }
        
        Object groups = stats.get("groups");
        if (groups != null) {
            System.out.println("👥 Grupos: " + groups);
        }
    }
    
    private void runDemos() {
        System.out.println("\n🧪 === EJECUTANDO DEMOS ===");
        
        try {
            // Demo 1: Registro y autenticación
            System.out.println("\n1️⃣ Demo: Registro y Autenticación de Usuario");
            String userId = userService.registerUser(
                "Demo User", 
                "demo@universidad.edu", 
                "password123", 
                "Investigación"
            );
            
            if (userId != null) {
                System.out.println("✅ Usuario registrado: " + userId);
                
                Map<String, Object> authResult = userService.authenticateUser("demo@universidad.edu", "password123");
                if (authResult != null) {
                    System.out.println("✅ Autenticación exitosa: " + authResult.get("fullName"));
                    System.out.println("   Permisos: " + authResult.get("permissions"));
                }
            }
            
            // Demo 2: Registro de mediciones
            System.out.println("\n2️⃣ Demo: Registro de Mediciones");
            if (userId != null) {
                Measurement measurement = Measurement.createTemperatureMeasurement(
                    "550e8400-e29b-41d4-a716-446655440001",
                    23.5,
                    "Buenos Aires",
                    "Argentina"
                );
                
                boolean recorded = sensorService.recordMeasurement(userId, measurement);
                if (recorded) {
                    System.out.println("✅ Medición registrada exitosamente");
                } else {
                    System.out.println("⚠️ No se pudo registrar la medición (verifica permisos)");
                }
            }
            
            // Demo 3: Consulta de estado de sensores
            System.out.println("\n3️⃣ Demo: Estado de Sensores");
            if (userId != null) {
                List<Map<String, Object>> sensorStatus = sensorService.getCurrentSensorStatus(userId);
                System.out.println("📊 Sensores encontrados: " + sensorStatus.size());
                
                sensorStatus.stream().limit(3).forEach(sensor -> {
                    System.out.println("   - " + sensor.get("sensorCode") + 
                                     " (" + sensor.get("city") + ") - " + 
                                     sensor.get("sensorState"));
                });
            }
            
            System.out.println("\n✅ Demos completados!");
            
        } catch (Exception e) {
            logger.error("Error ejecutando demos", e);
            System.out.println("❌ Error en demos: " + e.getMessage());
        }
    }
    
    private void showSystemInfo() {
        System.out.println("\n🔧 === INFORMACIÓN DEL SISTEMA ===");
        
        DatabaseConfig config = DatabaseConfig.getInstance();
        
        System.out.println("📋 Configuración:");
        System.out.println("   Aplicación: " + config.getAppName());
        System.out.println("   Versión: " + config.getAppVersion());
        System.out.println("   Entorno: " + config.getAppEnvironment());
        
        System.out.println("\n🔗 Conexiones:");
        System.out.println("   MongoDB: " + config.getMongoHost() + ":" + config.getMongoPort());
        System.out.println("   Cassandra: " + config.getCassandraHost() + ":" + config.getCassandraPort());
        System.out.println("   Neo4j: " + config.getNeo4jUri());
        
        System.out.println("\n📊 Estado de Pools:");
        MongoConnectionManager.getInstance().logConnectionPoolStats();
        CassandraConnectionManager.getInstance().logSessionStats();
        Neo4jConnectionManager.getInstance().logConnectionPoolStats();
    }
    
    // Métodos auxiliares para las operaciones del menú
    private void registerUser() {
        System.out.print("Nombre completo: ");
        String fullName = scanner.nextLine();
        System.out.print("Email: ");
        String email = scanner.nextLine();
        System.out.print("Contraseña: ");
        String password = scanner.nextLine();
        System.out.print("Departamento: ");
        String department = scanner.nextLine();
        
        String userId = userService.registerUser(fullName, email, password, department);
        if (userId != null) {
            System.out.println("✅ Usuario registrado con ID: " + userId);
        } else {
            System.out.println("❌ Error registrando usuario");
        }
    }
    
    private void authenticateUser() {
        System.out.print("Email: ");
        String email = scanner.nextLine();
        System.out.print("Contraseña: ");
        String password = scanner.nextLine();
        
        Map<String, Object> result = userService.authenticateUser(email, password);
        if (result != null) {
            System.out.println("✅ Autenticación exitosa!");
            System.out.println("   Usuario: " + result.get("fullName"));
            System.out.println("   Departamento: " + result.get("department"));
            System.out.println("   Permisos: " + result.get("permissions"));
        } else {
            System.out.println("❌ Credenciales inválidas");
        }
    }
    
    private void viewUserProfile() {
        System.out.print("ID de usuario: ");
        String userId = scanner.nextLine();
        
        Map<String, Object> profile = userService.getUserProfile(userId);
        if (profile != null) {
            System.out.println("👤 Perfil de Usuario:");
            profile.forEach((key, value) -> 
                System.out.println("   " + key + ": " + value));
        } else {
            System.out.println("❌ Usuario no encontrado");
        }
    }
    
    private void listUsersByDepartment() {
        System.out.print("Departamento: ");
        String department = scanner.nextLine();
        
        List<User> users = userService.getUsersByDepartment(department);
        System.out.println("👥 Usuarios en " + department + ": " + users.size());
        users.forEach(user -> 
            System.out.println("   - " + user.getFullName() + " (" + user.getEmail() + ")"));
    }
    
    private void recordMeasurement() {
        System.out.print("ID de usuario: ");
        String userId = scanner.nextLine();
        System.out.print("ID de sensor: ");
        String sensorId = scanner.nextLine();
        System.out.print("Temperatura: ");
        Double temperature = Double.parseDouble(scanner.nextLine());
        System.out.print("Humedad: ");
        Double humidity = Double.parseDouble(scanner.nextLine());
        System.out.print("Ciudad: ");
        String city = scanner.nextLine();
        System.out.print("País: ");
        String country = scanner.nextLine();
        
        Measurement measurement = new Measurement(sensorId, temperature, humidity, "combinado", city, country);
        boolean recorded = sensorService.recordMeasurement(userId, measurement);
        
        if (recorded) {
            System.out.println("✅ Medición registrada exitosamente");
        } else {
            System.out.println("❌ Error registrando medición");
        }
    }
    
    private void viewSensorMeasurements() {
        System.out.print("ID de usuario: ");
        String userId = scanner.nextLine();
        System.out.print("ID de sensor: ");
        String sensorId = scanner.nextLine();
        System.out.print("Límite de resultados: ");
        int limit = Integer.parseInt(scanner.nextLine());
        
        List<Measurement> measurements = sensorService.getLatestMeasurements(userId, sensorId, limit);
        System.out.println("📊 Mediciones encontradas: " + measurements.size());
        
        measurements.forEach(m -> 
            System.out.println("   " + m.getTimestamp() + " - T:" + m.getTemperature() + "°C, H:" + m.getHumidity() + "%"));
    }
    
    private void viewCityMeasurements() {
        System.out.print("ID de usuario: ");
        String userId = scanner.nextLine();
        System.out.print("Ciudad: ");
        String city = scanner.nextLine();
        System.out.print("Límite de resultados: ");
        int limit = Integer.parseInt(scanner.nextLine());
        
        List<Measurement> measurements = sensorService.getMeasurementsByCity(userId, city, LocalDate.now(), limit);
        System.out.println("📊 Mediciones de " + city + ": " + measurements.size());
        
        measurements.forEach(m -> 
            System.out.println("   Sensor:" + m.getSensorId() + " - T:" + m.getTemperature() + "°C, H:" + m.getHumidity() + "%"));
    }
    
    private void viewSensorStatus() {
        System.out.print("ID de usuario: ");
        String userId = scanner.nextLine();
        
        List<Map<String, Object>> status = sensorService.getCurrentSensorStatus(userId);
        System.out.println("📡 Estado de Sensores: " + status.size());
        
        status.forEach(sensor -> {
            System.out.println("   - " + sensor.get("sensorCode") + 
                             " (" + sensor.get("city") + ") - " + 
                             sensor.get("sensorState"));
            if (sensor.get("lastTemperature") != null) {
                System.out.println("     Última medición: T:" + sensor.get("lastTemperature") + "°C");
            }
        });
    }
    
    private void assignTechnician() {
        System.out.print("ID de administrador: ");
        String adminId = scanner.nextLine();
        System.out.print("ID de técnico: ");
        String techId = scanner.nextLine();
        System.out.print("Ciudad: ");
        String city = scanner.nextLine();
        
        boolean assigned = sensorService.assignTechnicianToCity(adminId, techId, city);
        if (assigned) {
            System.out.println("✅ Técnico asignado a la ciudad");
        } else {
            System.out.println("❌ Error asignando técnico");
        }
    }
    
    private void viewUserPermissions() {
        System.out.print("ID de usuario: ");
        String userId = scanner.nextLine();
        
        Map<String, Object> profile = userService.getUserProfile(userId);
        if (profile != null) {
            System.out.println("🔐 Permisos de " + profile.get("fullName") + ":");
            @SuppressWarnings("unchecked")
            java.util.Set<String> permissions = (java.util.Set<String>) profile.get("permissions");
            if (permissions != null) {
                permissions.forEach(p -> System.out.println("   - " + p));
            }
        }
    }
    
    private void checkSpecificPermission() {
        // Implementación simplificada
        System.out.println("⚠️ Funcionalidad disponible a través de la API de servicios");
    }
    
    private void viewGroupMembers() {
        // Implementación simplificada
        System.out.println("⚠️ Funcionalidad disponible a través de la API de servicios");
    }
    
    private void viewTechniciansByCity() {
        System.out.print("Ciudad: ");
        String city = scanner.nextLine();
        
        List<Map<String, Object>> technicians = sensorService.getAvailableTechnicians(city);
        System.out.println("🔧 Técnicos en " + city + ": " + technicians.size());
        
        technicians.forEach(tech -> 
            System.out.println("   - " + tech.get("fullName") + " (" + tech.get("email") + ")"));
    }
    
    private void cleanup() {
        try {
            System.out.println("\n🧹 Cerrando conexiones...");
            
            MongoConnectionManager.getInstance().close();
            CassandraConnectionManager.getInstance().close();
            Neo4jConnectionManager.getInstance().close();
            
            System.out.println("✅ Aplicación terminada correctamente");
            
        } catch (Exception e) {
            logger.error("Error durante cleanup", e);
        }
    }
    
    private void showMigrationsMenu() {
        System.out.println("\n🗄️ === MIGRACIONES DE BASE DE DATOS ===");
        System.out.println("1. Ejecutar todas las migraciones");
        System.out.println("2. Migrar solo MongoDB");
        System.out.println("3. Migrar solo Cassandra");
        System.out.println("4. Migrar solo Neo4j");
        System.out.println("0. Volver al menú principal");
        System.out.println("=".repeat(50));
        
        System.out.print("Selecciona una opción: ");
        
        try {
            int option = Integer.parseInt(scanner.nextLine());
            
            switch (option) {
                case 1 -> {
                    System.out.println("\n🚀 Ejecutando todas las migraciones...");
                    MigrationRunner.runAllMigrations();
                    System.out.println("\n✅ Proceso de migraciones completado");
                    System.out.print("Presiona Enter para continuar...");
                    scanner.nextLine();
                }
                case 2 -> {
                    System.out.println("\n📊 Ejecutando migraciones MongoDB...");
                    try {
                        MigrationRunner.runMongoMigrations();
                        System.out.println("✅ Migraciones MongoDB completadas");
                    } catch (Exception e) {
                        System.out.println("❌ Error en migraciones MongoDB: " + e.getMessage());
                    }
                    System.out.print("Presiona Enter para continuar...");
                    scanner.nextLine();
                }
                case 3 -> {
                    System.out.println("\n📈 Ejecutando migraciones Cassandra...");
                    try {
                        MigrationRunner.runCassandraMigrations();
                        System.out.println("✅ Migraciones Cassandra completadas");
                    } catch (Exception e) {
                        System.out.println("❌ Error en migraciones Cassandra: " + e.getMessage());
                    }
                    System.out.print("Presiona Enter para continuar...");
                    scanner.nextLine();
                }
                case 4 -> {
                    System.out.println("\n🕸️ Ejecutando migraciones Neo4j...");
                    try {
                        MigrationRunner.runNeo4jMigrations();
                        System.out.println("✅ Migraciones Neo4j completadas");
                    } catch (Exception e) {
                        System.out.println("❌ Error en migraciones Neo4j: " + e.getMessage());
                    }
                    System.out.print("Presiona Enter para continuar...");
                    scanner.nextLine();
                }
                case 0 -> {
                    return;
                }
                default -> System.out.println("❌ Opción inválida. Intenta de nuevo.");
            }
            
        } catch (NumberFormatException e) {
            System.out.println("❌ Por favor ingresa un número válido.");
        } catch (Exception e) {
            logger.error("Error en menú de migraciones", e);
            System.out.println("❌ Error inesperado: " + e.getMessage());
        }
    }
}
