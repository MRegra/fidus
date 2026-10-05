package dev.sobatista.fidus.cluster;

/**
 * Quorum arithmetic for a Byzantine system of {@code n} replicas tolerating {@code f} faults, with {@code n = 3f + 1}.
 *
 * <p>These thresholds are the whole game:
 * <ul>
 *   <li>{@link #echo()} — more than {@code (n+f)/2} matching ECHOs before a replica is willing to say READY;
 *   <li>{@link #amplify()} — {@code f+1} READYs (at least one from a correct replica) is enough to join in and send
 *       READY yourself, so a value one correct replica is ready for reaches them all;
 *   <li>{@link #deliver()} — {@code 2f+1} READYs before delivering, guaranteeing a correct majority stands behind it;
 *   <li>{@link #read()} — {@code 2f+1} matching signed values form a read quorum that no {@code f} liars can fake.
 * </ul>
 */
public record Quorums(int n, int f) {
    public Quorums {
        if (n < 3 * f + 1) {
            throw new IllegalArgumentException("need n >= 3f+1 (n=" + n + ", f=" + f + ")");
        }
    }

    /** The largest f a system of n replicas can tolerate: floor((n-1)/3). */
    public static Quorums forReplicas(int n) {
        return new Quorums(n, (n - 1) / 3);
    }

    public int echo() {
        return Math.floorDiv(n + f, 2) + 1;
    }

    public int amplify() {
        return f + 1;
    }

    public int deliver() {
        return 2 * f + 1;
    }

    public int read() {
        return 2 * f + 1;
    }
}
