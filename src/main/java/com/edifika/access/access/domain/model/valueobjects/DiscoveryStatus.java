package com.edifika.access.access.domain.model.valueobjects;

/**
 * Estado de un nodo que un gateway identificó sin estar registrado.
 */
public enum DiscoveryStatus {
    /** Se anunció y espera que un administrador lo adopte; mientras tanto está inerte. */
    PENDING,
    /** Se creó el dispositivo a partir de él. */
    ADOPTED,
    /** El administrador decidió no adoptarlo; deja de listarse como pendiente. */
    IGNORED
}
