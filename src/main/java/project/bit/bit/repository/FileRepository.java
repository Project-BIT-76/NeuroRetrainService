package project.bit.bit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.bit.bit.model.File;
import project.bit.bit.model.Folder;

import java.util.List;
import java.util.UUID;

public interface FileRepository extends JpaRepository<File, UUID> {
    List<File> findByFolder(Folder folder);
}