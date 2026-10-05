package dev.sobatista.fidus.node;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A hashcash-style proof of work that makes flooding the notary expensive: before a write is accepted, the client
 * must find a nonce whose hash over the request has {@code difficulty} leading zero bits. Minting costs the client
 * ~2^difficulty hashes; <b>checking is one hash</b>, and the server does that check itself — the server-side
 * validation the original project was missing.
 *
 * <p>The proof is bound to the exact request bytes, so a stamp cannot be lifted onto a different request.
 */
public final class ProofOfWork {

    /** A minted proof: the nonce that solves the puzzle, and the difficulty it was solved at. */
    public record Stamp(long nonce, int difficulty) implements Serializable {}

    private ProofOfWork() {}

    /** Find a stamp for {@code request} at the given difficulty. */
    public static Stamp mint(byte[] request, int difficulty) {
        long nonce = 0;
        while (!solved(request, nonce, difficulty)) {
            nonce++;
        }
        return new Stamp(nonce, difficulty);
    }

    /** Server-side check: the stamp must meet the minimum difficulty and actually solve the puzzle for this request. */
    public static boolean validate(byte[] request, Stamp stamp, int minDifficulty) {
        return stamp != null
                && stamp.difficulty() >= minDifficulty
                && solved(request, stamp.nonce(), stamp.difficulty());
    }

    private static boolean solved(byte[] request, long nonce, int difficulty) {
        return leadingZeroBits(hash(request, nonce)) >= difficulty;
    }

    private static byte[] hash(byte[] request, long nonce) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(request);
            md.update(("|pow|" + nonce).getBytes(StandardCharsets.UTF_8));
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int leadingZeroBits(byte[] h) {
        int bits = 0;
        for (byte b : h) {
            if (b == 0) {
                bits += 8;
            } else {
                bits += Integer.numberOfLeadingZeros(b & 0xFF) - 24;
                break;
            }
        }
        return bits;
    }
}
