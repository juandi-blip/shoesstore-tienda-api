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
