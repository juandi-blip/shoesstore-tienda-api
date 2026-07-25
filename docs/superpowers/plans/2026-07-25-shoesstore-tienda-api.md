# shoesstore-tienda-api Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a new Spring Boot backend (`shoesstore-tienda-api`) that provides real authentication, a product catalog, and order/checkout services for the Shoes'sStore React storefront — replacing its current mocks — while every order validates and decrements stock against the existing `shoesstore-inventario-api` over HTTP.

**Architecture:** Three domain packages (`auth`, `productos`, `pedidos`) plus a `common` package for cross-cutting concerns, all on one Spring Boot app talking to its own MySQL database (`shoesstore_tienda`). A single `InventarioClient` (Spring `RestClient`) is the only place that talks to `shoesstore-inventario-api` — no other class calls it directly.

**Tech Stack:** Spring Boot 3.2.5, Java 17, Maven, Spring Data JPA + MySQL (`mysql-connector-j`), `spring-boot-starter-validation`, `spring-security-crypto` (BCrypt only — no full Spring Security), JUnit 5 + Mockito (from `spring-boot-starter-test`).

## Global Constraints

- Runs on port `8081` (inventory API keeps `8080`).
- Own database `shoesstore_tienda`, created fresh — never touches the `shoesstore` database's tables directly, only via HTTP calls to `shoesstore-inventario-api`.
- CORS allowed origin: `http://localhost:5173` (the Vite storefront), methods `GET, POST, PUT, DELETE, OPTIONS`, headers `Content-Type, Authorization`.
- Passwords stored as BCrypt hashes — never plain text (this is the one concrete improvement over the existing `auth-service`, which stores plain text).
- Session token: random `UUID` string, stored in a `sesiones` table with an expiration; sent by the client as `Authorization: Bearer <token>`.
- Public (no token required): `POST /api/auth/registro`, `POST /api/auth/login`, `GET /api/productos`, `GET /api/productos/{id}`. Everything else requires a valid, unexpired token.
- Order creation must validate stock for every line item against `shoesstore-inventario-api` **before** decrementing any of them — never partially decrement then fail (see spec's "Integración con el inventario" section).
- All code comments in Spanish (matches the rest of this SENA evidence trail); naming conventions: `camelCase` for variables/methods, `PascalCase` for classes, no abbreviations in domain names (`nombreUsuario`, not `usr`).
- `spring.datasource.password` must read from the `DB_PASSWORD` environment variable, never hardcoded in `application.properties` (same convention as `shoesstore-inventario-api`).

---

### Task 1: Scaffold the project and confirm it boots against MySQL

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/shoesstore/tienda/ShoesstoreTiendaApiApplication.java`
- Create: `src/main/resources/application.properties`
- Create: `src/main/java/com/shoesstore/tienda/config/WebConfig.java`
- Create: `.gitignore`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: a bootable Spring Boot app on port 8081, connected to MySQL `shoesstore_tienda`, with CORS configured — every later task adds controllers/services into this running app.

- [ ] **Step 1: Create the MySQL database**

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS shoesstore_tienda CHARACTER SET utf8mb4;"
```

(Enter the root password when prompted.)

- [ ] **Step 2: Write `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <groupId>com.shoesstore.tienda</groupId>
    <artifactId>shoesstore-tienda-api</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>shoesstore-tienda-api</name>
    <description>API de la tienda Shoes'sStore: autenticacion, catalogo y pedidos</description>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-crypto</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <source>17</source>
                    <target>17</target>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: Write `application.properties`**

```properties
server.port=8081

spring.datasource.url=jdbc:mysql://localhost:3306/shoesstore_tienda?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

# URL base del sistema de inventario (shoesstore-inventario-api)
inventario.api.base-url=http://localhost:8080
```

- [ ] **Step 4: Write the main application class**

```java
package com.shoesstore.tienda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ShoesstoreTiendaApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShoesstoreTiendaApiApplication.class, args);
    }
}
```

- [ ] **Step 5: Write the CORS configuration**

```java
package com.shoesstore.tienda.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Permite que el storefront React (localhost:5173) consuma esta API.
@Configuration
public class WebConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:5173")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("Content-Type", "Authorization")
                        .allowCredentials(true);
            }
        };
    }
}
```

- [ ] **Step 6: Write `.gitignore`**

```
target/
*.class
.mvn/wrapper/maven-wrapper.jar
```

- [ ] **Step 7: Boot the app and confirm it connects to MySQL**

```bash
export DB_PASSWORD='ChessDai1357#'
mvn spring-boot:run
```

Expected: starts on port 8081, log line `HikariPool-1 - Start completed`, no schema errors (there are no entities yet, so Hibernate has nothing to create — that's expected at this step).

- [ ] **Step 8: Commit**

```bash
git add pom.xml src .gitignore
git commit -m "chore: scaffold shoesstore-tienda-api Spring Boot project"
```

---

### Task 2: Auth — registro, login, perfil, logout with BCrypt and token sessions

**Files:**
- Create: `src/main/java/com/shoesstore/tienda/auth/model/Usuario.java`
- Create: `src/main/java/com/shoesstore/tienda/auth/model/Sesion.java`
- Create: `src/main/java/com/shoesstore/tienda/auth/repository/UsuarioRepository.java`
- Create: `src/main/java/com/shoesstore/tienda/auth/repository/SesionRepository.java`
- Create: `src/main/java/com/shoesstore/tienda/auth/dto/RegistroDTO.java`
- Create: `src/main/java/com/shoesstore/tienda/auth/dto/LoginDTO.java`
- Create: `src/main/java/com/shoesstore/tienda/auth/dto/SesionDTO.java`
- Create: `src/main/java/com/shoesstore/tienda/auth/dto/PerfilDTO.java`
- Create: `src/main/java/com/shoesstore/tienda/auth/AuthService.java`
- Create: `src/main/java/com/shoesstore/tienda/auth/AuthController.java`
- Create: `src/main/java/com/shoesstore/tienda/common/RespuestaDTO.java`
- Create: `src/main/java/com/shoesstore/tienda/common/ManejadorErrores.java`
- Create: `src/main/java/com/shoesstore/tienda/common/NoAutorizadoException.java`
- Create: `src/main/java/com/shoesstore/tienda/common/TokenAuthFilter.java`
- Test: `src/test/java/com/shoesstore/tienda/auth/AuthServiceTest.java`

**Interfaces:**
- Consumes: nothing from other domain packages.
- Produces: `AuthService.registrar(RegistroDTO): Sesion`, `AuthService.login(LoginDTO): Sesion`, `AuthService.obtenerUsuarioPorToken(String token): Optional<Usuario>`, `AuthService.logout(String token): void` — the `pedidos` and `productos` controllers (Tasks 3–4) call `obtenerUsuarioPorToken` (via `TokenAuthFilter`, which stores the resolved `Usuario.id` as the request attribute `"usuarioId"` of type `Long`) to know who's making the request.

- [ ] **Step 1: Write the failing test for registration hashing and duplicate rejection**

```java
package com.shoesstore.tienda.auth;

