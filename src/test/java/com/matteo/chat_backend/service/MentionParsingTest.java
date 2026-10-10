package com.matteo.chat_backend.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MentionParsingTest {
    @Test void matchesNamesLiterallyWithUnicodeAndBoundaries() {
        assertTrue(ChannelActivityService.containsMention("Hola @ANA MARÍA, mira esto", "Ana María"));
        assertTrue(ChannelActivityService.containsMention("(@ana.test)", "ana.test"));
        assertFalse(ChannelActivityService.containsMention("correo@ana.test", "ana.test"));
        assertFalse(ChannelActivityService.containsMention("@anabel", "ana"));
        assertFalse(ChannelActivityService.containsMention("@@ana", "ana"));
        assertFalse(ChannelActivityService.containsMention("@ana.test", "ana"));
    }
    @Test void overlappingNamesOnlyNotifyTheChosenFullName() {
        var shortName = java.util.UUID.randomUUID();
        var fullName = java.util.UUID.randomUUID();
        var names = java.util.Map.of(shortName, "Ana", fullName, "Ana María");
        assertEquals(java.util.Set.of(fullName), ChannelActivityService.resolveMentions("Hola @Ana María", names));
        assertEquals(java.util.Set.of(shortName, fullName), ChannelActivityService.resolveMentions("@Ana María y @Ana, hola", names));
    }
}
