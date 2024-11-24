package project.bit.bit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.bit.bit.model.Folder;
import project.bit.bit.model.Models;

import java.util.List;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folder, UUID> {
    List<Folder> findByModel(Models models);
}