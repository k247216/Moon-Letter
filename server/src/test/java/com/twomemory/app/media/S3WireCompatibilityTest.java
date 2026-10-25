package com.twomemory.app.media;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class S3WireCompatibilityTest {
    @Test void sdkSendsLengthDelimitedOriginalWithoutAwsChunkedOrAutomaticChecksumTrailer() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var received=new AtomicReference<byte[]>();
        var encoding=new AtomicReference<String>();
        var transfer=new AtomicReference<String>();
        var checksum=new AtomicReference<String>();
        server.createContext("/",exchange->{
            encoding.set(exchange.getRequestHeaders().getFirst("Content-Encoding"));
            transfer.set(exchange.getRequestHeaders().getFirst("Transfer-Encoding"));
            checksum.set(exchange.getRequestHeaders().getFirst("x-amz-sdk-checksum-algorithm"));
            received.set(exchange.getRequestBody().readAllBytes());
            exchange.sendResponseHeaders(200,-1); exchange.close();
        });
        server.start();
        try {
            var properties=new S3StorageProperties("http://127.0.0.1:"+server.getAddress().getPort(),"cn-hangzhou","test-bucket","test-access","test-secret",true);
            var storage=new S3ObjectStorage(properties,null,null);
            byte[] original={1,2,3};
            var sha=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(original));
            storage.putVerified("original",new ByteArrayInputStream(original),original.length,sha,"image/png");
            assertThat(encoding.get()).isNull();
            assertThat(transfer.get()).isNull();
            assertThat(checksum.get()).isNull();
            assertThat(received.get()).isEqualTo(original);
        } finally { server.stop(0); }
    }
}
