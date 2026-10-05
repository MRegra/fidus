package dev.sobatista.fidus.node;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BftDemoTest {
    @Test
    void demoAgreesOnTheTransferDespiteAByzantineReplica() {
        assertEquals("bob", BftDemo.run(), "the cluster transfers G1 to bob with one replica silent");
    }
}
