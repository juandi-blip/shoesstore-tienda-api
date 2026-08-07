package com.shoesstore.tienda.imagenes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImagenServiceTest {

    private final ImagenService service = new ImagenService();

    @Test
    void incluyeMarcaEnMayusculasYNombreTalCual() {
        String svg = service.generarSvg("Nike", "Air Force 1 '07");
        assertTrue(svg.contains("NIKE"));
        assertTrue(svg.contains("Air Force 1 &apos;07"));
    }

    @Test
    void usaValoresPorDefectoCuandoMarcaYNombreSonNulos() {
        String svg = service.generarSvg(null, null);
        assertTrue(svg.contains("SHOES.STORE"));
        assertTrue(svg.contains("Imagen no disponible"));
    }

    @Test
    void usaValoresPorDefectoCuandoMarcaYNombreEstanVacios() {
        String svg = service.generarSvg("  ", "");
        assertTrue(svg.contains("SHOES.STORE"));
    }

    @Test
    void escapaCaracteresEspecialesXml() {
        String svg = service.generarSvg("A&B", "<script>alert('x')</script>");
        assertTrue(svg.contains("A&amp;B"));
        assertTrue(svg.contains("&lt;script&gt;"));
        assertFalse(svg.contains("<script>"));
    }

    @Test
    void produceMarcadoSvgValidoQueEmpiezaYTerminaCorrectamente() {
        String svg = service.generarSvg("Adidas", "Superstar");
        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.endsWith("</svg>"));
    }
}