import com.shoesstore.tienda.auth.dto.LoginDTO;
import com.shoesstore.tienda.auth.dto.RegistroDTO;
import com.shoesstore.tienda.auth.model.Sesion;
import com.shoesstore.tienda.auth.model.Usuario;
import com.shoesstore.tienda.auth.repository.SesionRepository;
import com.shoesstore.tienda.auth.repository.UsuarioRepository;
import com.shoesstore.tienda.common.NoAutorizadoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UsuarioRepository usuarioRepository;
    @Mock SesionRepository sesionRepository;

    private AuthService authService;

    private AuthService crearServicio() {
        return new AuthService(usuarioRepository, sesionRepository, new BCryptPasswordEncoder());
    }

    @Test
    void registrarRechazaUsuarioDuplicado() {
        authService = crearServicio();
        when(usuarioRepository.findByNombreUsuario("juan")).thenReturn(Optional.of(new Usuario()));

        RegistroDTO dto = new RegistroDTO("juan", "clave123", "Juan Florez", "juan@correo.com");

        assertThrows(IllegalStateException.class, () -> authService.registrar(dto));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void registrarGuardaLaContrasenaComoHashNoTextoPlano() {
        authService = crearServicio();
        when(usuarioRepository.findByNombreUsuario("juan")).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sesionRepository.save(any(Sesion.class))).thenAnswer(inv -> inv.getArgument(0));

        RegistroDTO dto = new RegistroDTO("juan", "clave123", "Juan Florez", "juan@correo.com");
        authService.registrar(dto);

        var captor = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertNotEquals("clave123", captor.getValue().getContrasenaHash());
        assertTrue(new BCryptPasswordEncoder().matches("clave123", captor.getValue().getContrasenaHash()));
    }

    @Test
    void loginRechazaContrasenaIncorrecta() {
        authService = crearServicio();
        Usuario usuario = new Usuario();
        usuario.setNombreUsuario("juan");
        usuario.setContrasenaHash(new BCryptPasswordEncoder().encode("claveCorrecta"));
        when(usuarioRepository.findByNombreUsuario("juan")).thenReturn(Optional.of(usuario));

        LoginDTO dto = new LoginDTO("juan", "claveIncorrecta");

        assertThrows(NoAutorizadoException.class, () -> authService.login(dto));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails (classes don't exist yet)**

```bash
mvn -q test -Dtest=AuthServiceTest
```

Expected: compilation failure — `AuthService`, `Usuario`, `Sesion`, `RegistroDTO`, `LoginDTO`, `NoAutorizadoException` don't exist yet.

- [ ] **Step 3: Write the entities**

```java
package com.shoesstore.tienda.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// Un usuario registrado en la tienda (cliente del storefront).
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_usuario", nullable = false, unique = true, length = 60)
    private String nombreUsuario;

    @Column(name = "contrasena_hash", nullable = false, length = 100)
    private String contrasenaHash;

    @Column(name = "nombre_completo", length = 120)
    private String nombreCompleto;

    @Column(length = 120)
    private String email;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }
    public String getContrasenaHash() { return contrasenaHash; }
    public void setContrasenaHash(String contrasenaHash) { this.contrasenaHash = contrasenaHash; }
    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }
}
```

```java
package com.shoesstore.tienda.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// Una sesion activa: el "boleto" (token) que el cliente reenvia en cada peticion protegida.
@Entity
@Table(name = "sesiones")
public class Sesion {

    @Id
    @Column(length = 36)
    private String token;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "fecha_expiracion", nullable = false)
    private LocalDateTime fechaExpiracion;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaExpiracion() { return fechaExpiracion; }
    public void setFechaExpiracion(LocalDateTime fechaExpiracion) { this.fechaExpiracion = fechaExpiracion; }
}
```

- [ ] **Step 4: Write the repositories**

```java
package com.shoesstore.tienda.auth.repository;

import com.shoesstore.tienda.auth.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByNombreUsuario(String nombreUsuario);
}
```

```java
package com.shoesstore.tienda.auth.repository;

import com.shoesstore.tienda.auth.model.Sesion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SesionRepository extends JpaRepository<Sesion, String> {
}
```

- [ ] **Step 5: Write the DTOs**

```java
package com.shoesstore.tienda.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class RegistroDTO {
    @NotBlank(message = "El nombre de usuario es obligatorio.")
    private String nombreUsuario;
    @NotBlank(message = "La contrasena es obligatoria.")
    private String contrasena;
    private String nombreCompleto;
    private String email;

    public RegistroDTO() {}
    public RegistroDTO(String nombreUsuario, String contrasena, String nombreCompleto, String email) {
        this.nombreUsuario = nombreUsuario;
        this.contrasena = contrasena;
        this.nombreCompleto = nombreCompleto;
        this.email = email;
    }

    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }
    public String getContrasena() { return contrasena; }
    public void setContrasena(String contrasena) { this.contrasena = contrasena; }
    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
```

```java
package com.shoesstore.tienda.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginDTO {
    @NotBlank(message = "El nombre de usuario es obligatorio.")
    private String nombreUsuario;
    @NotBlank(message = "La contrasena es obligatoria.")
    private String contrasena;

    public LoginDTO() {}
    public LoginDTO(String nombreUsuario, String contrasena) {
        this.nombreUsuario = nombreUsuario;
        this.contrasena = contrasena;
    }

    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }
    public String getContrasena() { return contrasena; }
    public void setContrasena(String contrasena) { this.contrasena = contrasena; }
}
```

```java
package com.shoesstore.tienda.auth.dto;

// Respuesta de login/registro exitoso: el token que el cliente debe reenviar.
public class SesionDTO {
    private String token;
    private String nombreUsuario;

    public SesionDTO(String token, String nombreUsuario) {
        this.token = token;
        this.nombreUsuario = nombreUsuario;
    }

    public String getToken() { return token; }
    public String getNombreUsuario() { return nombreUsuario; }
}
```

```java
package com.shoesstore.tienda.auth.dto;

import java.time.LocalDateTime;

public class PerfilDTO {
    private String nombreUsuario;
    private String nombreCompleto;
    private String email;
    private LocalDateTime fechaRegistro;

    public PerfilDTO(String nombreUsuario, String nombreCompleto, String email, LocalDateTime fechaRegistro) {
        this.nombreUsuario = nombreUsuario;
        this.nombreCompleto = nombreCompleto;
        this.email = email;
        this.fechaRegistro = fechaRegistro;
    }

    public String getNombreUsuario() { return nombreUsuario; }
    public String getNombreCompleto() { return nombreCompleto; }
    public String getEmail() { return email; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
}
```

- [ ] **Step 6: Write the common error-handling pieces**

```java
package com.shoesstore.tienda.common;

// Envoltorio simple para mensajes de exito/error en las respuestas JSON.
public class RespuestaDTO {
    private String mensaje;

    public RespuestaDTO(String mensaje) { this.mensaje = mensaje; }
    public String getMensaje() { return mensaje; }
}
```

```java
package com.shoesstore.tienda.common;

// Se lanza cuando faltan credenciales validas o un token invalido/expirado.
public class NoAutorizadoException extends RuntimeException {
    public NoAutorizadoException(String mensaje) { super(mensaje); }
}
```

```java
package com.shoesstore.tienda.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Manejador global de errores: validaciones de campos, credenciales invalidas, stock insuficiente.
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespuestaDTO> manejarValidacion(MethodArgumentNotValidException ex) {
        String mensaje = ex.getBindingResult().getFieldError().getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new RespuestaDTO(mensaje));
    }

    @ExceptionHandler(NoAutorizadoException.class)
    public ResponseEntity<RespuestaDTO> manejarNoAutorizado(NoAutorizadoException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new RespuestaDTO(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<RespuestaDTO> manejarConflicto(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new RespuestaDTO(ex.getMessage()));
    }
}
```

Note: Task 4 (pedidos) will add a `StockInsuficienteException` class and a matching `@ExceptionHandler` method appended to this same file — not done here because that class doesn't exist yet, and referencing it now would break compilation.

- [ ] **Step 7: Write `AuthService`**

```java
package com.shoesstore.tienda.auth;

import com.shoesstore.tienda.auth.dto.LoginDTO;
import com.shoesstore.tienda.auth.dto.RegistroDTO;
import com.shoesstore.tienda.auth.model.Sesion;
import com.shoesstore.tienda.auth.model.Usuario;
import com.shoesstore.tienda.auth.repository.SesionRepository;
import com.shoesstore.tienda.auth.repository.UsuarioRepository;
import com.shoesstore.tienda.common.NoAutorizadoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

// Logica de negocio de registro, login y validacion de sesiones (tokens).
@Service
public class AuthService {

    private static final int HORAS_EXPIRACION_TOKEN = 24;

    private final UsuarioRepository usuarioRepository;
    private final SesionRepository sesionRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public AuthService(UsuarioRepository usuarioRepository, SesionRepository sesionRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.sesionRepository = sesionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Sesion registrar(RegistroDTO dto) {
        if (usuarioRepository.findByNombreUsuario(dto.getNombreUsuario()).isPresent()) {
            throw new IllegalStateException("El usuario ya existe.");
        }
        Usuario usuario = new Usuario();
        usuario.setNombreUsuario(dto.getNombreUsuario());
        usuario.setContrasenaHash(passwordEncoder.encode(dto.getContrasena()));
        usuario.setNombreCompleto(dto.getNombreCompleto());
        usuario.setEmail(dto.getEmail());
        usuario = usuarioRepository.save(usuario);
        return crearSesion(usuario);
    }

    public Sesion login(LoginDTO dto) {
        Usuario usuario = usuarioRepository.findByNombreUsuario(dto.getNombreUsuario())
                .orElseThrow(() -> new NoAutorizadoException("Usuario o contrasena incorrectos."));
        if (!passwordEncoder.matches(dto.getContrasena(), usuario.getContrasenaHash())) {
            throw new NoAutorizadoException("Usuario o contrasena incorrectos.");
        }
        return crearSesion(usuario);
    }

    public Optional<Usuario> obtenerUsuarioPorToken(String token) {
        return sesionRepository.findById(token)
                .filter(sesion -> sesion.getFechaExpiracion().isAfter(LocalDateTime.now()))
                .map(Sesion::getUsuario);
    }

    public void logout(String token) {
        sesionRepository.deleteById(token);
    }

    private Sesion crearSesion(Usuario usuario) {
        Sesion sesion = new Sesion();
        sesion.setToken(UUID.randomUUID().toString());
        sesion.setUsuario(usuario);
        sesion.setFechaExpiracion(LocalDateTime.now().plusHours(HORAS_EXPIRACION_TOKEN));
        return sesionRepository.save(sesion);
    }
}
```

- [ ] **Step 8: Run the test to verify it passes**

```bash
mvn -q test -Dtest=AuthServiceTest
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 9: Write the token filter**

```java
package com.shoesstore.tienda.common;

import com.shoesstore.tienda.auth.AuthService;
import com.shoesstore.tienda.auth.model.Usuario;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

// Intercepta cada peticion: si la ruta es publica la deja pasar, si no exige
// un Authorization: Bearer <token> valido y expone el id del usuario autenticado
// en el atributo de request "usuarioId" para que los controladores lo lean.
@Component
public class TokenAuthFilter extends OncePerRequestFilter {

    private final AuthService authService;

    public TokenAuthFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (esRutaPublica(request)) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            responderNoAutorizado(response, "Falta el token de autenticacion.");
            return;
        }

        String token = header.substring("Bearer ".length());
        Optional<Usuario> usuario = authService.obtenerUsuarioPorToken(token);
        if (usuario.isEmpty()) {
            responderNoAutorizado(response, "Token invalido o expirado.");
            return;
        }

        request.setAttribute("usuarioId", usuario.get().getId());
        chain.doFilter(request, response);
    }

    private boolean esRutaPublica(HttpServletRequest request) {
        String path = request.getRequestURI();
        String metodo = request.getMethod();
        if ("OPTIONS".equals(metodo)) return true;
        if (path.equals("/api/auth/registro") || path.equals("/api/auth/login")) return true;
        return "GET".equals(metodo) && path.startsWith("/api/productos");
    }

    private void responderNoAutorizado(HttpServletResponse response, String mensaje) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"mensaje\":\"" + mensaje + "\"}");
    }
}
```

- [ ] **Step 10: Write `AuthController`**

```java
package com.shoesstore.tienda.auth;

