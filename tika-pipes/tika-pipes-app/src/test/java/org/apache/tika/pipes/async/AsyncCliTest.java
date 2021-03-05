package org.apache.tika.pipes.async;

import org.junit.Test;

public class AsyncCliTest {
    @Test
    public void testBasic() throws Exception {
        String[] args = {
                "/Users/allison/Desktop/tika-tmp/tika-config.xml"
        };
        AsyncCli.main(args);
    }

    @Test
    public void testUnhandled() throws InterruptedException {
        Thread t = new Thread(new Task());

        t.start();
        t.join();
        for (StackTraceElement el : t.getStackTrace()) {
            System.out.println(el);
        }
    }

    private static class Task implements Runnable {

        @Override
        public void run() {
            Thread.currentThread().setUncaughtExceptionHandler(new MyUncaught());
            for (int i = 0; i < 5; i++) {
                System.out.println(i);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException();
                }
            }
            throw new RuntimeException("kaboom");
        }
    }

    private static class MyUncaught implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            throw new RuntimeException("bad");
        }
    }
}
