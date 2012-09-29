package org.jenkinsci.ant.interceptor;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

/**
 * @author Kohsuke Kawaguchi
 */
public class StreamCipherFactory {
    private final SecretKey key;

    public StreamCipherFactory(SecretKey key) {
        this.key = key;
    }

    public InputStream wrap(InputStream in) throws GeneralSecurityException {
        Cipher decrypt = Cipher.getInstance(ALGORITHM);
        decrypt.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(key.getEncoded()));

        return new CipherInputStream(in,decrypt);
    }

    public OutputStream wrap(OutputStream out) throws GeneralSecurityException {
        Cipher decrypt = Cipher.getInstance(ALGORITHM);
        decrypt.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(key.getEncoded()));

        return new CipherOutputStream(out,decrypt);
    }

    private static final String ALGORITHM = "AES/CFB8/NoPadding";
}
