package project.bit.bit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.bit.bit.model.Models;

import java.util.UUID;

public interface ModelRepository extends JpaRepository<Models, UUID> {
}