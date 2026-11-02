package com.example.showcaselab.di.scan;

import org.springframework.stereotype.Service;

/** Stereotyp @Service: bean warstwy przypadków użycia; znaczenie dla czytelnika, nie dla kontenera. */
@Service
public record ScannedService(ScannedRepository repository) {}
