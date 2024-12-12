package project.bit.bit.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

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
}