import com.shoesstore.tienda.auth.dto.*;
import com.shoesstore.tienda.auth.model.Sesion;
import com.shoesstore.tienda.auth.model.Usuario;
import com.shoesstore.tienda.common.NoAutorizadoException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/registro")
    public ResponseEntity<SesionDTO> registrar(@Valid @RequestBody RegistroDTO dto) {
        Sesion sesion = authService.registrar(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SesionDTO(sesion.getToken(), sesion.getUsuario().getNombreUsuario()));
    }

    @PostMapping("/login")
    public ResponseEntity<SesionDTO> login(@Valid @RequestBody LoginDTO dto) {
        Sesion sesion = authService.login(dto);
        return ResponseEntity.ok(new SesionDTO(sesion.getToken(), sesion.getUsuario().getNombreUsuario()));
    }

    @GetMapping("/perfil")
    public ResponseEntity<PerfilDTO> perfil(HttpServletRequest request) {
        Long usuarioId = (Long) request.getAttribute("usuarioId");
        Usuario usuario = authService.obtenerUsuarioPorToken(extraerToken(request))
                .orElseThrow(() -> new NoAutorizadoException("Sesion invalida."));
        return ResponseEntity.ok(new PerfilDTO(usuario.getNombreUsuario(), usuario.getNombreCompleto(),
                usuario.getEmail(), usuario.getFechaRegistro()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(extraerToken(request));
        return ResponseEntity.noContent().build();
    }

    private String extraerToken(HttpServletRequest request) {
        return request.getHeader("Authorization").substring("Bearer ".length());
    }
}
```

- [ ] **Step 11: Boot the app and manually test the auth flow with curl**

```bash
export DB_PASSWORD='ChessDai1357#'
mvn spring-boot:run
```

In another terminal:

```bash
curl -X POST http://localhost:8081/api/auth/registro -H "Content-Type: application/json" \
  -d '{"nombreUsuario":"juan","contrasena":"clave123","nombreCompleto":"Juan Florez","email":"juan@correo.com"}'
# -> 201, { "token": "...", "nombreUsuario": "juan" }

curl http://localhost:8081/api/auth/perfil -H "Authorization: Bearer <token de la respuesta anterior>"
# -> 200, datos del perfil

curl http://localhost:8081/api/auth/perfil
# -> 401, falta el token
```

- [ ] **Step 12: Commit**

```bash
git add src
git commit -m "feat: add auth (registro/login/perfil/logout) with BCrypt and token sessions"
```

---

### Task 3: Productos — catalog CRUD enriched with live inventory availability

**Files:**
- Create: `src/main/java/com/shoesstore/tienda/productos/model/Producto.java`
- Create: `src/main/java/com/shoesstore/tienda/productos/model/ProductoTalla.java`
- Create: `src/main/java/com/shoesstore/tienda/productos/repository/ProductoRepository.java`
- Create: `src/main/java/com/shoesstore/tienda/productos/repository/ProductoTallaRepository.java`
- Create: `src/main/java/com/shoesstore/tienda/productos/InventarioClient.java`
- Create: `src/main/java/com/shoesstore/tienda/productos/dto/TallaDisponibleDTO.java`
- Create: `src/main/java/com/shoesstore/tienda/productos/dto/ProductoDetalleDTO.java`
- Create: `src/main/java/com/shoesstore/tienda/productos/ProductoService.java`
- Create: `src/main/java/com/shoesstore/tienda/productos/ProductoController.java`
- Test: `src/test/java/com/shoesstore/tienda/productos/ProductoServiceTest.java`

**Interfaces:**
- Consumes: `TokenAuthFilter`'s `usuarioId` request attribute (Task 2) to protect the admin write endpoints.
- Produces: `InventarioClient.consultarStock(Long idProductoInventario): int` and `InventarioClient.descontarStock(Long idProductoInventario, int nuevoStock): void` — Task 4 (`PedidoService`) calls both of these directly.

- [ ] **Step 1: Write the failing test for availability resolution**

```java
package com.shoesstore.tienda.productos;

import com.shoesstore.tienda.productos.dto.ProductoDetalleDTO;
import com.shoesstore.tienda.productos.model.Producto;
import com.shoesstore.tienda.productos.model.ProductoTalla;
import com.shoesstore.tienda.productos.repository.ProductoRepository;
import com.shoesstore.tienda.productos.repository.ProductoTallaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock ProductoRepository productoRepository;
    @Mock ProductoTallaRepository productoTallaRepository;
    @Mock InventarioClient inventarioClient;

    @Test
    void detalleMarcaTallaComoNoDisponibleSiElInventarioNoTieneStock() {
        ProductoService service = new ProductoService(productoRepository, productoTallaRepository, inventarioClient);

        Producto producto = new Producto();
        producto.setId(1L);
        producto.setNombre("Air Force 1 '07");
        producto.setPrecio(new BigDecimal("115"));

        ProductoTalla talla9 = new ProductoTalla();
        talla9.setTalla(9.0);
        talla9.setIdProductoInventario(101L);
        ProductoTalla talla10 = new ProductoTalla();
        talla10.setTalla(10.0);
        talla10.setIdProductoInventario(102L);

        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));
        when(productoTallaRepository.findByProductoId(1L)).thenReturn(List.of(talla9, talla10));
        when(inventarioClient.consultarStock(101L)).thenReturn(0);
        when(inventarioClient.consultarStock(102L)).thenReturn(5);

        ProductoDetalleDTO detalle = service.obtenerDetalle(1L);

        assertFalse(detalle.getTallas().get(0).isDisponible());
        assertTrue(detalle.getTallas().get(1).isDisponible());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
