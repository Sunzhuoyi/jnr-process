package jnr.process;

import static org.junit.Assert.*;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Created by headius on 1/19/15.
 */
public class TestProcessBuilder {
    @Test
    public void testBasicProcess() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", "echo hello |tee test; sleep 40");

        pb.directory(new File("/tmp"));
        pb.redirectErrorStream(true);

        Process p = pb.start();

        byte[] hello = new byte[5];
        p.getInputStream().read(hello);
        assertFalse(p.waitFor(10, TimeUnit.SECONDS));
        System.out.println(System.currentTimeMillis());
        p.destroy();
        System.out.println(System.currentTimeMillis());
//        assertNotEquals(0, p.exitValue());
//        assertNotEquals(-1, p.exitValue());
        assertNotEquals(0, p.waitFor());
        System.out.println(System.currentTimeMillis());
        System.out.println(p.exitValue());
        
        assertArrayEquals("hello".getBytes(), hello);
    }
}
