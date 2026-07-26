package com.shoesstore.tienda.auth.repository;

import com.shoesstore.tienda.auth.model.Sesion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SesionRepository extends JpaRepository<Sesion, String> {
}
