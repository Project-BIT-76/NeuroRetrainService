package project.bit.bit.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Table(name = "models")
public class Model {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "input_description", columnDefinition = "TEXT")
    private String inputDescription;

    @Column(name = "output_description", columnDefinition = "TEXT")
    private String outputDescription;

    @Column(name = "model_class_file_path")
    private String modelClassFilePath;

    @Column(name = "model_file_path")
    private String modelFilePath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @JsonIgnore
    @OneToMany(mappedBy = "model", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ModelFile> files = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void addFile(ModelFile file) {
        files.add(file);
        file.setModel(this);
    }

    public void removeFile(ModelFile file) {
        files.remove(file);
        file.setModel(null);
    }
}