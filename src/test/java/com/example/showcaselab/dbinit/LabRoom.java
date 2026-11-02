package com.example.showcaselab.dbinit;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Encja wyłącznie dla laboratorium inicjalizacji bazy - nie ma migracji Liquibase. */
@Entity(name = "LabRoom")
@Table(name = "lab_room")
public class LabRoom {

    @Id
    private Long id;

    private String name;

    protected LabRoom() {}

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
