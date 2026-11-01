package com.ragedriver.sodiumvolt.client.resourcepack;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.file.spi.FileSystemProvider;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class DirectoryPinnedRootFailureTestSupport {
    static boolean runtimeFailure;

    public static void run() throws Exception {
        for (boolean runtime : new boolean[]{false, true}) {
            runtimeFailure = runtime;
            closes.set(0);
            main(new String[0]);
        }
    }

    static final AtomicInteger closes = new AtomicInteger();
    static DirectoryStream<Path> raw;

    static class FS extends FileSystem {
        Provider p = new Provider();

        public FileSystemProvider provider() {
            return p;
        }

        public void close() {
        }

        public boolean isOpen() {
            return true;
        }

        public boolean isReadOnly() {
            return false;
        }

        public String getSeparator() {
            return "/";
        }

        public Iterable<Path> getRootDirectories() {
            return List.of();
        }

        public Iterable<FileStore> getFileStores() {
            return List.of();
        }

        public Set<String> supportedFileAttributeViews() {
            return Set.of("basic");
        }

        public Path getPath(String f, String... m) {
            throw new UnsupportedOperationException();
        }

        public PathMatcher getPathMatcher(String x) {
            throw new UnsupportedOperationException();
        }

        public UserPrincipalLookupService getUserPrincipalLookupService() {
            throw new UnsupportedOperationException();
        }

        public WatchService newWatchService() {
            throw new UnsupportedOperationException();
        }
    }

    static Path real;
    static final FS fs = new FS();

    static class Provider extends FileSystemProvider {
        public String getScheme() {
            return "quality-fault";
        }

        public FileSystem newFileSystem(URI u, Map<String, ?> e) {
            return fs;
        }

        public FileSystem getFileSystem(URI u) {
            return fs;
        }

        public Path getPath(URI u) {
            throw new UnsupportedOperationException();
        }

        public SeekableByteChannel newByteChannel(Path p, Set<? extends OpenOption> o, FileAttribute<?>... a) {
            throw new UnsupportedOperationException();
        }

        @SuppressWarnings("unchecked")
        public DirectoryStream<Path> newDirectoryStream(Path p, DirectoryStream.Filter<? super Path> f) throws IOException {
            raw = Files.newDirectoryStream(real);
            return (SecureDirectoryStream<Path>) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{SecureDirectoryStream.class}, (proxy, m, args) -> {
                if (m.getName().equals("close")) {
                    closes.incrementAndGet();
                    raw.close();
                    return null;
                }
                if (m.getName().equals("getFileAttributeView")) {
                    return new BasicFileAttributeView() {
                        public String name() {
                            return "basic";
                        }

                        public BasicFileAttributes readAttributes() throws IOException {
                            if (runtimeFailure) throw new IllegalStateException("injected pinned attribute failure");
                            throw new IOException("injected pinned attribute failure");
                        }

                        public void setTimes(FileTime a, FileTime b, FileTime c) {
                        }
                    };
                }
                throw new UnsupportedOperationException(m.getName());
            });
        }

        public void createDirectory(Path p, FileAttribute<?>... a) {
            throw new UnsupportedOperationException();
        }

        public void delete(Path p) {
            throw new UnsupportedOperationException();
        }

        public void copy(Path a, Path b, CopyOption... o) {
            throw new UnsupportedOperationException();
        }

        public void move(Path a, Path b, CopyOption... o) {
            throw new UnsupportedOperationException();
        }

        public boolean isSameFile(Path a, Path b) {
            return false;
        }

        public boolean isHidden(Path p) {
            return false;
        }

        public FileStore getFileStore(Path p) {
            throw new UnsupportedOperationException();
        }

        public void checkAccess(Path p, AccessMode... m) {
        }

        public <V extends FileAttributeView> V getFileAttributeView(Path p, Class<V> t, LinkOption... o) {
            return Files.getFileAttributeView(real, t, o);
        }

        public <A extends BasicFileAttributes> A readAttributes(Path p, Class<A> t, LinkOption... o) throws IOException {
            return Files.readAttributes(real, t, o);
        }

        public Map<String, Object> readAttributes(Path p, String t, LinkOption... o) throws IOException {
            return Files.readAttributes(real, t, o);
        }

        public void setAttribute(Path p, String a, Object v, LinkOption... o) {
            throw new UnsupportedOperationException();
        }
    }

    public static void main(String[] args) throws Exception {
        real = Files.createTempDirectory("shield-quality-fault-");
        Path wrapped = (Path) java.lang.reflect.Proxy.newProxyInstance(DirectoryPinnedRootFailureTestSupport.class.getClassLoader(), new Class[]{Path.class}, (proxy, m, a) -> {
            if (m.getName().equals("getFileSystem")) return fs;
            return m.invoke(real, a);
        });
        Class<?> attrs = Class.forName("com.ragedriver.sodiumvolt.client.resourcepack.DirectoryCandidateValidator$OperationAttributes");
        var ctor = attrs.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object op = ctor.newInstance();
        var begin = attrs.getDeclaredMethod("begin", Path.class, DirectoryCandidateValidator.SecureOpenHook.class);
        begin.setAccessible(true);
        var clear = attrs.getDeclaredMethod("clear");
        clear.setAccessible(true);
        try {
            try {
                begin.invoke(op, wrapped, DirectoryCandidateValidator.SecureOpenHook.NONE);
                throw new AssertionError("expected attribute failure");
            } catch (InvocationTargetException e) {
                if (!(e.getCause() instanceof IOException) && !(runtimeFailure && e.getCause() instanceof IllegalStateException))
                    throw e;
            } finally {
                clear.invoke(op);
            }
            if (closes.get() != 1)
                throw new AssertionError("Pinned attribute failure must release its root handle exactly once: " + closes.get());
        } finally {
            if (raw != null) raw.close();
            Files.delete(real);
        }
    }
}
