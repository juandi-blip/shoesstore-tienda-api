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
