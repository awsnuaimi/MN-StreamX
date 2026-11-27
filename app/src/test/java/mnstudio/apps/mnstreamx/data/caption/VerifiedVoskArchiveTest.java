package mnstudio.apps.mnstreamx.data.caption;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class VerifiedVoskArchiveTest {
    @Rule public final TemporaryFolder temp = new TemporaryFolder();

    private File writeArchive(String... paths) throws IOException {
        File file = temp.newFile();
        try (ZipOutputStream stream = new ZipOutputStream(new FileOutputStream(file))) {
            for (String path : paths) {
                stream.putNextEntry(new ZipEntry(path));
                stream.write(new byte[16]);
                stream.closeEntry();
            }
        }
        return file;
    }

    @Test public void installsAndDetectsSameLengthCorruption() throws IOException {
        File directory = temp.newFolder();
        try (ZipFile zip = new ZipFile(writeArchive("model/am/final.mdl", "model/conf/model.conf"))) {
            VerifiedVoskArchive model = new VerifiedVoskArchive(zip, "model");
            Runnable active = () -> {};
            assertFalse(model.isInstalled(directory, active));
            model.extract(directory, active);
            assertTrue(model.isInstalled(directory, active));
            try (FileOutputStream output = new FileOutputStream(new File(directory, "am/final.mdl"))) {
                byte[] corrupt = new byte[16];
                corrupt[7] = 42;
                output.write(corrupt);
            }
            assertFalse(model.isInstalled(directory, active));
        }
    }

    @Test public void rejectsUnexpectedPathsAndHonorsCancellation() throws IOException {
        try (ZipFile zip = new ZipFile(writeArchive("model/am/final.mdl", "model/conf/model.conf", "model/../x"))) {
            assertThrows(IOException.class, () -> new VerifiedVoskArchive(zip, "model"));
        }
        try (ZipFile zip = new ZipFile(writeArchive("model/am/final.mdl", "model/conf/model.conf"))) {
            VerifiedVoskArchive model = new VerifiedVoskArchive(zip, "model");
            assertThrows(CancellationException.class, () -> model.extract(temp.newFolder(),
                () -> { throw new CancellationException(); }));
        }
    }
}