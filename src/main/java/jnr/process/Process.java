package jnr.process;

import jnr.constants.platform.WaitFlags;
import jnr.enxio.channels.NativeDeviceChannel;
import jnr.enxio.channels.NativeSelectorProvider;
import jnr.ffi.LibraryLoader;
import jnr.posix.POSIX;
import jnr.constants.platform.Signal;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by headius on 1/19/15.
 */
public class Process extends java.lang.Process {
    private final long pid;
    private final POSIX posix;
    private final LibC libc;
    private final NativeDeviceChannel out; // stdin of child
    private final NativeDeviceChannel in; // stdout  of child
    private final NativeDeviceChannel err; // stderr of child
    long exitcode = -1;

    private final ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();

    public interface LibC {
        int killpg(int pgrp, int sig);
    }

    public Process(POSIX posix, long pid, int out, int in, int err) {
        libc = LibraryLoader.create(LibC.class).load("c");
        //libc.setpgid((int)pid, (int)pid);
        this.posix = posix;
        this.pid = pid;
        this.out = new NativeDeviceChannel(NativeSelectorProvider.getInstance(), out, SelectionKey.OP_WRITE);
        this.in = new NativeDeviceChannel(NativeSelectorProvider.getInstance(), in, SelectionKey.OP_READ);
        this.err = new NativeDeviceChannel(NativeSelectorProvider.getInstance(), err, SelectionKey.OP_READ);
    }

    public long getPid() {
        return pid;
    }

    public SelectableChannel getOut() {
        return out;
    }

    @Override
    public OutputStream getOutputStream() {
        return Channels.newOutputStream(out);
    }

    public SelectableChannel getIn() {
        return in;
    }

    @Override
    public InputStream getInputStream() {
        return Channels.newInputStream(in);
    }

    public SelectableChannel getErr() {
        return err;
    }

    @Override
    public InputStream getErrorStream() {
        return Channels.newInputStream(err);
    }

    private long waitForProcess() {
        if(exitcode != -1){
            return exitcode;
        }
        int[] status = new int[1];
        int ret = posix.waitpid(pid, status, WaitFlags.WNOHANG.intValue());
        if(ret > 0) {
            exitcode = status[0];
        }
        return exitcode;
    }

    @Override
    public synchronized int waitFor() throws InterruptedException {
        while (exitcode == -1) {
            waitForProcess();
            wait(1000);
        }
        return Long.valueOf(exitcode).intValue();
    }

    @Override
    public synchronized boolean  waitFor(long timeout, TimeUnit unit) throws InterruptedException {
        if(exitcode != -1) {
            return true;
        }
        if(timeout <= 0) {
            return false;
        }

        long timeoutAsNanos = unit.toNanos(timeout);
        long startTime = System.nanoTime();
        long rem = timeoutAsNanos;

        while (exitcode == -1 && (rem > 0)) {
            wait(1000);
            waitForProcess();
            rem = timeoutAsNanos - (System.nanoTime() - startTime);
        }
        return exitcode != -1;

    }

    public int kill() {
        return kill(Signal.SIGKILL);
    }

    public int kill(Signal sig) {
        return posix.kill(Long.valueOf(pid).intValue(), sig.intValue());
    }

    public int killProcessGroup() {
        return killProcessGroup(Signal.SIGKILL);
    }

    public int killProcessGroup(Signal sig) {
        return libc.killpg(Long.valueOf(pid).intValue(), sig.intValue());
    }

    public int exitValue() {
        waitForProcess();
        if (exitcode == -1) {
            throw new IllegalThreadStateException("subprocess has not yet completed");
        }
        return Long.valueOf(exitcode).intValue();
    }

    @Override
    public void destroy() {
        int ret = killProcessGroup();
        if(ret == -1) {
            kill();
        }
    }
}