mvn -q test -Dtest=ProductoServiceTest
```

Expected: compilation failure — none of the classes exist yet.

- [ ] **Step 3: Write the entities**

```java
package com.shoesstore.tienda.productos.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

// Un modelo de zapatilla del catalogo publico (no una unidad de inventario:
// eso vive en shoesstore-inventario-api, referenciado desde ProductoTalla).
@Entity
@Table(name = "productos")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 60)
    private String marca;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(length = 20)
    private String genero;

    @Column(length = 40)
    private String proposito;

    @Column(length = 40)
    private String subcategoria;

    @Column(length = 80)
    private String colorway;

    private boolean novedad;

    private boolean outlet;

    @Column(length = 500)
    private String imagen;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public String getGenero() { return genero; }
    public void setGenero(String genero) { this.genero = genero; }
    public String getProposito() { return proposito; }
    public void setProposito(String proposito) { this.proposito = proposito; }
    public String getSubcategoria() { return subcategoria; }
    public void setSubcategoria(String subcategoria) { this.subcategoria = subcategoria; }
    public String getColorway() { return colorway; }
    public void setColorway(String colorway) { this.colorway = colorway; }
    public boolean isNovedad() { return novedad; }
    public void setNovedad(boolean novedad) { this.novedad = novedad; }
    public boolean isOutlet() { return outlet; }
    public void setOutlet(boolean outlet) { this.outlet = outlet; }
    public String getImagen() { return imagen; }
    public void setImagen(String imagen) { this.imagen = imagen; }
}
```

```java
package com.shoesstore.tienda.productos.model;

import jakarta.persistence.*;

// Una talla concreta de un producto, ligada a su variante real en el
// sistema de inventario (shoesstore-inventario-api) via idProductoInventario.
@Entity
@Table(name = "producto_tallas")
public class ProductoTalla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private Double talla;

    @Column(name = "id_producto_inventario", nullable = false)
    private Long idProductoInventario;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) { this.producto = producto; }
    public Double getTalla() { return talla; }
    public void setTalla(Double talla) { this.talla = talla; }
    public Long getIdProductoInventario() { return idProductoInventario; }
    public void setIdProductoInventario(Long idProductoInventario) { this.idProductoInventario = idProductoInventario; }
}
```

- [ ] **Step 4: Write the repositories**

```java
package com.shoesstore.tienda.productos.repository;

import com.shoesstore.tienda.productos.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
}
```

```java
package com.shoesstore.tienda.productos.repository;

import com.shoesstore.tienda.productos.model.ProductoTalla;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductoTallaRepository extends JpaRepository<ProductoTalla, Long> {
    List<ProductoTalla> findByProductoId(Long productoId);
    Optional<ProductoTalla> findByProductoIdAndTalla(Long productoId, Double talla);
}
```

- [ ] **Step 5: Write `InventarioClient`**

```java
package com.shoesstore.tienda.productos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

// Unico punto de contacto con shoesstore-inventario-api. Nadie mas en esta
// aplicacion llama directamente a ese servicio.
@Component
public class InventarioClient {

    private final RestClient restClient;

