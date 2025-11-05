package com.bd2.app;

import com.bd2.app.config.DatabaseConfig;
import com.bd2.app.database.CassandraConnectionManager;
import com.bd2.app.database.MongoConnectionManager;
import com.bd2.app.database.Neo4jConnectionManager;
import com.bd2.app.migrations.MigrationRunner;
import com.bd2.app.model.Measurement;
import com.bd2.app.model.User;
import com.bd2.app.model.Sensor;
import com.bd2.app.service.SensorService;
import com.bd2.app.service.UserService;
import com.bd2.app.service.ProcessService;
import com.bd2.app.service.InvoiceService;
import com.bd2.app.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.*;

/**
 * Aplicación principal del sistema de persistencia políglota
 * Demuestra el uso integrado de MongoDB, Cassandra y Neo4j
 */
public class Application {
    
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    
    private UserService userService;
    private SensorService sensorService;
    private ProcessService processService;
    private InvoiceService invoiceService;
    private MessageService messageService;
    private final Scanner scanner;
    
    // Usuario autenticado
    private Map<String, Object> currentUser;
    private Set<String> currentPermissions;
    private Set<String> currentRoles;
    
    public Application() {
        this.scanner = new Scanner(System.in);
    }
    
    private void initializeServices() {
        logger.info("Inicializando servicios...");
        this.userService = new UserService();
        this.sensorService = new SensorService();
        this.processService = new ProcessService();
        this.invoiceService = new InvoiceService();
        this.messageService = new MessageService();
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
            
            // Loop principal: autenticación y menú
            boolean continueRunning = true;
            while (continueRunning) {
                // Autenticar usuario antes de mostrar el menú
                if (authenticateUserLogin()) {
                    // Mostrar menú principal según permisos
                    // Si el usuario sale del menú, vuelve al login
                    showMainMenu();
                    
                    // Preguntar si quiere volver a iniciar sesión o salir
                    System.out.print("\n¿Deseas iniciar sesión con otro usuario? (s/n): ");
                    String response = scanner.nextLine().trim().toLowerCase();
                    
                    if (!response.equals("s") && !response.equals("si") && !response.equals("sí")) {
                        continueRunning = false;
                        System.out.println("\n👋 ¡Hasta luego!");
                    }
                } else {
                    System.out.println("\n❌ No se pudo autenticar. La aplicación se cerrará.");
                    continueRunning = false;
                }
            }
            
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
    
    /**
     * Autentica al usuario antes de acceder al sistema
     */
    private boolean authenticateUserLogin() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🔐 AUTENTICACIÓN DE USUARIO");
        System.out.println("=".repeat(60));
        System.out.println("Por favor, ingresa tus credenciales para acceder al sistema.");
        System.out.println("Usuario admin: admin@admin.com / Contraseña: admin");
        System.out.println("=".repeat(60));
        
        int maxAttempts = 3;
        int attempts = 0;
        
        while (attempts < maxAttempts) {
            try {
                System.out.print("\n📧 Email: ");
                String email = scanner.nextLine().trim();
                
                System.out.print("🔑 Contraseña: ");
                String password = scanner.nextLine().trim();
                
                // Intentar autenticar
                Map<String, Object> authResult = userService.authenticateUser(email, password);
                
                if (authResult != null) {
                    this.currentUser = authResult;
                    @SuppressWarnings("unchecked")
                    Set<String> permissions = (Set<String>) authResult.get("permissions");
                    this.currentPermissions = permissions != null ? permissions : new HashSet<>();
                    
                    @SuppressWarnings("unchecked")
                    Set<String> roles = (Set<String>) authResult.get("roles");
                    this.currentRoles = roles != null ? roles : new HashSet<>();
                    
                    System.out.println("\n✅ ¡Bienvenido, " + authResult.get("fullName") + "!");
                    System.out.println("📊 Departamento: " + authResult.get("department"));
                    
                    // Mostrar roles de forma más clara
                    if (this.currentRoles.isEmpty()) {
                        System.out.println("🎭 Roles: (sin roles en Neo4j - modo simplificado)");
                    } else {
                        System.out.println("🎭 Roles: " + this.currentRoles);
                    }
                    
                    // Mostrar permisos de forma más clara
                    if (this.currentPermissions.isEmpty()) {
                        System.out.println("🔐 Permisos: (todos - modo simplificado)");
                    } else {
                        System.out.println("🔐 Permisos: " + this.currentPermissions);
                    }
                    
                    return true;
                } else {
                    attempts++;
                    int remaining = maxAttempts - attempts;
                    if (remaining > 0) {
                        System.out.println("\n❌ Credenciales inválidas. Te quedan " + remaining + " intentos.");
                    }
                }
                
            } catch (Exception e) {
                logger.error("Error durante autenticación", e);
                attempts++;
            }
        }
        
        System.out.println("\n❌ Número máximo de intentos alcanzado.");
        return false;
    }
    
