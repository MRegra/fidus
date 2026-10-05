package dev.sobatista.fidus.crypto;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Builds canonical, unambiguous byte encodings for signing.
 *
 * <p>Fields are joined with a unit-separator (0x1F) so that {@code of("a","bc")} and {@code of("ab","c")}
 * never produce the same bytes — a real signing pitfall. Whatever a message decides to sign, it signs
 * <em>exactly</em> those bytes, so the signature covers every field that matters (including sequence numbers).
 */
public final class Bytes {
    private static final int SEP = 0x1F;

    private Bytes() {}

    public static byte[] of(Object... fields) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Object f : fields) {
            byte[] b = String.valueOf(f).getBytes(StandardCharsets.UTF_8);
            out.write(b, 0, b.length);
            out.write(SEP);
        }
        return out.toByteArray();
    }
}
