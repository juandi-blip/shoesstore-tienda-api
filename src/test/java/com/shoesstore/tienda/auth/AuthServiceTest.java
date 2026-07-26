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
