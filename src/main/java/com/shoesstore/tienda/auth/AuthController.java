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