    public InventarioClient(@Value("${inventario.api.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** Consulta el stock actual de una variante (fila) del inventario. */
    public int consultarStock(Long idProductoInventario) {
        Map<?, ?> producto = restClient.get()
                .uri("/api/productos/{id}", idProductoInventario)
                .retrieve()
                .body(Map.class);
        return producto == null ? 0 : ((Number) producto.get("stock")).intValue();
    }

    /** Actualiza el stock de una variante del inventario (descuento tras un pedido). */
    public void descontarStock(Long idProductoInventario, int nuevoStock) {
        Map<?, ?> producto = restClient.get()
                .uri("/api/productos/{id}", idProductoInventario)
                .retrieve()
                .body(Map.class);
        // Se envia el objeto completo de vuelta con el stock actualizado, porque
        // el PUT de shoesstore-inventario-api espera el recurso entero (ver su
        // ProductoController: no tiene un endpoint parcial de solo-stock).
        var actualizado = new java.util.HashMap<>(producto);
        actualizado.put("stock", nuevoStock);
        restClient.put()
                .uri("/api/productos/{id}", idProductoInventario)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(actualizado)
                .retrieve()
                .toBodilessEntity();
    }
}
```

- [ ] **Step 6: Write the DTOs**

```java
package com.shoesstore.tienda.productos.dto;

public class TallaDisponibleDTO {
    private Double talla;
    private boolean disponible;

    public TallaDisponibleDTO(Double talla, boolean disponible) {
        this.talla = talla;
        this.disponible = disponible;
    }

    public Double getTalla() { return talla; }
    public boolean isDisponible() { return disponible; }
}
```

```java
package com.shoesstore.tienda.productos.dto;

import java.math.BigDecimal;
import java.util.List;

public class ProductoDetalleDTO {
    private Long id;
    private String nombre;
    private String marca;
    private BigDecimal precio;
    private String genero;
    private String proposito;
    private String subcategoria;
    private String colorway;
    private boolean novedad;
    private boolean outlet;
    private String imagen;
    private List<TallaDisponibleDTO> tallas;

    public ProductoDetalleDTO(Long id, String nombre, String marca, BigDecimal precio, String genero,
                               String proposito, String subcategoria, String colorway, boolean novedad,
                               boolean outlet, String imagen, List<TallaDisponibleDTO> tallas) {
        this.id = id;
        this.nombre = nombre;
        this.marca = marca;
        this.precio = precio;
        this.genero = genero;
        this.proposito = proposito;
        this.subcategoria = subcategoria;
        this.colorway = colorway;
        this.novedad = novedad;
        this.outlet = outlet;
        this.imagen = imagen;
        this.tallas = tallas;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getMarca() { return marca; }
    public BigDecimal getPrecio() { return precio; }
    public String getGenero() { return genero; }
    public String getProposito() { return proposito; }
    public String getSubcategoria() { return subcategoria; }
    public String getColorway() { return colorway; }
    public boolean isNovedad() { return novedad; }
    public boolean isOutlet() { return outlet; }
    public String getImagen() { return imagen; }
    public List<TallaDisponibleDTO> getTallas() { return tallas; }
}
```

- [ ] **Step 7: Write `ProductoService`**

```java
package com.shoesstore.tienda.productos;

import com.shoesstore.tienda.productos.dto.ProductoDetalleDTO;
import com.shoesstore.tienda.productos.dto.TallaDisponibleDTO;
import com.shoesstore.tienda.productos.model.Producto;
import com.shoesstore.tienda.productos.model.ProductoTalla;
import com.shoesstore.tienda.productos.repository.ProductoRepository;
import com.shoesstore.tienda.productos.repository.ProductoTallaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final ProductoTallaRepository productoTallaRepository;
    private final InventarioClient inventarioClient;

    public ProductoService(ProductoRepository productoRepository,
                            ProductoTallaRepository productoTallaRepository,
                            InventarioClient inventarioClient) {
        this.productoRepository = productoRepository;
        this.productoTallaRepository = productoTallaRepository;
        this.inventarioClient = inventarioClient;
    }

    public List<Producto> listar(String genero, String marca, String proposito) {
        return productoRepository.findAll().stream()
                .filter(p -> genero == null || genero.equalsIgnoreCase(p.getGenero()))
                .filter(p -> marca == null || marca.equalsIgnoreCase(p.getMarca()))
                .filter(p -> proposito == null || proposito.equalsIgnoreCase(p.getProposito()))
                .collect(Collectors.toList());
    }

    public ProductoDetalleDTO obtenerDetalle(Long id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Producto no encontrado."));

        List<TallaDisponibleDTO> tallas = productoTallaRepository.findByProductoId(id).stream()
                .map(this::resolverDisponibilidad)
                .collect(Collectors.toList());

        return new ProductoDetalleDTO(producto.getId(), producto.getNombre(), producto.getMarca(),
                producto.getPrecio(), producto.getGenero(), producto.getProposito(), producto.getSubcategoria(),
                producto.getColorway(), producto.isNovedad(), producto.isOutlet(), producto.getImagen(), tallas);
    }

    private TallaDisponibleDTO resolverDisponibilidad(ProductoTalla productoTalla) {
        int stock = inventarioClient.consultarStock(productoTalla.getIdProductoInventario());
        return new TallaDisponibleDTO(productoTalla.getTalla(), stock > 0);
    }

    public Producto crear(Producto producto) { return productoRepository.save(producto); }

    public Producto actualizar(Long id, Producto datos) {
        Producto existente = productoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Producto no encontrado."));
        existente.setNombre(datos.getNombre());
        existente.setMarca(datos.getMarca());
        existente.setPrecio(datos.getPrecio());
        existente.setGenero(datos.getGenero());
        existente.setProposito(datos.getProposito());
        existente.setSubcategoria(datos.getSubcategoria());
        existente.setColorway(datos.getColorway());
        existente.setNovedad(datos.isNovedad());
        existente.setOutlet(datos.isOutlet());
        existente.setImagen(datos.getImagen());
        return productoRepository.save(existente);
    }

    public void eliminar(Long id) { productoRepository.deleteById(id); }
}
```

- [ ] **Step 8: Run the test to verify it passes**

```bash
mvn -q test -Dtest=ProductoServiceTest
```

Expected: `Tests run: 1, Failures: 0, Errors: 0`.

- [ ] **Step 9: Write `ProductoController`**

```java
package com.shoesstore.tienda.productos;

import com.shoesstore.tienda.productos.dto.ProductoDetalleDTO;
import com.shoesstore.tienda.productos.model.Producto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    public List<Producto> listar(@RequestParam(required = false) String genero,
                                  @RequestParam(required = false) String marca,
                                  @RequestParam(required = false) String proposito) {
        return productoService.listar(genero, marca, proposito);
    }

    @GetMapping("/{id}")
    public ProductoDetalleDTO obtener(@PathVariable Long id) {
        return productoService.obtenerDetalle(id);
    }

    @PostMapping
    public ResponseEntity<Producto> crear(@RequestBody Producto producto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productoService.crear(producto));
    }

