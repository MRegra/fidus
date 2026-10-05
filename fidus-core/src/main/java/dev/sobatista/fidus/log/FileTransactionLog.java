package dev.sobatista.fidus.log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A durable write-ahead log: each entry is a length-prefixed serialized record, flushed and fsync'd before
 * {@link #append} returns. {@link #replay} stops cleanly at a torn record at the tail — the signature of a crash
 * mid-write — so recovery never trips over a half-written entry.
 *
 * <p>Deserialization here is of our own {@link LogEntry} types from a local file we wrote; it is not a remote
 * input channel.
 */
public final class FileTransactionLog implements TransactionLog {
    private final Path file;
    private final FileOutputStream fos;
    private final DataOutputStream out;

    public FileTransactionLog(Path file) throws IOException {
        this.file = file;
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.fos = new FileOutputStream(file.toFile(), true);
        this.out = new DataOutputStream(new BufferedOutputStream(fos));
    }

    @Override
    public synchronized void append(LogEntry entry) {
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(buf)) {
                oos.writeObject(entry);
            }
            byte[] bytes = buf.toByteArray();
            out.writeInt(bytes.length);
            out.write(bytes);
            out.flush();
            fos.getFD().sync();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to append to log", e);
        }
    }

    @Override
    public List<LogEntry> replay() {
        List<LogEntry> entries = new ArrayList<>();
        if (!Files.exists(file)) {
            return entries;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            while (true) {
                int len;
                try {
                    len = in.readInt();
                } catch (EOFException end) {
                    break;
                }
                byte[] bytes = in.readNBytes(len);
                if (bytes.length < len) {
                    break; // torn write at the tail: a crash mid-append — ignore the partial record
                }
                try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                    entries.add((LogEntry) ois.readObject());
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("corrupt log entry", e);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to replay log", e);
        }
        return entries;
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
