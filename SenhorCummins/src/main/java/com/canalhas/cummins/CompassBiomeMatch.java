package com.canalhas.cummins;

/**
 * Representa o resultado de um scan de bioma a longa distância nas 8 direções cardeais.
 * Definido como classe pública top-level para evitar quaisquer falhas de classloading dinâmico no servidor.
 */
public class CompassBiomeMatch {
    public final String directionPt;
    public final String biomeNamePt;

    public CompassBiomeMatch(String directionPt, String biomeNamePt) {
        this.directionPt = directionPt;
        this.biomeNamePt = biomeNamePt;
    }
}
