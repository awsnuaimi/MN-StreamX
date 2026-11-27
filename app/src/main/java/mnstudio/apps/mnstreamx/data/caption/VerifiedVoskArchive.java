package mnstudio.apps.mnstreamx.data.caption;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** The caller authenticates the complete ZIP with its pinned SHA-256 before constructing this view. */
final class VerifiedVoskArchive {
    private static final long MAX_BYTES = 192L * 1024 * 1024;
    private final Map<String, ZipEntry> entries = new LinkedHashMap<>();
    private final ZipFile zip;
    private final long bytes;

    VerifiedVoskArchive(ZipFile zip, String folder) throws IOException {
        this.zip = zip;
        String prefix = folder + "/";
        long total = 0;
        Enumeration<? extends ZipEntry> items = zip.entries();
        while (items.hasMoreElements()) {
            ZipEntry entry = items.nextElement();
            String name = entry.getName();
            if (!name.startsWith(prefix)) throw new IOException("Unexpected model directory");
            if (entry.isDirectory()) continue;
            String path = name.substring(prefix.length());
            if (!path.matches("[A-Za-z0-9_.-]+(/[A-Za-z0-9_.-]+)*")) throw new IOException("Invalid model path");
            for (String part : path.split("/")) {
                if (part.equals(".") || part.equals("..")) throw new IOException("Invalid model path");
            }
            long size = entry.getSize();
            if (size < 0 || entry.getCrc() < 0 || size > MAX_BYTES - total || entries.size() >= 128
                    || entries.put(path, entry) != null) throw new IOException("Invalid model archive");
            total += size;
        }
        if (!entries.containsKey("am/final.mdl") || !entries.containsKey("conf/model.conf"))
            throw new IOException("Incomplete model archive");
        this.bytes = total;
    }

    long getBytes() {
        return bytes;
    }

    boolean isInstalled(File target, Runnable check) throws IOException {
        if (!target.isDirectory()) return false;
        byte[] buffer = new byte[65536];
        for (Map.Entry<String, ZipEntry> item : entries.entrySet()) {
            check.run();
            File file = new File(target, item.getKey());
            ZipEntry entry = item.getValue();
            if (!file.isFile() || file.length() != entry.getSize()) return false;
            try (InputStream input = new FileInputStream(file)) {
                if (!transfer(input, null, buffer, entry, check)) return false;
            }
        }
        return true;
    }

    void extract(File staging, Runnable check) throws IOException {
        byte[] buffer = new byte[65536];
        for (Map.Entry<String, ZipEntry> item : entries.entrySet()) {
            check.run();
            File file = new File(staging, item.getKey());
            File parent = file.getParentFile();
            if (parent == null || (!parent.isDirectory() && !parent.mkdirs()))
                throw new IOException("Cannot create model directory");
            ZipEntry entry = item.getValue();
            try (InputStream input = zip.getInputStream(entry); OutputStream output = new FileOutputStream(file)) {
                if (!transfer(input, output, buffer, entry, check)) throw new IOException("Damaged model entry");
            }
        }
    }

    private static boolean transfer(InputStream input, OutputStream output, byte[] buffer,
                                    ZipEntry expected, Runnable check) throws IOException {
        CRC32 crc = new CRC32();
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            check.run();
            total += read;
            if (total > expected.getSize()) return false;
            crc.update(buffer, 0, read);
            if (output != null) output.write(buffer, 0, read);
        }
        return total == expected.getSize() && crc.getValue() == expected.getCrc();
    }
}