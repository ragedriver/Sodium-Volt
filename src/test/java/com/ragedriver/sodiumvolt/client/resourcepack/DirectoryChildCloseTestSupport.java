package com.ragedriver.sodiumvolt.client.resourcepack;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;

public class DirectoryChildCloseTestSupport {
    public static void run() throws Exception {
        closed = new int[3];
        channelCloses = 0;
        main(new String[0]);
    }

    static int[] closed = new int[3];
    static int channelCloses;
    static SeekableByteChannel raw;
    static Path file;

    @SuppressWarnings("unchecked")
    static SecureDirectoryStream<Path> directory(int depth) {
        return (SecureDirectoryStream<Path>) Proxy.newProxyInstance(DirectoryChildCloseTestSupport.class.getClassLoader(), new Class[]{SecureDirectoryStream.class}, (p, m, a) -> {
            switch (m.getName()) {
                case "newDirectoryStream":
                    return directory(depth + 1);
                case "close":
                    closed[depth]++;
                    if (depth == 2) throw new IOException("injected child close failure");
                    return null;
                case "newByteChannel":
                    raw = Files.newByteChannel(file);
                    return new SeekableByteChannel() {
                        public int read(ByteBuffer b) throws IOException {
                            return raw.read(b);
                        }

                        public int write(ByteBuffer b) throws IOException {
                            return raw.write(b);
                        }

                        public long position() throws IOException {
                            return raw.position();
                        }

                        public SeekableByteChannel position(long p) throws IOException {
                            raw.position(p);
                            return this;
                        }

                        public long size() throws IOException {
                            return raw.size();
                        }

                        public SeekableByteChannel truncate(long s) throws IOException {
                            raw.truncate(s);
                            return this;
                        }

                        public boolean isOpen() {
                            return raw.isOpen();
                        }

                        public void close() throws IOException {
                            channelCloses++;
                            raw.close();
                        }
                    };
            }
            throw new UnsupportedOperationException(m.getName());
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("shield-child-close-");
        Files.createDirectories(root.resolve("a/b"));
        file = root.resolve("a/b/test.json");
        Files.writeString(file, "{}");
        try {
            Object cache = DirectoryCandidateValidator.newCache(List.of(root), 8);
            Class<?> plan = Class.forName("com.ragedriver.sodiumvolt.client.resourcepack.DirectoryCandidateValidator$CandidatePlan");
            var c = plan.getDeclaredConstructor(List.class, List.class);
            c.setAccessible(true);
            Object candidate = c.newInstance(List.of(root), List.of(root.resolve("a"), root.resolve("a/b"), file));
            var open = cache.getClass().getDeclaredMethod("openCandidateSecure", plan, SecureDirectoryStream.class);
            open.setAccessible(true);
            try {
                open.invoke(cache, candidate, directory(0));
                throw new AssertionError("Expected cleanup failure while opening a resource");
            } catch (InvocationTargetException e) {
                if (!(e.getCause() instanceof IOException)) throw e;
            }
            if (closed[1] != 1 || closed[2] != 1 || channelCloses != 1)
                throw new AssertionError("Failed cleanup must release both child directories and the unreturned file stream");
            if (raw != null) raw.close();
            closed = new int[3];
            Class<?> expected = Class.forName("com.ragedriver.sodiumvolt.client.resourcepack.DirectoryCandidateValidator$ExpectedType");
            var validate = cache.getClass().getDeclaredMethod("validateCandidateSecure", plan, SecureDirectoryStream.class, expected);
            validate.setAccessible(true);
            try {
                validate.invoke(cache, candidate, directory(0), Enum.valueOf((Class) expected, "REGULAR_FILE"));
                throw new AssertionError("Expected cleanup failure while validating a resource");
            } catch (InvocationTargetException e) {
                if (!(e.getCause() instanceof IOException)) throw e;
            }
            if (closed[1] != 1 || closed[2] != 1)
                throw new AssertionError("Validation cleanup must attempt every opened child directory");
            closed = new int[3];
            IOException primary = new IOException("injected open operation failure");
            Object failing = DirectoryCandidateValidator.newCacheForTesting(List.of(root), 8, new DirectoryCandidateValidator.SecureOpenHook() {
                public void beforeFinalOpen() throws IOException {
                    throw primary;
                }
            });
            try {
                open.invoke(failing, candidate, directory(0));
                throw new AssertionError("Expected operation failure");
            } catch (InvocationTargetException e) {
                if (e.getCause() != primary || primary.getSuppressed().length != 1)
                    throw new AssertionError("The original operation failure must retain cleanup failures as suppressed", e);
            }
            if (closed[1] != 1 || closed[2] != 1)
                throw new AssertionError("Operation failure must still release every child directory");
        } finally {
            if (raw != null) raw.close();
            try (var files = Files.walk(root)) {
                for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
    }
}
