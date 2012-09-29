package org.jenkinsci.ant.interceptor;

import hudson.remoting.Channel;
import hudson.remoting.Channel.Mode;
import hudson.remoting.ChannelProperty;
import hudson.remoting.SocketInputStream;
import hudson.remoting.SocketOutputStream;
import org.jenkinsci.ant.AntEvent;
import org.jenkinsci.ant.AntListener;
import org.kohsuke.MetaInfServices;

import javax.annotation.Nonnull;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Establishes a channel, retrieves {@link AntListener}s and then delegate events to them.
 *
 * @author Kohsuke Kawaguchi
 */
@MetaInfServices
public class AntInterceptor extends AntListener {
    private final List<AntListener> delegates = new ArrayList<AntListener>();

    public AntInterceptor() throws Exception {
        String connect = System.getenv(JENKINS_ANT_CONNECTOR);
        if (connect==null) {
            System.err.println("Can't talk to Jenkins because "+ JENKINS_ANT_CONNECTOR +" environment variable is missing. Did you unset this?");
            return;
        }

        String[] tokens = connect.split("\\|");
        if (tokens==null || tokens.length<2) {
            // allow more tokens for future extension
            System.err.println("Can't talk to Jenkins because "+ JENKINS_ANT_CONNECTOR +" environment variable is malformed: "+connect);
            return;
        }

        SecretKey symKey = new SecretKeySpec(readFileToByteArray(new File(tokens[1])),"AES");

        StreamCipherFactory cipher = new StreamCipherFactory(symKey);

        ExecutorService pool = Executors.newCachedThreadPool(new ThreadFactory() {
            private int iota=1;
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("AntInterceptor pool thread #"+(iota++));
                return t;
            }
        });

        Socket s = new Socket((String)null, Integer.parseInt(tokens[0]));
        CHANNEL = new Channel("channel", pool, Mode.BINARY,
                new BufferedInputStream(cipher.wrap(new SocketInputStream(s))),
                new BufferedOutputStream(cipher.wrap(new SocketOutputStream(s))));

        delegates.addAll((Collection<AntListener>)CHANNEL.getRemoteProperty(LISTENERS_KEY));
    }

    private byte[] readFileToByteArray(File f) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FileInputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[256];
            int len;
            while ((len=in.read(buf))>=0) {
                baos.write(buf,0,len);
            }
        } finally {
            in.close();
        }

        return baos.toByteArray();
    }

    @Override
    public void buildStarted(@Nonnull AntEvent event) {
        for (AntListener l : delegates)
            l.buildStarted(event);
    }

    @Override
    public void buildFinished(@Nonnull AntEvent event) {
        for (AntListener l : delegates)
            l.buildFinished(event);
    }

    @Override
    public void targetStarted(@Nonnull AntEvent event) {
        for (AntListener l : delegates)
            l.targetStarted(event);
    }

    @Override
    public void targetFinished(@Nonnull AntEvent event) {
        for (AntListener l : delegates)
            l.targetFinished(event);
    }

    @Override
    public void taskStarted(@Nonnull AntEvent event) {
        for (AntListener l : delegates)
            l.taskStarted(event);
    }

    @Override
    public void taskFinished(@Nonnull AntEvent event) {
        for (AntListener l : delegates)
            l.taskFinished(event);
    }

    @Override
    public void messageLogged(@Nonnull AntEvent event) {
        for (AntListener l : delegates)
            l.messageLogged(event);
    }

    public static final String LISTENERS_KEY = "AntListeners";

    public static final String JENKINS_ANT_CONNECTOR = "JENKINS_ANT_CONNECTOR";

    /**
     * Once the channel is established, it'll be kept here.
     * We only support one connection per VM.
     */
    private static Channel CHANNEL;

}
