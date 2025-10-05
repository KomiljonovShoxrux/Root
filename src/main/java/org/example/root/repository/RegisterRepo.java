package org.example.root.repository;

import org.example.root.model.Register;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegisterRepo extends JpaRepository<Register, Long> {
}
