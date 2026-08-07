package com.shoesstore.tienda.imagenes;

import org.springframework.stereotype.Service;

// Genera imagenes de producto propias (SVG determinista) para no depender
// de CDNs de terceros con derechos de autor. Ver docs/superpowers/specs/
// 2026-08-07-frontend-backend-integration-design.md.
@Service
public class ImagenService {

    public String generarSvg(String marca, String nombre) {
        String marcaSegura = escapar(esVacio(marca) ? "SHOES.STORE" : marca.toUpperCase());
        String nombreSeguro = escapar(esVacio(nombre) ? "Imagen no disponible" : nombre);
        return "<svg xmlns='http://www.w3.org/2000/svg' width='660' height='660' viewBox='0 0 660 660'>"
                + "<rect width='660' height='660' fill='#161616'/>"
                + "<rect x='24' y='24' width='612' height='612' rx='24' fill='none' stroke='#2a2a2a' stroke-width='2'/>"
                + "<text x='50%' y='45%' text-anchor='middle' fill='#3d3d3d' font-family='Poppins,system-ui,sans-serif' font-size='52' font-weight='700' letter-spacing='6'>" + marcaSegura + "</text>"
                + "<text x='50%' y='54%' text-anchor='middle' fill='#565656' font-family='Poppins,system-ui,sans-serif' font-size='26' font-weight='500'>" + nombreSeguro + "</text>"
                + "<text x='50%' y='63%' text-anchor='middle' fill='#3d3d3d' font-family='Poppins,system-ui,sans-serif' font-size='16'>Imagen no disponible</text>"
                + "</svg>";
    }

    private boolean esVacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private String escapar(String texto) {
        return texto
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("'", "&apos;")
                .replace("\"", "&quot;");
    }
}
