package com.twomemory.app;

import android.app.Application;
import java.security.*;
import java.security.cert.Certificate;
import java.io.*;
import java.util.*;

/** Only test sources: exercise real AES/GCM without a product plaintext escape hatch. */
public class TestKeystoreApplication extends Application {
    @Override public void onCreate() { super.onCreate(); install(); }
    public static void install() {
        if(Security.getProvider("AndroidKeyStore")==null) Security.addProvider(new TestProvider());
    }
    public static class TestProvider extends Provider {
        public TestProvider() { super("AndroidKeyStore",1.0,"JVM test key store"); put("KeyStore.AndroidKeyStore",TestStore.class.getName()); }
    }
    public static class TestStore extends KeyStoreSpi {
        private static final javax.crypto.SecretKey key=createKey();
        private static javax.crypto.SecretKey createKey() {
            try { var generator=javax.crypto.KeyGenerator.getInstance("AES"); generator.init(256); return generator.generateKey(); }
            catch(Exception e) { throw new IllegalStateException(e); }
        }
        public Key engineGetKey(String alias,char[] password){return key;}
        public Certificate[] engineGetCertificateChain(String alias){return null;}
        public Certificate engineGetCertificate(String alias){return null;}
        public Date engineGetCreationDate(String alias){return new Date(0);}
        public void engineSetKeyEntry(String alias,Key key,char[] password,Certificate[] chain){throw new UnsupportedOperationException();}
        public void engineSetKeyEntry(String alias,byte[] key,Certificate[] chain){throw new UnsupportedOperationException();}
        public void engineSetCertificateEntry(String alias,Certificate certificate){throw new UnsupportedOperationException();}
        public void engineDeleteEntry(String alias){throw new UnsupportedOperationException();}
        public Enumeration<String> engineAliases(){return Collections.enumeration(List.of("moon_letter_session_key"));}
        public boolean engineContainsAlias(String alias){return true;}
        public int engineSize(){return 1;}
        public boolean engineIsKeyEntry(String alias){return true;}
        public boolean engineIsCertificateEntry(String alias){return false;}
        public String engineGetCertificateAlias(Certificate certificate){return null;}
        public void engineStore(OutputStream out,char[] password){}
        public void engineLoad(InputStream in,char[] password){}
    }
}