    /**
     * Verifica si el usuario tiene un permiso específico
     */
    private boolean hasPermission(String permission) {
        return currentPermissions != null && currentPermissions.contains(permission);
    }
    
    /**
     * Verifica si el usuario es administrador
     * Puede ser por rol directo o por tener permisos administrativos
     */
    private boolean isAdmin() {
        // Verificar si tiene el rol de admin
        if (currentRoles != null && currentRoles.contains("admin")) {
            return true;
        }
        // O si tiene permisos administrativos
        return hasPermission("pt_admin_usuarios") || 
               hasPermission("pt_admin_grupos") || 
               hasPermission("pt_admin_sensores");
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
            
            // Cassandra (intentar conectar pero no fallar si no está disponible)
            System.out.print("🔗 Conectando a Cassandra... ");
            try {
                CassandraConnectionManager cassandraManager = CassandraConnectionManager.getInstance();
                cassandraManager.registerShutdownHook();
                if (cassandraManager.isConnected()) {
                    System.out.println("✅ Conectado");
                    cassandraManager.logClusterInfo();
                } else {
                    System.out.println("⚠️  No disponible (funcionalidad limitada)");
                }
            } catch (Exception cassandraError) {
                System.out.println("⚠️  No disponible (funcionalidad limitada)");
                logger.warn("Cassandra no disponible, continuando sin series temporales", cassandraError);
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
            System.out.println("👤 Usuario: " + currentUser.get("fullName"));
            System.out.println("📊 Departamento: " + currentUser.get("department"));
            System.out.println("🎭 Roles: " + currentRoles);
            System.out.println("🔐 Permisos: " + currentPermissions);
            System.out.println("=".repeat(60));
            
            // Construir menú dinámico según permisos
            Map<Integer, String> menuOptions = new HashMap<>();
            int optionNumber = 1;
            
            // 1. Gestión de Usuarios - Solo administradores
            if (canAccessUserManagement()) {
                System.out.println(optionNumber + ". 👤 Gestión de Usuarios");
                menuOptions.put(optionNumber, "users");
                optionNumber++;
            }
            
            // 2. Gestión de Sensores y Mediciones - Admins, Operadores y Técnicos
            if (canAccessSensorManagement()) {
                System.out.println(optionNumber + ". 📊 Gestión de Sensores y Mediciones");
                menuOptions.put(optionNumber, "sensors");
                optionNumber++;
            }
            
            // 3. Gestión de Procesos y Reportes - Todos los usuarios autenticados
            System.out.println(optionNumber + ". 📋 Gestión de Procesos y Reportes");
            menuOptions.put(optionNumber, "processes");
            optionNumber++;
            
            // 4. Mensajería - Todos los usuarios
            System.out.println(optionNumber + ". 💬 Mensajería");
            menuOptions.put(optionNumber, "messages");
            optionNumber++;
            
            // 5. Facturación y Cuenta Corriente - Todos los usuarios
            System.out.println(optionNumber + ". 💰 Facturación y Cuenta Corriente");
            menuOptions.put(optionNumber, "invoices");
            optionNumber++;
            
            // 6. Dashboard y Estadísticas - Todos pueden ver
            System.out.println(optionNumber + ". 📈 Dashboard y Estadísticas");
            menuOptions.put(optionNumber, "dashboard");
            optionNumber++;
            
            // 7. Información del Sistema - Todos pueden ver
            System.out.println(optionNumber + ". 🔧 Información del Sistema");
            menuOptions.put(optionNumber, "system");
            optionNumber++;
            
            System.out.println("0. 🚪 Salir");
            System.out.println("=".repeat(60));
            
            System.out.print("Selecciona una opción: ");
            System.out.flush();
            
            try {
                String input = scanner.nextLine();
                int option = Integer.parseInt(input);
                
                if (option == 0) {
                    System.out.println("\n👋 ¡Hasta luego, " + currentUser.get("fullName") + "!");
                    return;
                }
                
                String selectedOption = menuOptions.get(option);
                if (selectedOption == null) {
                    System.out.println("❌ Opción inválida. Intenta de nuevo.");
                    continue;
                }
                
                switch (selectedOption) {
                    case "users" -> showUserMenu();
                    case "sensors" -> showSensorMenu();
                    case "processes" -> showProcessMenu();
                    case "messages" -> showMessageMenu();
                    case "invoices" -> showInvoiceMenu();
                    case "dashboard" -> showDashboard();
                    case "system" -> showSystemInfo();
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
    
    /**
     * Verifica si el usuario puede acceder a la gestión de usuarios
     */
    private boolean canAccessUserManagement() {
        // Solo administradores pueden gestionar usuarios
        return hasPermission("pt_admin_usuarios") || isAdmin();
    }
    
    /**
     * Verifica si el usuario puede acceder a la gestión de sensores
     */
    private boolean canAccessSensorManagement() {
        // Admins, operadores y técnicos pueden gestionar sensores
        return hasPermission("pt_admin_sensores") || 
               hasPermission("pt_maxmin") || 
               hasPermission("pt_prom") ||
               isAdmin();
    }
    
    /**
     * Verifica si el usuario puede ejecutar demos
     */
    private boolean canExecuteDemos() {
        // Solo admins y operadores pueden ejecutar demos
        return hasPermission("pt_admin_usuarios") || 
               hasPermission("pt_maxmin") ||
               isAdmin();
    }
    
    private void showUserMenu() {
        // Verificar permisos antes de mostrar el menú
        if (!canAccessUserManagement()) {
            System.out.println("❌ No tienes permisos para acceder a la gestión de usuarios.");
            return;
        }
        
        System.out.println("\n👤 === GESTIÓN DE USUARIOS ===");
        System.out.println("1. Registrar nuevo usuario");
        System.out.println("2. Autenticar usuario");
        System.out.println("3. Ver perfil de usuario");
        System.out.println("4. Listar usuarios por departamento");
        System.out.println("0. Volver al menú principal");
        
        System.out.print("Selecciona una opción: ");
        try {
            int option = Integer.parseInt(scanner.nextLine());
            
            switch (option) {
                case 1 -> registerUser();
                case 2 -> authenticateUser();
                case 3 -> viewUserProfile();
                case 4 -> listUsersByDepartment();
                case 0 -> { /* Volver */ }
                default -> System.out.println("❌ Opción inválida.");
            }
        } catch (NumberFormatException e) {
            System.out.println("❌ Por favor ingresa un número válido.");
        }
    }
    
    private void showSensorMenu() {
        // Verificar permisos antes de mostrar el menú
        if (!canAccessSensorManagement()) {
            System.out.println("❌ No tienes permisos para acceder a la gestión de sensores.");
            return;
        }
        
        System.out.println("\n📊 === GESTIÓN DE SENSORES ===");
        
        // Construir menú según permisos específicos
        Map<Integer, String> menuOptions = new HashMap<>();
        int optionNumber = 1;
        
        // Crear sensor - Solo admins
        if (isAdmin() || hasPermission("pt_admin_sensores")) {
            System.out.println(optionNumber + ". Crear nuevo sensor");
            menuOptions.put(optionNumber, "create");
            optionNumber++;
        }
        
        // Registrar medición - Solo operadores y admins
        if (hasPermission("pt_maxmin") || hasPermission("pt_prom") || isAdmin()) {
            System.out.println(optionNumber + ". Registrar medición");
            menuOptions.put(optionNumber, "record");
            optionNumber++;
        }
        
        // Ver mediciones - Todos con acceso a sensores
        System.out.println(optionNumber + ". Ver últimas mediciones de sensor");
        menuOptions.put(optionNumber, "view_sensor");
        optionNumber++;
        
        System.out.println(optionNumber + ". Ver mediciones por ciudad");
        menuOptions.put(optionNumber, "view_city");
        optionNumber++;
        
        System.out.println(optionNumber + ". Ver estado actual de sensores");
        menuOptions.put(optionNumber, "status");
        optionNumber++;
        
        // Asignar técnico - Solo admins
        if (isAdmin() || hasPermission("pt_admin_sensores")) {
            System.out.println(optionNumber + ". Asignar técnico a ciudad");
            menuOptions.put(optionNumber, "assign");
            optionNumber++;
        }
        
        System.out.println("0. Volver al menú principal");
        
        System.out.print("Selecciona una opción: ");
        try {
            int option = Integer.parseInt(scanner.nextLine());
            
            if (option == 0) return;
            
            String selectedOption = menuOptions.get(option);
            if (selectedOption == null) {
                System.out.println("❌ Opción inválida.");
                return;
            }
            
            switch (selectedOption) {
                case "create" -> createNewSensor();
                case "record" -> recordMeasurement();
                case "view_sensor" -> viewSensorMeasurements();
                case "view_city" -> viewCityMeasurements();
                case "status" -> viewSensorStatus();
                case "assign" -> assignTechnician();
                default -> System.out.println("❌ Opción inválida.");
            }
        } catch (NumberFormatException e) {
            System.out.println("❌ Por favor ingresa un número válido.");
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
        try {
            int option = Integer.parseInt(scanner.nextLine());
            
            switch (option) {
                case 1 -> viewUserPermissions();
                case 2 -> checkSpecificPermission();
                case 3 -> viewGroupMembers();
                case 4 -> viewTechniciansByCity();
                case 0 -> { /* Volver */ }
                default -> System.out.println("❌ Opción inválida.");
            }
        } catch (NumberFormatException e) {
            System.out.println("❌ Por favor ingresa un número válido.");
        }
    }
    
    private void showDashboard() {
        System.out.println("\n📈 === DASHBOARD Y ESTADÍSTICAS ===");
        
        // Usar el ID del usuario autenticado actual
        String userId = (String) currentUser.get("userId");
        
        Map<String, Object> stats = sensorService.getDashboardStats(userId);
        
        if (stats.isEmpty()) {
            System.out.println("❌ No se pudieron obtener estadísticas.");
            System.out.println("💡 Nota: Solo los administradores pueden ver el dashboard completo.");
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
        
        // Mostrar permisos del usuario actual
        if (isAdmin()) {
            System.out.println("\n🔐 Tienes acceso completo al sistema (Administrador)");
        } else {
            System.out.println("\n🔐 Tus permisos: " + currentPermissions);
        }
    }
    
    private void runDemos() {
        // Verificar permisos antes de ejecutar demos
        if (!canExecuteDemos()) {
            System.out.println("❌ No tienes permisos para ejecutar demos.");
            System.out.println("💡 Solo los administradores y operadores pueden ejecutar demos.");
            return;
        }
        
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
        // Verificar permisos de administrador
        if (!isAdmin() && !hasPermission("pt_admin_usuarios")) {
            System.out.println("❌ No tienes permisos para registrar usuarios.");
            System.out.println("💡 Solo los administradores pueden registrar nuevos usuarios.");
            return;
        }
        
        System.out.println("\n👤 REGISTRAR NUEVO USUARIO");
        System.out.println("═".repeat(60));
        
        System.out.print("Nombre completo: ");
        String fullName = scanner.nextLine();
        
        System.out.print("Email: ");
        String email = scanner.nextLine();
        
        System.out.print("Contraseña: ");
        String password = scanner.nextLine();
        
        System.out.print("Departamento: ");
        String department = scanner.nextLine();
        
        // Seleccionar rol
        System.out.println("\nSelecciona el rol:");
        System.out.println("1. Administrador (acceso completo)");
        System.out.println("2. Operador (gestión de sensores y mediciones)");
        System.out.println("3. Analista (solo lectura)");
        System.out.println("4. Técnico (mantenimiento)");
        System.out.print("Opción (1-4): ");
        
        String roleId;
        try {
            int roleOption = Integer.parseInt(scanner.nextLine());
            roleId = switch (roleOption) {
                case 1 -> "role_admin";
                case 2 -> "role_operator";
                case 3 -> "role_analyst";
                case 4 -> "role_technician";
                default -> "role_operator"; // Por defecto
            };
        } catch (NumberFormatException e) {
            roleId = "role_operator";
        }
        
        String userId = userService.registerUserWithRole(fullName, email, password, department, roleId);
        if (userId != null) {
            System.out.println("\n✅ Usuario registrado exitosamente!");
            System.out.println("ID: " + userId);
            System.out.println("Email: " + email);
            System.out.println("Rol: " + getRoleName(roleId));
        } else {
            System.out.println("❌ Error registrando usuario (el email puede estar duplicado)");
        }
    }
    
    private String getRoleName(String roleId) {
        return switch (roleId) {
            case "role_admin" -> "Administrador";
            case "role_operator" -> "Operador";
            case "role_analyst" -> "Analista";
            case "role_technician" -> "Técnico";
            default -> roleId;
        };
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
        System.out.print("Email del usuario: ");
        String email = scanner.nextLine().trim();
        
        Map<String, Object> profile = userService.getUserProfileByEmail(email);
        if (profile != null) {
            System.out.println("\n👤 PERFIL DE USUARIO");
            System.out.println("═".repeat(60));
            System.out.println("Nombre: " + profile.get("fullName"));
            System.out.println("Email: " + profile.get("email"));
            System.out.println("Departamento: " + profile.get("department"));
            System.out.println("Estado: " + profile.get("status"));
            System.out.println("Registrado: " + profile.get("registeredAt"));
            System.out.println("Permisos: " + profile.get("permissions"));
            System.out.println("═".repeat(60));
        } else {
            System.out.println("❌ Usuario no encontrado con email: " + email);
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
    
    private void createNewSensor() {
        // Verificar permisos de admin
        if (!isAdmin() && !hasPermission("pt_admin_sensores")) {
            System.out.println("❌ No tienes permisos para crear sensores.");
            System.out.println("💡 Solo los administradores pueden crear sensores.");
            return;
        }
        
        String userId = (String) currentUser.get("userId");
        
        System.out.println("\n🆕 CREAR NUEVO SENSOR");
        System.out.println("═".repeat(60));
        
        System.out.print("Nombre del sensor: ");
        String name = scanner.nextLine();
        
        System.out.print("Ciudad: ");
        String city = scanner.nextLine();
        
        System.out.print("País: ");
        String country = scanner.nextLine();
        
        System.out.println("\nTipo de sensor:");
        System.out.println("1. Temperatura");
        System.out.println("2. Humedad");
        System.out.print("Selecciona (1 o 2): ");
        
        String type;
        try {
            int typeOption = Integer.parseInt(scanner.nextLine());
            type = typeOption == 1 ? "temperature" : "humidity";
        } catch (NumberFormatException e) {
            type = "temperature"; // Por defecto
        }
        
        System.out.print("Latitud (ej: -34.6037): ");
        double latitude = Double.parseDouble(scanner.nextLine());
        
        System.out.print("Longitud (ej: -58.3816): ");
        double longitude = Double.parseDouble(scanner.nextLine());
        
        String sensorId = sensorService.createSensor(userId, name, city, country, type, latitude, longitude);
        
        if (sensorId != null) {
            System.out.println("\n✅ Sensor creado exitosamente!");
            System.out.println("ID: " + sensorId);
            System.out.println("Tipo: " + (type.equals("temperature") ? "Temperatura" : "Humedad"));
            System.out.println("💡 Ya puedes registrar mediciones con este sensor");
        } else {
            System.out.println("❌ Error creando sensor (verifica permisos)");
        }
    }
    
    private void recordMeasurement() {
        // Verificar permisos para registrar mediciones (operadores y admins)
        if (!hasPermission("pt_maxmin") && !hasPermission("pt_prom") && !isAdmin()) {
            System.out.println("❌ No tienes permisos para registrar mediciones.");
            System.out.println("💡 Solo los operadores y administradores pueden registrar mediciones.");
            return;
        }
        
        // Usar el ID del usuario autenticado actual
        String userId = (String) currentUser.get("userId");
        
        System.out.println("\n📊 REGISTRAR NUEVA MEDICIÓN");
        System.out.println("═".repeat(60));
        
        System.out.print("ID de sensor: ");
        String sensorId = scanner.nextLine();
        
        // Obtener información del sensor para saber su ubicación y tipo
        Sensor sensor = sensorService.getSensorById(sensorId);
        
        if (sensor == null) {
            System.out.println("❌ Sensor no encontrado: " + sensorId);
            return;
        }
        
        System.out.println("Sensor: " + sensor.getCode());
        System.out.println("Ubicación: " + sensor.getCity() + ", " + sensor.getCountry());
        System.out.println("Tipo de sensor: " + sensor.getType());
        System.out.println();
        
        String type = sensor.getType();
        Double temperature = 0.0;
        Double humidity = 0.0;
        
        try {
            if (type.equals("temperature")) {
                System.out.print("Temperatura (°C): ");
                temperature = Double.parseDouble(scanner.nextLine());
            } else if (type.equals("humidity")) {
                System.out.print("Humedad (%): ");
                humidity = Double.parseDouble(scanner.nextLine());
            } else {
                // Si es temperature_humidity, pedir ambos
                System.out.print("Temperatura (°C): ");
                temperature = Double.parseDouble(scanner.nextLine());
                System.out.print("Humedad (%): ");
                humidity = Double.parseDouble(scanner.nextLine());
            }
        } catch (Exception e) {
            System.out.println("❌ Entrada inválida");
            return;
        }
        
        // Usar ciudad y país del sensor
        Measurement measurement = new Measurement(sensorId, temperature, humidity, type, 
                                                 sensor.getCity(), sensor.getCountry());
        boolean recorded = sensorService.recordMeasurement(userId, measurement);
        
        if (recorded) {
            System.out.println("\n✅ Medición registrada exitosamente");
            System.out.println("Sensor: " + sensor.getCode());
            System.out.println("Ubicación: " + sensor.getCity() + ", " + sensor.getCountry());
            if (type.equals("temperature") || temperature > 0) {
                System.out.println("Temperatura: " + temperature + "°C");
            }
            if (type.equals("humidity") || humidity > 0) {
                System.out.println("Humedad: " + humidity + "%");
            }
        } else {
            System.out.println("❌ Error registrando medición");
        }
    }
    
    private void viewSensorMeasurements() {
        // Usar el ID del usuario autenticado actual
        String userId = (String) currentUser.get("userId");
        
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
        // Usar el ID del usuario autenticado actual
        String userId = (String) currentUser.get("userId");
        
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
        // Usar el ID del usuario autenticado actual
        String userId = (String) currentUser.get("userId");
        
        System.out.println("\n¿Deseas filtrar por ciudad? (s/n): ");
        String filterResponse = scanner.nextLine().trim().toLowerCase();
        
        List<Map<String, Object>> status;
        String cityFilter = null;
        
        if (filterResponse.equals("s") || filterResponse.equals("si") || filterResponse.equals("sí")) {
            System.out.print("Ciudad: ");
            cityFilter = scanner.nextLine().trim();
            status = sensorService.getSensorStatusByCity(userId, cityFilter);
        } else {
            status = sensorService.getCurrentSensorStatus(userId);
        }
        
        System.out.println("\n📡 ESTADO DE SENSORES" + 
                          (cityFilter != null ? " - " + cityFilter : "") + 
                          ": " + status.size());
        System.out.println("═".repeat(70));
        
        if (status.isEmpty()) {
            System.out.println("No hay sensores" + (cityFilter != null ? " en " + cityFilter : ""));
            return;
        }
        
        for (Map<String, Object> sensor : status) {
            System.out.println("📡 " + sensor.get("sensorCode") + 
                             " - " + sensor.get("city") + 
                             " (" + sensor.get("sensorState") + ")");
            
            if (sensor.get("lastTemperature") != null) {
                Double temp = (Double) sensor.get("lastTemperature");
                Double hum = (Double) sensor.get("lastHumidity");
                System.out.println("   Última medición: T:" + String.format("%.1f", temp) + "°C, H:" + String.format("%.1f", hum) + "%");
                System.out.println("   Momento: " + sensor.get("lastMeasurementTime"));
            } else {
                System.out.println("   Estado: Sin mediciones registradas");
            }
            System.out.println("─".repeat(70));
        }
    }
    
    private void assignTechnician() {
        // Verificar permisos de administrador
        if (!isAdmin() && !hasPermission("pt_admin_sensores")) {
            System.out.println("❌ No tienes permisos para asignar técnicos.");
            System.out.println("💡 Solo los administradores pueden asignar técnicos a ciudades.");
            return;
        }
        
        // Usar el ID del usuario autenticado actual como admin
        String adminId = (String) currentUser.get("userId");
        
        System.out.println("\n🔧 ASIGNAR TÉCNICO A CIUDAD");
        System.out.println("═".repeat(60));
        
        System.out.print("Email del técnico: ");
        String techEmail = scanner.nextLine().trim();
        
        System.out.print("Ciudad: ");
        String city = scanner.nextLine().trim();
        
        boolean assigned = sensorService.assignTechnicianToCityByEmail(adminId, techEmail, city);
        if (assigned) {
            System.out.println("\n✅ Técnico asignado exitosamente");
            System.out.println("Técnico: " + techEmail);
            System.out.println("Ciudad: " + city);
        } else {
            System.out.println("❌ Error asignando técnico (verifica que el email exista)");
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
    
    // ============================================
    // NUEVOS MENÚS DE FUNCIONALIDADES
    // ============================================
    
    private void showProcessMenu() {
        System.out.println("\n📋 === GESTIÓN DE PROCESOS Y REPORTES ===");
        System.out.println("1. Solicitar nuevo reporte");
        System.out.println("2. Ver mis procesos solicitados");
        System.out.println("3. Ejecutar proceso pendiente");
        System.out.println("4. Ver resultado de proceso");
        System.out.println("0. Volver al menú principal");
        
        System.out.print("Selecciona una opción: ");
        try {
            int option = Integer.parseInt(scanner.nextLine());
            
            switch (option) {
                case 1 -> requestNewReport();
                case 2 -> viewMyProcesses();
                case 3 -> executeProcessRequest();
                case 4 -> viewProcessResult();
                case 0 -> { /* Volver */ }
                default -> System.out.println("❌ Opción inválida.");
            }
        } catch (NumberFormatException e) {
            System.out.println("❌ Por favor ingresa un número válido.");
        }
    }
    
    private void showMessageMenu() {
        System.out.println("\n💬 === MENSAJERÍA ===");
        System.out.println("1. Enviar mensaje privado");
        System.out.println("2. Ver mis conversaciones");
        System.out.println("3. Ver mensajes de conversación");
        System.out.println("0. Volver al menú principal");
        
        System.out.print("Selecciona una opción: ");
        try {
            int option = Integer.parseInt(scanner.nextLine());
            
            switch (option) {
                case 1 -> sendPrivateMessage();
                case 2 -> viewMyConversations();
                case 3 -> viewConversationMessages();
                case 0 -> { /* Volver */ }
                default -> System.out.println("❌ Opción inválida.");
            }
        } catch (NumberFormatException e) {
            System.out.println("❌ Por favor ingresa un número válido.");
        }
    }
    
    private void showInvoiceMenu() {
        System.out.println("\n💰 === FACTURACIÓN Y CUENTA CORRIENTE ===");
        System.out.println("1. Ver mis facturas");
        System.out.println("2. Ver saldo de cuenta corriente");
        System.out.println("3. Generar factura para proceso");
        System.out.println("0. Volver al menú principal");
        
        System.out.print("Selecciona una opción: ");
        try {
            int option = Integer.parseInt(scanner.nextLine());
            
            switch (option) {
                case 1 -> viewMyInvoices();
                case 2 -> viewAccountBalance();
                case 3 -> generateInvoiceForProcess();
                case 0 -> { /* Volver */ }
                default -> System.out.println("❌ Opción inválida.");
            }
        } catch (NumberFormatException e) {
            System.out.println("❌ Por favor ingresa un número válido.");
        }
    }
    
    // ============================================
    // IMPLEMENTACIÓN DE PROCESOS
    // ============================================
    
    private void requestNewReport() {
        String userId = (String) currentUser.get("userId");
        
        System.out.println("\n📋 SOLICITAR NUEVO REPORTE");
        System.out.println("Tipos de reporte disponibles:");
        System.out.println("1. Reporte Max/Min (pt_maxmin)");
        System.out.println("2. Reporte de Promedios (pt_prom)");
        System.out.println("3. Reporte de Alertas (pt_alerts)");
        
        System.out.print("Selecciona tipo de reporte: ");
        int type = Integer.parseInt(scanner.nextLine());
        
        String processTypeId = switch (type) {
            case 1 -> "pt_maxmin";
            case 2 -> "pt_prom";
            case 3 -> "pt_alerts";
            default -> null;
        };
        
        if (processTypeId == null) {
            System.out.println("❌ Tipo inválido");
            return;
        }
        
        System.out.print("Ciudad (ej: Buenos Aires): ");
        String city = scanner.nextLine();
        
        System.out.print("Fecha (YYYY-MM-DD) o Enter para hoy: ");
        String dateStr = scanner.nextLine().trim();
        if (dateStr.isEmpty()) {
            dateStr = java.time.LocalDate.now().toString();
        }
        
        Map<String, String> params = Map.of(
            "city", city,
            "date", dateStr
        );
        
        String requestId = processService.requestProcess(userId, processTypeId, params);
        
        if (requestId != null) {
            System.out.println("✅ Proceso solicitado con ID: " + requestId);
            System.out.println("💡 Usa la opción 3 para ejecutarlo");
        } else {
            System.out.println("❌ Error solicitando proceso (verifica permisos)");
        }
    }
    
    private void viewMyProcesses() {
        String userId = (String) currentUser.get("userId");
        
        List<Map<String, Object>> processes = processService.listUserProcesses(userId);
        
        System.out.println("\n📋 MIS PROCESOS SOLICITADOS (" + processes.size() + ")");
        System.out.println("─".repeat(80));
        
        if (processes.isEmpty()) {
            System.out.println("No tienes procesos solicitados");
            return;
        }
        
        for (Map<String, Object> proc : processes) {
            System.out.println("ID: " + proc.get("requestId"));
            System.out.println("  Tipo: " + proc.get("processId"));
            System.out.println("  Estado: " + proc.get("status"));
            System.out.println("  Solicitado: " + proc.get("requestedAt"));
            if (proc.get("completedAt") != null) {
                System.out.println("  Completado: " + proc.get("completedAt"));
            }
            System.out.println("─".repeat(80));
        }
    }
    
    private void executeProcessRequest() {
        System.out.print("ID del proceso a ejecutar: ");
        String requestId = scanner.nextLine();
        
        System.out.println("⏳ Ejecutando proceso...");
        Map<String, Object> result = processService.executeProcess(requestId);
        
        if (result.containsKey("error")) {
            System.out.println("❌ Error: " + result.get("error"));
        } else {
            System.out.println("✅ Proceso ejecutado exitosamente!");
            System.out.println("\n📊 RESULTADO:");
            result.forEach((key, value) -> System.out.println("  " + key + ": " + value));
            
            // Generar factura automáticamente
            System.out.println("\n💰 Generando factura...");
            String invoiceId = invoiceService.generateInvoiceForProcess(requestId);
            if (invoiceId != null) {
                System.out.println("✅ Factura generada: " + invoiceId);
            }
        }
    }
    
    private void viewProcessResult() {
        System.out.print("ID del proceso: ");
        String requestId = scanner.nextLine();
        
        Map<String, Object> result = processService.getProcessResult(requestId);
        
        if (result.isEmpty()) {
            System.out.println("❌ No se encontró resultado para este proceso");
        } else {
            System.out.println("\n📊 RESULTADO DEL PROCESO:");
            System.out.println("─".repeat(60));
            result.forEach((key, value) -> System.out.println("  " + key + ": " + value));
        }
    }
    
    // ============================================
    // IMPLEMENTACIÓN DE MENSAJERÍA
    // ============================================
    
    private void sendPrivateMessage() {
        String fromUserId = (String) currentUser.get("userId");
        
        System.out.print("Email del destinatario: ");
        String toEmail = scanner.nextLine().trim();
        
        System.out.print("Mensaje: ");
        String content = scanner.nextLine();
        
        boolean sent = messageService.sendPrivateMessageByEmail(fromUserId, toEmail, content);
        
        if (sent) {
            System.out.println("✅ Mensaje enviado exitosamente a " + toEmail);
        } else {
            System.out.println("❌ Error enviando mensaje (verifica que el email exista)");
        }
    }
    
    private void viewMyConversations() {
        String userId = (String) currentUser.get("userId");
        
        List<Map<String, Object>> conversations = messageService.getUserConversations(userId);
        
        System.out.println("\n💬 MIS CONVERSACIONES (" + conversations.size() + ")");
        System.out.println("─".repeat(60));
        
        if (conversations.isEmpty()) {
            System.out.println("No tienes conversaciones");
            return;
        }
        
        for (Map<String, Object> conv : conversations) {
            System.out.println("Conversación ID: " + conv.get("conversationId"));
            System.out.println("  Último mensaje: " + conv.get("lastMessage"));
            System.out.println("  No leídos: " + conv.get("unreadCount"));
            System.out.println("─".repeat(60));
        }
    }
    
    private void viewConversationMessages() {
        String currentUserId = (String) currentUser.get("userId");
        
        System.out.print("Email del otro usuario: ");
        String otherEmail = scanner.nextLine().trim();
        
        List<Map<String, Object>> messages = messageService.getConversationMessagesByEmail(currentUserId, otherEmail, 20);
        
        if (messages.isEmpty()) {
            System.out.println("\n💬 No hay mensajes con " + otherEmail);
            System.out.println("💡 Envía un mensaje primero usando la opción 1");
            return;
        }
        
        System.out.println("\n💬 CONVERSACIÓN CON " + otherEmail + " (" + messages.size() + " mensajes)");
        System.out.println("═".repeat(60));
        
        for (Map<String, Object> msg : messages) {
            String sender = (String) msg.get("sender");
            String content = (String) msg.get("content");
            
            if (sender.equals("Tú")) {
                System.out.println("👤 Tú:");
            } else {
                System.out.println("👥 " + sender + ":");
            }
            System.out.println("   " + content);
            System.out.println("─".repeat(60));
        }
    }
    
    // ============================================
    // IMPLEMENTACIÓN DE FACTURACIÓN
    // ============================================
    
    private void viewMyInvoices() {
        String userId = (String) currentUser.get("userId");
        
        List<Map<String, Object>> invoices = invoiceService.listUserInvoices(userId);
        
        System.out.println("\n💰 MIS FACTURAS (" + invoices.size() + ")");
        System.out.println("─".repeat(80));
        
        if (invoices.isEmpty()) {
            System.out.println("No tienes facturas");
            return;
        }
        
        double total = 0;
        for (Map<String, Object> inv : invoices) {
            System.out.println("ID: " + inv.get("invoiceId"));
            System.out.println("  Proceso: " + inv.get("processId"));
            System.out.println("  Monto: $" + inv.get("amount"));
            System.out.println("  Estado: " + inv.get("status"));
            System.out.println("  Fecha: " + inv.get("issuedAt"));
            System.out.println("─".repeat(80));
            
            if ("pendiente".equals(inv.get("status"))) {
                total += (Double) inv.get("amount");
            }
        }
        
        System.out.println("Total pendiente: $" + String.format("%.2f", total));
    }
    
    private void viewAccountBalance() {
        String userId = (String) currentUser.get("userId");
        
        Double balance = invoiceService.getAccountBalance(userId);
        
        System.out.println("\n💰 SALDO DE CUENTA CORRIENTE");
        System.out.println("─".repeat(60));
        System.out.println("Saldo actual: $" + String.format("%.2f", balance));
        
        if (balance < 0) {
            System.out.println("⚠️ Saldo negativo - tienes facturas pendientes");
        } else {
            System.out.println("✅ Saldo positivo");
        }
    }
    
    private void generateInvoiceForProcess() {
        // Verificar permisos de admin
        if (!isAdmin()) {
            System.out.println("❌ Solo administradores pueden generar facturas manualmente");
            return;
        }
        
        System.out.print("ID del proceso completado: ");
        String requestId = scanner.nextLine();
        
        String invoiceId = invoiceService.generateInvoiceForProcess(requestId);
        
        if (invoiceId != null) {
            System.out.println("✅ Factura generada: " + invoiceId);
        } else {
            System.out.println("❌ Error generando factura");
        }
    }
    
}