    @PutMapping("/{id}")
    public Producto actualizar(@PathVariable Long id, @RequestBody Producto producto) {
        return productoService.actualizar(id, producto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        productoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 10: Boot both APIs and manually confirm the catalog resolves live availability**

Start `shoesstore-inventario-api` on 8080 first (Task 3 of the rename plan already verifies it runs), then start `shoesstore-tienda-api`:

```bash
export DB_PASSWORD='ChessDai1357#'
mvn spring-boot:run
```

```bash
curl -X POST http://localhost:8081/api/productos -H "Content-Type: application/json" \
  -d '{"nombre":"Air Force 1 '\''07","marca":"Nike","precio":115,"genero":"hombre","proposito":"lifestyle","subcategoria":"originals","colorway":"White/White","novedad":false,"outlet":false,"imagen":"https://static.sneakerjagers.com/products/660x660/185674.jpg"}'
# -> 201, anota el id devuelto para el siguiente paso manual de Task 5 (seed data)
```

- [ ] **Step 11: Commit**

```bash
git add src
git commit -m "feat: add productos catalog with live availability from shoesstore-inventario-api"
```

---

### Task 4: Pedidos — checkout that validates and decrements inventory stock

**Files:**
- Create: `src/main/java/com/shoesstore/tienda/pedidos/model/Pedido.java`
- Create: `src/main/java/com/shoesstore/tienda/pedidos/model/PedidoItem.java`
- Create: `src/main/java/com/shoesstore/tienda/pedidos/repository/PedidoRepository.java`
- Create: `src/main/java/com/shoesstore/tienda/pedidos/repository/PedidoItemRepository.java`
- Create: `src/main/java/com/shoesstore/tienda/pedidos/dto/ItemPedidoDTO.java`
- Create: `src/main/java/com/shoesstore/tienda/pedidos/dto/CrearPedidoDTO.java`
- Create: `src/main/java/com/shoesstore/tienda/common/StockInsuficienteException.java`
- Modify: `src/main/java/com/shoesstore/tienda/common/ManejadorErrores.java` (add the `StockInsuficienteException` handler now that the class exists)
- Create: `src/main/java/com/shoesstore/tienda/pedidos/PedidoService.java`
- Create: `src/main/java/com/shoesstore/tienda/pedidos/PedidoController.java`
- Test: `src/test/java/com/shoesstore/tienda/pedidos/PedidoServiceTest.java`

**Interfaces:**
- Consumes: `InventarioClient.consultarStock`/`descontarStock` (Task 3), `ProductoTallaRepository.findByProductoIdAndTalla` (Task 3), `usuarioId` request attribute (Task 2).
- Produces: `PedidoService.crearPedido(Long usuarioId, CrearPedidoDTO): Pedido` — nothing later consumes this (last domain task).

- [ ] **Step 1: Write the failing test for the validate-before-decrement rule**

```java
package com.shoesstore.tienda.pedidos;

import com.shoesstore.tienda.common.StockInsuficienteException;
import com.shoesstore.tienda.pedidos.dto.CrearPedidoDTO;
import com.shoesstore.tienda.pedidos.dto.ItemPedidoDTO;
import com.shoesstore.tienda.pedidos.repository.PedidoItemRepository;
import com.shoesstore.tienda.pedidos.repository.PedidoRepository;
import com.shoesstore.tienda.productos.InventarioClient;
import com.shoesstore.tienda.productos.model.ProductoTalla;
import com.shoesstore.tienda.productos.repository.ProductoTallaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock PedidoRepository pedidoRepository;
    @Mock PedidoItemRepository pedidoItemRepository;
    @Mock ProductoTallaRepository productoTallaRepository;
    @Mock InventarioClient inventarioClient;

    @Test
    void rechazaElPedidoCompletoSiUnaSolaLineaNoTieneStockYNoDescuentaNada() {
        PedidoService service = new PedidoService(pedidoRepository, pedidoItemRepository,
                productoTallaRepository, inventarioClient);

        ProductoTalla tallaConStock = new ProductoTalla();
        tallaConStock.setIdProductoInventario(101L);
        ProductoTalla tallaSinStock = new ProductoTalla();
        tallaSinStock.setIdProductoInventario(102L);

        when(productoTallaRepository.findByProductoIdAndTalla(1L, 9.0)).thenReturn(Optional.of(tallaConStock));
        when(productoTallaRepository.findByProductoIdAndTalla(1L, 10.0)).thenReturn(Optional.of(tallaSinStock));
        when(inventarioClient.consultarStock(101L)).thenReturn(5);
        when(inventarioClient.consultarStock(102L)).thenReturn(0);

        CrearPedidoDTO dto = new CrearPedidoDTO();
        dto.setMetodoPago("tarjeta");
        dto.setItems(List.of(
                new ItemPedidoDTO(1L, 9.0, 1, new BigDecimal("115")),
                new ItemPedidoDTO(1L, 10.0, 1, new BigDecimal("115"))
        ));

        assertThrows(StockInsuficienteException.class, () -> service.crearPedido(1L, dto));

        verify(inventarioClient, never()).descontarStock(anyLong(), anyInt());
        verify(pedidoRepository, never()).save(any());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
mvn -q test -Dtest=PedidoServiceTest
```

Expected: compilation failure — none of the pedidos classes exist yet.

- [ ] **Step 3: Write the entities**

```java
package com.shoesstore.tienda.pedidos.model;

import com.shoesstore.tienda.auth.model.Usuario;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "numero_orden", nullable = false, unique = true, length = 30)
    private String numeroOrden;

    @Column(name = "metodo_pago", nullable = false, length = 20)
    private String metodoPago;

    @Column(length = 40)
    private String banco;

    @Column(name = "total_cop", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCop;

    @Column(name = "envio_cop", nullable = false, precision = 12, scale = 2)
    private BigDecimal envioCop;

    @Column(nullable = false, length = 20)
    private String estado = "confirmado";

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<PedidoItem> items;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public String getNumeroOrden() { return numeroOrden; }
    public void setNumeroOrden(String numeroOrden) { this.numeroOrden = numeroOrden; }
    public String getMetodoPago() { return metodoPago; }
    public void setMetodoPago(String metodoPago) { this.metodoPago = metodoPago; }
    public String getBanco() { return banco; }
    public void setBanco(String banco) { this.banco = banco; }
    public BigDecimal getTotalCop() { return totalCop; }
    public void setTotalCop(BigDecimal totalCop) { this.totalCop = totalCop; }
    public BigDecimal getEnvioCop() { return envioCop; }
    public void setEnvioCop(BigDecimal envioCop) { this.envioCop = envioCop; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public List<PedidoItem> getItems() { return items; }
    public void setItems(List<PedidoItem> items) { this.items = items; }
}
```

```java
package com.shoesstore.tienda.pedidos.model;

import com.shoesstore.tienda.productos.model.Producto;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "pedido_items")
public class PedidoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private Double talla;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUnitario;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Pedido getPedido() { return pedido; }
    public void setPedido(Pedido pedido) { this.pedido = pedido; }
    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) { this.producto = producto; }
    public Double getTalla() { return talla; }
    public void setTalla(Double talla) { this.talla = talla; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }
}
```

- [ ] **Step 4: Write the repositories**

```java
package com.shoesstore.tienda.pedidos.repository;

import com.shoesstore.tienda.pedidos.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    List<Pedido> findByUsuarioId(Long usuarioId);
}
```

```java
package com.shoesstore.tienda.pedidos.repository;

import com.shoesstore.tienda.pedidos.model.PedidoItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoItemRepository extends JpaRepository<PedidoItem, Long> {
}
```

- [ ] **Step 5: Write the DTOs and the new exception**

```java
package com.shoesstore.tienda.pedidos.dto;

import java.math.BigDecimal;

public class ItemPedidoDTO {
    private Long productoId;
    private Double talla;
    private Integer cantidad;
    private BigDecimal precioUnitario;

    public ItemPedidoDTO() {}
    public ItemPedidoDTO(Long productoId, Double talla, Integer cantidad, BigDecimal precioUnitario) {
        this.productoId = productoId;
        this.talla = talla;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
    }

    public Long getProductoId() { return productoId; }
    public void setProductoId(Long productoId) { this.productoId = productoId; }
    public Double getTalla() { return talla; }
    public void setTalla(Double talla) { this.talla = talla; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }
}
```

```java
package com.shoesstore.tienda.pedidos.dto;

import java.math.BigDecimal;
import java.util.List;

public class CrearPedidoDTO {
    private String metodoPago;
    private String banco;
    private BigDecimal envioCop = BigDecimal.ZERO;
    private List<ItemPedidoDTO> items;

    public String getMetodoPago() { return metodoPago; }
    public void setMetodoPago(String metodoPago) { this.metodoPago = metodoPago; }
    public String getBanco() { return banco; }
    public void setBanco(String banco) { this.banco = banco; }
    public BigDecimal getEnvioCop() { return envioCop; }
    public void setEnvioCop(BigDecimal envioCop) { this.envioCop = envioCop; }
    public List<ItemPedidoDTO> getItems() { return items; }
    public void setItems(List<ItemPedidoDTO> items) { this.items = items; }
}
```

```java
package com.shoesstore.tienda.common;

// Se lanza cuando una o mas lineas de un pedido no tienen stock suficiente
// en shoesstore-inventario-api. El manejador global ya sabe traducirla a 409
// (ver ManejadorErrores, Task 2).
public class StockInsuficienteException extends RuntimeException {
    public StockInsuficienteException(String mensaje) { super(mensaje); }
}
```

- [ ] **Step 6: Add the missing handler to `ManejadorErrores`**

Append this method inside the existing `ManejadorErrores` class (written in Task 2, Step 6) — now that `StockInsuficienteException` exists:

```java
    @ExceptionHandler(StockInsuficienteException.class)
    public ResponseEntity<RespuestaDTO> manejarStockInsuficiente(StockInsuficienteException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new RespuestaDTO(ex.getMessage()));
    }
```

Also add `import com.shoesstore.tienda.common.StockInsuficienteException;` if the file doesn't already resolve it (it's in the same package, so no import is actually needed — skip this if the class is already visible).

- [ ] **Step 7: Write `PedidoService`**

```java
package com.shoesstore.tienda.pedidos;

import com.shoesstore.tienda.auth.model.Usuario;
import com.shoesstore.tienda.common.StockInsuficienteException;
import com.shoesstore.tienda.pedidos.dto.CrearPedidoDTO;
import com.shoesstore.tienda.pedidos.dto.ItemPedidoDTO;
import com.shoesstore.tienda.pedidos.model.Pedido;
import com.shoesstore.tienda.pedidos.model.PedidoItem;
import com.shoesstore.tienda.pedidos.repository.PedidoItemRepository;
import com.shoesstore.tienda.pedidos.repository.PedidoRepository;
import com.shoesstore.tienda.productos.InventarioClient;
import com.shoesstore.tienda.productos.model.ProductoTalla;
import com.shoesstore.tienda.productos.repository.ProductoTallaRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final PedidoItemRepository pedidoItemRepository;
    private final ProductoTallaRepository productoTallaRepository;
    private final InventarioClient inventarioClient;

    public PedidoService(PedidoRepository pedidoRepository, PedidoItemRepository pedidoItemRepository,
                          ProductoTallaRepository productoTallaRepository, InventarioClient inventarioClient) {
        this.pedidoRepository = pedidoRepository;
        this.pedidoItemRepository = pedidoItemRepository;
        this.productoTallaRepository = productoTallaRepository;
        this.inventarioClient = inventarioClient;
    }

    public Pedido crearPedido(Long usuarioId, CrearPedidoDTO dto) {
        // 1) Resuelve la variante de inventario de cada linea y valida TODO el stock
        //    antes de descontar nada (ver spec: "valida-todo-antes-de-descontar-nada").
        List<ProductoTalla> variantes = dto.getItems().stream()
                .map(item -> productoTallaRepository.findByProductoIdAndTalla(item.getProductoId(), item.getTalla())
                        .orElseThrow(() -> new NoSuchElementException("Producto/talla no encontrado.")))
                .toList();

        for (int i = 0; i < variantes.size(); i++) {
            ProductoTalla variante = variantes.get(i);
            int cantidadPedida = dto.getItems().get(i).getCantidad();
            int stockActual = inventarioClient.consultarStock(variante.getIdProductoInventario());
            if (stockActual < cantidadPedida) {
                throw new StockInsuficienteException(
                        "Stock insuficiente para la talla " + dto.getItems().get(i).getTalla() + ".");
            }
        }

        // 2) Todas las lineas tienen stock: ahora si se descuenta, una por una.
        for (int i = 0; i < variantes.size(); i++) {
            ProductoTalla variante = variantes.get(i);
            int cantidadPedida = dto.getItems().get(i).getCantidad();
            int stockActual = inventarioClient.consultarStock(variante.getIdProductoInventario());
            inventarioClient.descontarStock(variante.getIdProductoInventario(), stockActual - cantidadPedida);
        }

        // 3) Calcula el total y persiste el pedido con sus lineas.
        BigDecimal totalProductos = dto.getItems().stream()
                .map(item -> item.getPrecioUnitario().multiply(BigDecimal.valueOf(item.getCantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Pedido pedido = new Pedido();
        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);
        pedido.setUsuario(usuario);
        pedido.setNumeroOrden("SS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        pedido.setMetodoPago(dto.getMetodoPago());
        pedido.setBanco(dto.getBanco());
        pedido.setEnvioCop(dto.getEnvioCop());
        pedido.setTotalCop(totalProductos.add(dto.getEnvioCop()));
        pedido = pedidoRepository.save(pedido);

        for (ItemPedidoDTO itemDTO : dto.getItems()) {
            PedidoItem item = new PedidoItem();
            item.setPedido(pedido);
            var producto = new com.shoesstore.tienda.productos.model.Producto();
            producto.setId(itemDTO.getProductoId());
            item.setProducto(producto);
            item.setTalla(itemDTO.getTalla());
            item.setCantidad(itemDTO.getCantidad());
            item.setPrecioUnitario(itemDTO.getPrecioUnitario());
            pedidoItemRepository.save(item);
        }

        return pedido;
    }

    public List<Pedido> listarPedidosDeUsuario(Long usuarioId) {
        return pedidoRepository.findByUsuarioId(usuarioId);
    }

    public Pedido obtenerPedido(Long id) {
        return pedidoRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Pedido no encontrado."));
    }
}
```

- [ ] **Step 8: Run the test to verify it passes**

```bash
mvn -q test -Dtest=PedidoServiceTest
```

Expected: `Tests run: 1, Failures: 0, Errors: 0`.

- [ ] **Step 9: Write `PedidoController`**

```java
package com.shoesstore.tienda.pedidos;

import com.shoesstore.tienda.pedidos.dto.CrearPedidoDTO;
import com.shoesstore.tienda.pedidos.model.Pedido;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    public ResponseEntity<Pedido> crear(HttpServletRequest request, @RequestBody CrearPedidoDTO dto) {
        Long usuarioId = (Long) request.getAttribute("usuarioId");
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.crearPedido(usuarioId, dto));
    }

    @GetMapping
    public List<Pedido> listar(HttpServletRequest request) {
        Long usuarioId = (Long) request.getAttribute("usuarioId");
        return pedidoService.listarPedidosDeUsuario(usuarioId);
    }

    @GetMapping("/{id}")
    public Pedido obtener(@PathVariable Long id) {
        return pedidoService.obtenerPedido(id);
    }
}
```

- [ ] **Step 10: Boot both APIs and manually confirm stock drops cross-service**

With `shoesstore-inventario-api` (8080) and `shoesstore-tienda-api` (8081) running and a registered user's token from Task 2:

```bash
# Anota el stock actual del producto de inventario ligado a la talla que vas a pedir
curl http://localhost:8080/api/productos/101

curl -X POST http://localhost:8081/api/pedidos -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"metodoPago":"contraentrega","envioCop":0,"items":[{"productoId":1,"talla":9.0,"cantidad":1,"precioUnitario":115}]}'
# -> 201, pedido creado

curl http://localhost:8080/api/productos/101
# -> el stock bajo en 1 respecto al primer curl
```

- [ ] **Step 11: Commit**

```bash
git add src
git commit -m "feat: add pedidos with validate-then-decrement stock check against shoesstore-inventario-api"
```

---

### Task 5: Seed data linking both databases

**Files:**
- Create: `src/main/resources/data-seed.sql` (documentation of the exact SQL run — not auto-executed by Spring, run manually so both databases seed in the right order)

**Interfaces:**
- Consumes: the running `shoesstore-inventario-api` (Task 3 of the rename plan) and `shoesstore-tienda-api` (Tasks 1–4 of this plan).
- Produces: ~10 seeded catalog products in `shoesstore_tienda`, each `producto_tallas` row pointing at a real `shoesstore.producto` row — this is what Task 6's Postman collection and the evidence video exercise.

- [ ] **Step 1: Insert the inventory-side rows (one per model+talla+color) into `shoesstore`**

```sql
-- Ejecutar contra la base `shoesstore` (shoesstore-inventario-api).
-- Reutiliza una categoria/proveedor existentes (ajusta los IDs a los que ya tengas).
INSERT INTO producto (NOMBRE, DESCRIPCION, PRECIO, STOCK, TALLA, COLOR, IMAGEN_URL, ID_CATEGORIA, ID_PROVEEDOR)
VALUES
  ('Air Force 1 ''07', 'Nike Air Force 1 07', 115.00, 8, '9', 'White/White', 'https://static.sneakerjagers.com/products/660x660/185674.jpg', 1, 1),
  ('Air Force 1 ''07', 'Nike Air Force 1 07', 115.00, 0, '10', 'White/White', 'https://static.sneakerjagers.com/products/660x660/185674.jpg', 1, 1);
-- Anota los ID_PRODUCTO generados (ej. 101, 102) para el paso siguiente.
```

- [ ] **Step 2: Record the generated IDs**

```bash
curl http://localhost:8080/api/productos | grep -A2 "Air Force"
```

Note the two `idProducto` values (they map to `id_producto_inventario` in the next step).

- [ ] **Step 3: Insert the catalog-side rows into `shoesstore_tienda` via the API**

```bash
curl -X POST http://localhost:8081/api/productos -H "Content-Type: application/json" \
  -d '{"nombre":"Air Force 1 '\''07","marca":"Nike","precio":115,"genero":"hombre","proposito":"lifestyle","subcategoria":"originals","colorway":"White/White","novedad":false,"outlet":false,"imagen":"https://static.sneakerjagers.com/products/660x660/185674.jpg"}'
# -> anota el id devuelto (ej. 1)
```

```sql
-- Ejecutar contra `shoesstore_tienda`, usando el id de producto devuelto arriba
-- y los ID_PRODUCTO del inventario anotados en el Paso 2.
INSERT INTO producto_tallas (producto_id, talla, id_producto_inventario) VALUES
  (1, 9.0, 101),
  (1, 10.0, 102);
```

- [ ] **Step 4: Repeat Steps 1–3 for at least 9 more products from `productos.json`**

Use the same pattern (2 tallas per model is enough to demonstrate one available and one out-of-stock size), picking different models/marcas/colorways from `shoes'sStore 2.0/src/data/productos.json` for visual variety in the demo.

- [ ] **Step 5: Save the exact SQL used as documentation**

Write every `INSERT` statement actually run (with the real generated IDs) into `src/main/resources/data-seed.sql`, so the evidence video/document can show exactly how the two databases were linked.

- [ ] **Step 6: Verify the storefront-style detail call resolves availability correctly**

```bash
curl http://localhost:8081/api/productos/1
```

Expected: `tallas` array shows `{"talla":9.0,"disponible":true}` and `{"talla":10.0,"disponible":false}` (matching the stock 8/0 seeded in Step 1).

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/data-seed.sql
git commit -m "docs: record seed data linking shoesstore_tienda catalog to shoesstore inventory"
```

---

### Task 6: Postman collection and evidence documentation

**Files:**
- Create: `postman/shoesstore-tienda-api.postman_collection.json`
- Create: `ENDPOINTS.md`
- Create: `enlace_repositorio.txt`

**Interfaces:**
- Consumes: every endpoint from Tasks 2–4.
- Produces: the deliverables listed in the spec's "Entrega de la evidencia" section — nothing further in this plan depends on it.

- [ ] **Step 1: Create the GitHub repository and push**

```bash
cd "D:/juandiplay/cursito html/sena/shoesstore-tienda-api"
gh repo create juandi-blip/shoesstore-tienda-api --public --source=. --remote=origin
git push -u origin main
```

- [ ] **Step 2: Write `enlace_repositorio.txt`**

```
Repositorio: https://github.com/juandi-blip/shoesstore-tienda-api
```

- [ ] **Step 3: Write `ENDPOINTS.md`**

```markdown
# Endpoints — shoesstore-tienda-api (puerto 8081)

## Auth
- POST /api/auth/registro — público
- POST /api/auth/login — público
- GET /api/auth/perfil — requiere token
- POST /api/auth/logout — requiere token

## Productos
- GET /api/productos?genero=&marca=&proposito= — público
- GET /api/productos/{id} — público (incluye disponibilidad resuelta contra shoesstore-inventario-api)
- POST /api/productos — requiere token
- PUT /api/productos/{id} — requiere token
- DELETE /api/productos/{id} — requiere token

## Pedidos
- POST /api/pedidos — requiere token (valida y descuenta stock en shoesstore-inventario-api)
- GET /api/pedidos — requiere token
- GET /api/pedidos/{id} — requiere token
```

- [ ] **Step 4: Build the Postman collection**

In Postman, create a collection `shoesstore-tienda-api` with one folder per domain (Auth, Productos, Pedidos), one request per endpoint above, using `{{base_url}}` (`http://localhost:8081`) and a collection variable `{{token}}` set from the login response (Postman test script: `pm.collectionVariables.set("token", pm.response.json().token)` on the login request). Export it to `postman/shoesstore-tienda-api.postman_collection.json`.

- [ ] **Step 5: Commit**

```bash
git add postman ENDPOINTS.md enlace_repositorio.txt
git commit -m "docs: add Postman collection and endpoint reference for shoesstore-tienda-api"
git push
```
