package project.bit.bit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.bit.bit.model.Model;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModelRepository extends JpaRepository<Model, UUID> {

    @Query("SELECT DISTINCT m FROM Model m LEFT JOIN FETCH m.files")
    List<Model> findAllWithFiles();

    @Query("SELECT m FROM Model m LEFT JOIN FETCH m.files WHERE m.id = :id")
    Optional<Model> findByIdWithFiles(@Param("id") UUID id);

    @Query("SELECT m FROM Model m LEFT JOIN FETCH m.files f WHERE m.id = :id AND SIZE(f) > 0")
    Optional<Model> findModelForTraining(@Param("id") UUID id);

    @Query("SELECT m FROM Model m LEFT JOIN FETCH m.files f WHERE m.id = :id AND SIZE(f) > 0")
    Optional<Model> findModelForInference(@Param("id") UUID id);

    boolean existsById(UUID id);
}
